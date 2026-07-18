# Research: Transacciones (ingresos y gastos)

## Filtrado de transacciones (FR-004): en Java, no con parámetros opcionales en SQL

**Decision**: `JpaTransactionRepository.findAllByUserId(userId, accountId, categoryId, dateFrom,
dateTo)` trae todas las transacciones del usuario con una query simple
(`SpringDataTransactionRepository.findAllByUserId(userId)`) y aplica los cuatro filtros con
`.filter(...)` en Java, en vez de construir una query JPQL con condiciones tipo
`(:param IS NULL OR columna = :param)`.

**Rationale**: Se implementó primero con el patrón `(:param IS NULL OR ...)`, el enfoque más directo
para un filtro opcional en una sola query. Funcionó para `accountId`/`categoryId` (UUID) pero falló
para `dateFrom`/`dateTo` con `ERROR: could not determine data type of parameter` — un problema
conocido de Postgres/JDBC: cuando la única otra aparición de un parámetro es `IS NULL`, Postgres no
tiene contexto para inferirle un tipo. Forzar `CAST(:dateFrom AS date)` tampoco funcionó: cuando el
valor real es `null`, Hibernate lo manda sin tipo (como `bytea`), y `CAST(bytea AS date)` es un cast
inválido en Postgres — el error cambió pero no se resolvió. En vez de seguir peleando con el tipado
de parámetros de Postgres/Hibernate para este caso, filtrar en Java es simple, evita el problema de
raíz, y a esta escala (uso personal, no miles de transacciones por usuario) no tiene costo real de
rendimiento — consistente con el principio V de la constitución (YAGNI).

**Alternatives considered**:
- `(:param IS NULL OR ...)` con `CAST(...)` — descartado, ver arriba (el error persiste con `null`).
- JPA Specifications (construir la query dinámicamente según qué filtros vienen no-nulos) —
  descartado por ahora: es la solución "correcta" si el volumen de datos llegara a justificarlo, pero
  es más código y una abstracción nueva en el proyecto para un problema que, a esta escala, se
  resuelve igual de bien filtrando en memoria.

## Identificador de transacción: UUID v7, opcionalmente provisto por el cliente (FR-011)

**Decision**: `TransactionId` se genera con **UUID v7** (timestamp de 48 bits + bits aleatorios,
ordenable temporalmente) en vez de UUID v4 (aleatorio, como usan hoy `AccountId`/`CategoryId`/
`UserId`). Además, `POST /api/transactions` acepta un campo `id` opcional en el body: si el cliente
lo envía, se usa tal cual (debe ser un UUID válido); si no, el servidor genera uno con UUID v7. Si el
`id` enviado ya existe para ese usuario, el servidor responde `409` en vez de sobrescribir la
transacción existente.

**Rationale**: Dos motivos, explícitos en la spec (FR-011) y aportados por decisión de producto:
1. **Seguridad**: un ID no secuencial no se puede "probar" incrementando/decrementando un contador
   (a diferencia de un ID autoincremental de base de datos) — UUID v4 ya cumplía esto, pero v7 lo
   mantiene mientras gana el punto 2.
2. **Creación offline**: una futura app Android con soporte offline necesita generar el ID de una
   transacción *localmente*, antes de tener conexión para pedírselo al servidor. Con IDs
   client-generados, dos dispositivos (o el mismo dispositivo offline y luego online) nunca van a
   colisionar en la práctica (128 bits de espacio), y at-least-once retries de sincronización se
   vuelven idempotentes de forma natural (reenviar la misma transacción con el mismo `id` la
   servidor la reconoce como ya existente en vez de duplicarla). UUID v7 además mantiene los IDs
   *ordenables por tiempo de creación* (el timestamp va en los primeros 48 bits), útil para
   ordenar/paginar sin depender de una columna `created_at` separada — aunque esta feature igual
   mantiene `createdAt` por claridad y porque la fecha *de la transacción* (`date`, elegida por el
   usuario) es un campo de negocio distinto del momento en que el registro se creó.

Esto solo se aplica a `TransactionId` en esta feature — `AccountId`/`CategoryId`/`UserId` (features
001/002) no se tocan; son entidades que hoy siempre se crean online, sin necesidad de esta propiedad,
y cambiarlas sería un cambio no solicitado sobre código ya implementado y probado (YAGNI).

**Alternatives considered**:
- UUID v4 (igual que el resto del proyecto) — rechazado: no es ordenable temporalmente, lo que
  importaba menos hasta que apareció el requisito de soporte offline.
- ID autoincremental (`BIGSERIAL`) — rechazado de plano: es exactamente el patrón "adivinable/
  probable" que la spec pide evitar (FR-011), y no puede generarse offline sin coordinación con la
  base de datos.
- Generar el ID únicamente en el servidor (sin aceptar uno del cliente) — rechazado: no resuelve el
  caso offline, que es el motivo principal de este cambio.
