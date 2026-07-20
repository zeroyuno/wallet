# Research: Importar datos desde BudgetBakers Wallet

## 1. Endpoints y campos de la API de Wallet que se consumen

Referencia: OpenAPI 1.4.0 publicado en `https://rest.budgetbakers.com/wallet/openapi` (no lo
controlamos ni lo duplicamos — se documenta acá exactamente qué se usa, para trazabilidad).

| Endpoint | Uso |
|---|---|
| `GET /v1/api/accounts` | Listar cuentas del usuario (FR-002). Campos usados: `id`, `name`, `accountType`, `currencyCode`, `initialBalance.value`. |
| `GET /v1/api/categories` | Listar categorías del usuario (FR-003). Campos usados: `id`, `name`, `parentId`, `group.id`. |
| `GET /v1/api/records` | Listar movimientos, paginado (`limit`/`offset`, máx. 200 por página) y filtrado por `recordDate` (FR-004). Campos usados: `id`, `accountId`, `amount.value`, `amount.currencyCode`, `recordDate`, `recordType`, `categoryId`, `counterParty`, `note`. |

Autenticación: `Authorization: Bearer <token>` — el token lo genera el usuario desde su propia cuenta
de Wallet (fuera del alcance de esta spec) y se recibe en el request de inicio de la importación, sin
persistirlo (Assumptions de spec.md).

Rate limit: 500 requests/hora (headers `X-RateLimit-Remaining`, respuesta `429` con `Retry-After`
cuando se excede) — ver decisión 5 (reanudación).

## 2. Mapeo de tipo de cuenta (Wallet → propio)

**Decision**: tabla de mapeo fija, con `OTHER` como default para cualquier tipo no listado
explícitamente (incluye tipos futuros que Wallet agregue y no conozcamos):

| `accountType` de Wallet | `AccountType` propio |
|---|---|
| `Cash` | `CASH` |
| `CurrentAccount`, `SavingAccount` | `BANK` |
| `CreditCard` | `CREDIT_CARD` |
| `General`, `Bonus`, `Insurance`, `Investment`, `Loan`, `Mortgage`, `Overdraft`, *(cualquier otro)* | `OTHER` |

**Rationale**: nuestro modelo de cuentas (feature 002) es deliberadamente más simple que el de Wallet
(4 tipos vs. 11) — no se justifica ampliarlo solo para esta importación (YAGNI). El mapeo prioriza
preservar el tipo cuando hay equivalencia razonable y usa `OTHER` como red de seguridad para no
rechazar una cuenta completa por un tipo sin equivalente (FR-002, Acceptance Scenario US1.3).

**Alternatives considered**: ampliar `AccountType` propio para calzar 1:1 con Wallet — rechazado por
YAGNI, ya que esos tipos adicionales (inversión, préstamo, hipoteca) no tienen ningún comportamiento
propio en esta app todavía.

## 3. Tipo de categoría (ingreso/gasto) — inferencia, porque Wallet no lo expone explícito

**Decision**: el esquema `Category` de Wallet no tiene un campo de tipo ingreso/gasto explícito (a
diferencia de `Record`, que sí lo tiene vía `recordType`). Se infiere el tipo de cada categoría
importada a partir de su `group.id`: si es `"income"` → `INCOME`; en cualquier otro caso (incluida
ausencia de `group`) → `EXPENSE`.

**Rationale**: es el único campo disponible en la respuesta de `/categories` con información temática
suficiente para inferir el tipo, y la mayoría de las categorías en una app de gastos son de tipo gasto
— un default a `EXPENSE` es la opción de menor daño si la inferencia falla en algún caso puntual.

**Limitación conocida y aceptada**: si el usuario usó la misma categoría de Wallet para movimientos de
ambos tipos (algo que Wallet permite y nuestro modelo no, ya que acá una categoría tiene un tipo fijo),
un movimiento importado cuyo tipo no coincida con el tipo inferido de su categoría se importa igual
pero **sin** esa categoría asociada (se deja sin categorizar) en vez de fallar — ver FR-004 y el
manejo de errores en `ImportService`. Se documenta como limitación conocida del MVP, no se resuelve acá
(ampliar nuestro modelo de categorías para soportar ambos tipos sería un cambio de alcance mayor, fuera
de esta spec).

