# Research: Importar datos desde BudgetBakers Wallet

## 1. Endpoints y campos de la API de Wallet que se consumen

Referencia: OpenAPI 1.4.0 publicado en `https://rest.budgetbakers.com/wallet/openapi` (no lo
controlamos ni lo duplicamos — se documenta acá exactamente qué se usa, para trazabilidad).

| Endpoint | Uso |
|---|---|
| `GET /v1/api/accounts` | Listar cuentas del usuario (FR-002). Paginado (`limit`/`offset`/`nextOffset`, envuelto en `{"accounts": [...], ...}`, límite 1-200). Campos usados: `id`, `name`, `accountType`, `initialBalance.value`, `initialBalance.currencyCode` (no hay `currencyCode` a nivel raíz). |
| `GET /v1/api/categories` | Listar categorías del usuario (FR-003). Paginado igual que accounts, envuelto en `{"categories": [...], ...}`. Campos usados: `id`, `name`, `parentId`, `group.id`. |
| `GET /v1/api/records` | Listar movimientos, paginado (`limit`/`offset`, máx. 200 por página), envuelto en `{"records": [...], ...}`, filtrado por `recordDate` (FR-004) con sintaxis de operador (`recordDate=gte.<ISO-8601 instant>`, ver decisión #8). Campos usados: `id`, `accountId`, `amount.value` (con signo: negativo en gastos, ver #8), `recordDate`, `recordType`, `category.id` (objeto anidado, no `categoryId` plano), `counterParty`, `note`, `paymentType`, `recordState`, `transfer.mirrorRecord.id`, `labels[].name`. |

Autenticación: `Authorization: Bearer <token>` — el token lo genera el usuario desde su propia cuenta
de Wallet (fuera del alcance de esta spec) y se recibe en el request de inicio de la importación, sin
persistirlo (Assumptions de spec.md).

Rate limit: 500 requests/hora (headers `X-RateLimit-Remaining`, respuesta `429` con `Retry-After`
cuando se excede) — ver decisión 5 (reanudación).

**Nota (T033, verificación con datos reales)**: la forma inicial de este documento se basó en una
lectura del OpenAPI que resultó incompleta en varios puntos — quedó corregida recién al probar contra
la API real y releer el OpenAPI con más cuidado (ver decisión #8 para el detalle completo). Los
supuestos sobre `photos`/`place` (fuera de alcance) y el resto de los endpoints/autenticación/rate
limit sí se confirmaron correctos.

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

## 8. Captura de todos los campos de `Record` (excepto `photos` y `place`)

**Decision**: ampliación posterior al alcance inicial — se guardan todos los campos de `Record` salvo
`photos` (adjuntos) y `place` (ubicación), explícitamente excluidos. `counterParty` pasa a tener su
propia columna en `transactions` (antes se fusionaba con `note` en `description`); `paymentType` y
`recordState` se guardan tal cual, como texto crudo de Wallet, sin mapear a un enum propio ni tener
comportamiento asociado todavía; `transfer` se guarda como `walletTransferId` (el id crudo de Wallet),
sin modelar una relación de transferencia real entre dos movimientos propios; `labels` se guardan en
una tabla nueva `labels` (única por `user_id`+`name`, reutilizable entre transacciones) más una tabla
puente `transaction_labels`, en vez de un campo de texto simple.

**Rationale**: el usuario pidió explícitamente conservar el máximo de información posible del
histórico de Wallet. Guardar `paymentType`/`recordState`/`walletTransferId` "tal cual" (sin mapear)
evita inventar un enum o una relación propia sin un caso de uso concreto todavía (YAGNI) mientras
igual preserva el dato para el futuro. `labels` sí se modela con una tabla relacional propia (en vez
de texto plano) porque Wallet permite reutilizar la misma etiqueta en varios movimientos y eso es
consultable/filtrable a futuro sin necesitar otra migración.

**Alternatives considered**: mantener `counterParty` fusionado en `description` — rechazado porque el
usuario pidió expresamente preservar el campo por separado. Modelar `transfer` como una relación real
entre dos transacciones — rechazado por ahora: requeriría detectar y vincular ambos movimientos del
par, un cambio de mayor alcance que la spec original de transferencias (`Assumptions` de spec.md:
"transfers as two movements" independientes) no contemplaba; guardar el id crudo cumple con no perder
el dato sin ese rediseño.

**Correcciones tras probar contra la API real y releer el OpenAPI con más cuidado (T033)**: la
implementación inicial de `WalletApiHttpClient` tenía tres errores reales, encontrados recién al
correr una importación con datos de una cuenta de Wallet real:

1. **Signo del monto**: Wallet manda `amount.value` con signo (negativo en gastos, positivo en
   ingresos); nuestro dominio (`Transaction`) siempre exige magnitud positiva y expresa la dirección
   solo con `type`. Sin normalizar esto, **todo gasto** importado fallaba con "amount must be greater
   than zero" — se corrige tomando `amount.value.abs()` al mapear.
2. **Ventana de fecha por defecto**: `GET /v1/api/records` aplica automáticamente un filtro de los
   últimos 3 meses (`appliedRecordDateFilters` en la respuesta) si no se manda `recordDate`
   explícito — no es el parámetro `fromDate` que se había asumido, sino `recordDate` con sintaxis de
   operador (`gte.`/`gt.`/`lt.`/`lte.`/`eq.`, ej. `recordDate=gte.2024-01-01T00:00:00Z`), documentado
   en el OpenAPI pero no leído con suficiente cuidado la primera vez. Se corrige mandando siempre un
   `recordDate=gte.<cursor o 2000-01-01 si es una corrida nueva>` explícito.
3. **Formas anidadas no documentadas en el research.md original**: `category` en un `Record` es un
   objeto anidado (`category.id`), no un `categoryId` plano; `labels` es un array de objetos
   `{id, name, color, archived}` (se usa `.name`), no un array de strings; `transfer` no tiene un
   `id` propio — el id útil para trazabilidad es `transfer.mirrorRecord.id` (el movimiento espejo del
   otro lado de la transferencia); y `initialBalance.currencyCode` de una cuenta vive anidado, no
   como `currencyCode` a nivel raíz. Además, las tres listas (`accounts`/`categories`/`records`)
   vienen paginadas y envueltas en un objeto (`{"accounts": [...], "limit":..., "offset":...}`), no
   como array plano — el primer intento asumía un array directo y fallaba con
   `MismatchedInputException` en la primera llamada real.

**Rationale de la corrección**: se detectaron recién al ejecutar T033 contra una cuenta de Wallet
real con datos reales (19 cuentas, 96 categorías, movimientos históricos) — el fallback
`@JsonIgnoreProperties(ignoreUnknown = true)` ya presente evitó que campos no mapeados rompieran el
parseo, pero no puede inventar la forma correcta de un campo que sí se necesita. Queda como lección
para features futuras que integren APIs externas: preferir leer el OpenAPI completo (o probar con un
token real) antes de asumir formas de respuesta, en vez de derivarlas solo de la descripción textual
de los endpoints.