- Implementar UUID v7 a mano (bit-twiddling manual sobre `java.util.UUID`, que solo genera v4
  nativamente) — rechazado: `java.util.UUID` no tiene soporte nativo para v7 en esta versión de
  Java; se prefiere la librería `com.github.f4b6a3:uuid-creator` (liviana, sin dependencias
  transitivas, ampliamente usada) en vez de reimplementar la generación de IDs a mano, algo propenso
  a errores sutiles (ej. mal ordenamiento de bits) para un valor tan central como un identificador
  primario.

## Dirección de la dependencia entre `transaction` y `account`

**Decision**: `transaction` depende de `account` (vía `account.application`), nunca al revés.
`transaction.application.TransactionService` inyecta `AccountService`/`CategoryService` como
colaboradores para validar que la cuenta y (si se indica) la categoría de una transacción existen,
pertenecen al usuario autenticado, y —en el caso de la categoría— coinciden en tipo con la
transacción.

**Rationale**: Es la única dirección consistente con el dominio: una transacción siempre referencia
una cuenta (y opcionalmente una categoría), pero una cuenta o categoría no necesitan saber nada de las
transacciones que las referencian para existir o para cumplir su propio propósito. La constitución
(principio II) permite explícitamente que la comunicación entre contextos pase por la capa
`application`, y las reglas ArchUnit vigentes ya dejan esa capa sin restricciones cruzadas (solo
protegen `domain` e `infrastructure` por contexto).

**Alternatives considered**: Modelar `transaction` primero y que `account` consulte a `transaction`
para calcular su propio saldo — rechazado porque invierte la dependencia natural (un balance es un
derivado de las transacciones, no al revés) y forzaría a `account` a depender de un contexto que ni
siquiera existía cuando `account` se implementó.

## Cómo evitar que la dependencia hacia `account.application` arrastre tipos de `account.domain`

**Decision**: Se agregan tres métodos nuevos, puramente de lectura, a `AccountService`/
`CategoryService` (ambos ya existentes), diseñados para devolver **solo tipos primitivos**, nunca los
DTOs `AccountView`/`CategoryView` existentes ni ningún tipo de `account.domain`:

- `AccountService.existsOwnedByUser(UUID userId, UUID accountId): boolean`
- `AccountService.getInitialBalanceIfOwnedByUser(UUID userId, UUID accountId): Optional<BigDecimal>`
- `CategoryService.findTypeIfOwnedByUser(UUID userId, UUID categoryId): Optional<String>` (el tipo se
  devuelve como `String` — `"INCOME"`/`"EXPENSE"` — no como el enum `CategoryType`)

**Rationale**: `AccountView.type()` devuelve `AccountType` y `CategoryView.type()` devuelve
`CategoryType`, ambos enums definidos en `account.domain`. Si `TransactionService` consumiera esos
DTOs directamente, cualquier código que llame a `.type()` genera una dependencia de bytecode real
hacia `account.domain`, lo que viola la regla ArchUnit `domain_is_private_to_its_own_context_except_shared`
(que exime explícitamente a `shared`, pero no a otros bounded contexts). Devolver primitivos en la
frontera entre contextos es exactamente el mismo criterio que ya se aplicó en la feature 002 para
`userId` (`UUID` plano en `Account`/`Category`, en vez de importar el `UserId` de `auth.domain`) — acá
se generaliza ese criterio a cualquier dato que cruce la frontera de un contexto.

**Alternatives considered**:
- Reutilizar `AccountView`/`CategoryView` tal cual — rechazado, viola ArchUnit (ver arriba).
- Relajar la regla ArchUnit para permitir esta dependencia puntual — rechazado: la regla sigue siendo
  correcta y valiosa para el resto del código; el costo de tres métodos primitivos adicionales es
  mucho menor que debilitar una garantía arquitectónica que se aplica a todo el proyecto.
- Definir un puerto de inversión de dependencias (`account` declara una interfaz, `transaction` no la
  implementa — no aplica acá porque la dirección de la dependencia ya es la correcta; la inversión de
  dependencias solo hubiera sido necesaria en la dirección contraria, ver el punto de FR-010 abajo).

## Bloqueo de eliminación de cuenta/categoría con transacciones asociadas (FR-010)

**Decision**: Las FK de la nueva tabla `transactions` hacia `accounts.id` y `categories.id` se
declaran `ON DELETE RESTRICT` (comportamiento por defecto de Postgres, declarado explícitamente por
claridad). Se agrega un único `@ExceptionHandler(DataIntegrityViolationException.class)` genérico en
`AccountExceptionHandler` (ya existente, en `account.infrastructure.web`), que traduce esa violación
de FK a `409 Conflict` con un mensaje genérico ("no se puede eliminar: existen registros asociados").