## 4. Importación de jerarquía de categorías en dos pasadas

**Decision**: `GET /categories` devuelve categorías sin garantizar que un padre aparezca antes que su
hijo en la respuesta. La importación de categorías se hace en dos pasadas: (1) crear todas las
categorías sin asignar `parentCategoryId` todavía; (2) recorrer de nuevo y, para cada una que tenía
`parentId` en Wallet, resolver el id propio ya creado (vía `import_external_refs`) y setear el padre.

**Rationale**: evita depender del orden de la respuesta de la API externa, que no está garantizado por
su documentación.

**Alternatives considered**: ordenar categorías por profundidad antes de importar (requiere resolver el
árbol completo en memoria primero) — rechazado por ser más complejo que la solución de dos pasadas para
el mismo resultado, dado que Wallet solo soporta un nivel de anidamiento (padre/hijo, no
padre/hijo/nieto).

## 5. Reanudación tras el rate limit externo (500 req/hora)

**Decision**: la entidad `Import` guarda un cursor de progreso: qué fase completó (cuentas,
categorías) y, para movimientos, la fecha del último `recordDate` procesado exitosamente. Si una
llamada a Wallet devuelve `429`, `ImportService` persiste el estado `PAUSED_RATE_LIMIT` con el cursor
tal cual quedó y termina esa corrida sin error. Una nueva invocación de `POST /api/imports` para el
mismo usuario con una importación en ese estado continúa desde el cursor guardado, en vez de arrancar
de cero.

**Rationale**: es el mecanismo mínimo necesario para cumplir FR-008 sin necesitar guardar el estado
completo de la paginación (offset exacto) — usar la fecha del movimiento como cursor es suficiente
porque los movimientos se piden ordenados por fecha y son inmutables una vez importados (la idempotencia
de la decisión 6 cubre el resto).

**Alternatives considered**: reintentar automáticamente con backoff dentro de la misma corrida —
rechazado porque el límite es de 500/hora, un backoff razonable implicaría dejar el request HTTP
abierto (o el hilo async ocupado) por potencialmente más de una hora; es más simple terminar la corrida
y dejar que una corrida posterior (iniciada por el usuario o reintentada automáticamente más tarde)
continúe.

## 6. Mecanismo de idempotencia (FR-006)

**Decision**: tabla `import_external_refs` (`user_id`, `entity_type` [ACCOUNT/CATEGORY/TRANSACTION],
`external_id`, `internal_id`), con restricción única en (`user_id`, `entity_type`, `external_id`).
Antes de crear cada cuenta/categoría/movimiento, `ImportService` consulta si ya existe un mapeo para
ese `external_id` — si existe, lo omite (ya importado); si no, lo crea y registra el mapeo.

**Rationale**: es un mecanismo estándar de integración (mapeo de IDs externos), aislado en el propio
contexto `walletimport` sin tocar el modelo de dominio de `account`/`transaction` (ver Complexity
Tracking en plan.md). Permite además, como beneficio derivado, resolver la reasignación de
`categoryId`/`accountId` al importar movimientos (se busca el id propio a partir del id de Wallet
referenciado).

## 7. Ejecución en segundo plano

**Decision**: `ImportService.processImport(...)` anotado con `@Async` (Spring `TaskExecutor` default),
disparado desde `ImportController` tras crear el registro `Import` en estado `IN_PROGRESS` — el
controller responde `202 Accepted` de inmediato con el `id` de la importación, sin esperar a que
termine.

**Rationale**: cumple con que el usuario pueda consultar el progreso (US3) sin bloquear el request que
inicia la importación, con la herramienta más simple ya disponible en el framework — ver Complexity
Tracking en plan.md para por qué se descarta un sistema de colas dedicado.