**Rationale**: Es la única forma de implementar este requisito sin que `account` tenga que depender de
`transaction` (lo que violaría el principio II en la dirección prohibida — ver primera decisión de
este documento). La alternativa de un puerto de dominio con inversión de dependencias (`account`
define una interfaz tipo `TransactionLookupPort`, `transaction` la implementa) fue evaluada y
descartada: agrega una interfaz y un adaptador nuevos solo para replicar una garantía que la base de
datos ya ofrece de forma más simple y con menos superficie de código. Además, esta sesión ya identificó
y corrigió un bug real con exactamente este patrón (eliminar una categoría con subcategorías producía
un `401` en vez de un `409`, porque la violación de FK no estaba siendo traducida) — el fix de esa
vez fue agregar una verificación explícita en `CategoryService` antes de intentar el delete (para
subcategorías, un caso *dentro* del mismo contexto, donde sí es trivial consultar el propio
repositorio). Para FR-010 el caso es distinto: la verificación necesaria vive en `transaction`, un
contexto ajeno a `account`, así que el patrón de "restricción de BD + manejador de excepción
correctamente traducido" es la opción que respeta los límites de contexto sin sacrificar
correctitud — el bug de la sesión anterior no era el patrón en sí, sino que la excepción no estaba
siendo manejada.

**Alternatives considered**: Puerto de inversión de dependencias — rechazado (ver arriba). Verificar
desde `transaction` antes de permitir que `account` elimine — no aplica, `account` no expone (ni
debería exponer) un hook de "antes de eliminar" para que otros contextos se enganchen; sería más
acoplamiento, no menos.

## Cálculo del saldo de una cuenta

**Decision**: El saldo "actual" de una cuenta se calcula bajo demanda como
`initialBalance + SUM(ingresos) - SUM(gastos)` de sus transacciones, mediante una query de agregación
en `transaction.infrastructure.persistence`, combinada con el `initialBalance` obtenido de
`AccountService.getInitialBalanceIfOwnedByUser(...)`. No se agrega ni se muta ningún campo de saldo en
la tabla `accounts`.

**Rationale**: Con un campo de saldo desnormalizado y mutado en cada create/edit/delete de
transacción, cualquier bug de sincronización (ej. un update que reste el monto viejo pero no sume el
nuevo, o un delete que no revierta su efecto) deja el saldo mostrado inconsistente con la suma real de
movimientos — justo lo que SC-002 exige garantizar al 100%. Calculándolo bajo demanda, la
consistencia es estructural: no hay dos fuentes de verdad que puedan desincronizarse. A la escala de
esta app (uso personal, no miles de transacciones por cuenta) el costo de una suma en cada lectura es
insignificante.

**Alternatives considered**: Campo `current_balance` en `accounts`, actualizado incrementalmente en
cada operación — rechazado por el riesgo de inconsistencia descrito arriba, que además sería un bug
silencioso (el saldo se ve "razonable" pero incorrecto) y no algo que falle ruidosamente.

## Endpoint de saldo bajo `/api/accounts/{id}/balance` implementado fuera de `account`

**Decision**: `GET /api/accounts/{id}/balance` se implementa en `transaction.infrastructure.web`
(`BalanceController`), no en `AccountController`.

**Rationale**: La URL es la más intuitiva para quien consuma la API ("el saldo de esta cuenta"), pero
el saldo es un dato derivado de las transacciones — pertenece conceptualmente a `transaction`. Spring
no exige que un controller viva en el mismo paquete que el prefijo de su URL, así que no hay costo
técnico; se documenta explícitamente (acá y en plan.md) para que quede claro a futuro por qué este
endpoint no está en `AccountController`.

**Alternatives considered**: `GET /api/transactions/accounts/{id}/balance` (URL que refleja el
paquete que lo implementa) — rechazado por ser menos intuitivo para el cliente de la API sin ningún
beneficio arquitectónico real (el cliente no necesita saber qué bounded context implementa cada
endpoint).

## Campos editables de una transacción (FR-005)

**Decision**: Solo `amount`, `date`, `categoryId` y `description` son editables. `type` y `accountId`
son inmutables una vez creada la transacción — el DTO de edición (`TransactionUpdateRequest`) ni
siquiera incluye esos campos, a diferencia de `TransactionRequest` (creación).

**Rationale**: Es exactamente lo que dice FR-005 de la spec ("editar el monto, la fecha, la categoría
y la descripción"), que deliberadamente no menciona tipo ni cuenta. Modelar esto con un DTO de edición
separado (en vez de reutilizar `TransactionRequest` e ignorar silenciosamente los campos no
editables) evita que un cliente de la API asuma —incorrectamente— que puede mover una transacción a
otra cuenta o cambiar su tipo enviando esos campos en un `PUT`.

**Alternatives considered**: Reutilizar `TransactionRequest` para create y update (como se hizo en la
feature 002 para `AccountRequest`/`CategoryRequest`) — rechazado acá porque, a diferencia de cuentas y
categorías (donde todos los campos son editables), una transacción tiene campos explícitamente no
editables; reutilizar el mismo DTO sería engañoso.
