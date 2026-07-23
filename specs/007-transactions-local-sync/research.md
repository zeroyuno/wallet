# Research: Caché local y sincronización de movimientos en Android

## 1. Endpoint de sincronización incremental (backend)

**Decision**: un único endpoint nuevo, `GET /api/transactions/sync?since=&limit=`, que devuelve tanto
movimientos creados/editados como eliminados desde un cursor, ordenados de forma estable
(`updatedAt` ascendente, con desempate por `id`), paginado por `limit`. El mismo endpoint sirve tanto
para la sincronización inicial completa (cursor vacío = época) como para las sincronizaciones
incrementales posteriores — el cliente simplemente sigue pidiendo páginas hasta que `hasMore=false`, y
guarda el `nextSince` devuelto como cursor para la próxima vez.

**Rationale**: unificar ambos casos (carga inicial de ~11.000 registros y deltas de unos pocos
movimientos) en un solo mecanismo evita mantener dos endpoints/dos formatos de respuesta. Ordenar por
`updatedAt` (no por `id`/fecha de creación) es necesario porque una edición debe volver a aparecer en
el feed aunque el registro sea viejo.

**Alternatives considered**: paginación por `offset` sobre `GET /api/transactions` existente — se
descartó porque no resuelve el caso incremental (un offset no identifica "lo nuevo desde la última
vez", solo "la página N"); un endpoint separado para carga inicial vs. delta — se descartó por
duplicar lógica sin necesidad, dado que el cursor por `updatedAt` cubre ambos casos igual de bien.

## 2. Cómo el cliente se entera de los borrados

**Decision**: se agrega una tabla lateral `deleted_transactions (id, user_id, deleted_at)`. Al eliminar
un movimiento (`TransactionService.delete`), además de borrar la fila de `transactions` se inserta un
registro ahí. El feed de `/sync` combina (en memoria, dado el tamaño acotado de una página) los
movimientos vigentes modificados desde el cursor con los tombstones de borrado desde el cursor,
ordenados juntos por su respectivo timestamp.

**Rationale**: sin esto, el cliente no tiene forma de saber que algo desapareció sin volver a traer la
lista completa (lo que reintroduce el problema original). Una tabla lateral aditiva sigue el mismo
patrón ya usado en el proyecto para necesidades de tracking puntuales (`statement_import_line_hashes`
de la feature 006, `import_external_ref` de la feature 005) en vez de tocar el modelo central.

**Alternatives considered**: soft-delete en `transactions` (`deletedAt` + filtrar `IS NULL` en todas
las queries) — se descartó porque obliga a revisar y ajustar cada query existente del módulo
(`findAllByUserId`, `sumNetAmountForAccount`/saldo, los imports de las features 005/006) para resolver
algo que una tabla lateral aislada resuelve sin tocarlas.

## 3. Idempotencia de creación ante reintentos (FR-008)

**Decision**: no se modifica `TransactionService.create()`. Sigue rechazando con 409
(`DuplicateTransactionIdException`) un `id` de cliente ya existente — comportamiento ya establecido en
la feature 003/004 y cubierto por un test (`createRejectsDuplicateClientSuppliedId`). El worker de
sincronización en Android es quien interpreta ese 409: si la operación pendiente que estaba enviando
era justamente una creación con ese mismo `id`, trata la respuesta como éxito (ya se sincronizó en un
intento anterior cuya confirmación de red se perdió) en vez de reintentarla indefinidamente o marcarla
como error.

**Rationale**: el endpoint ya soporta `id` generado por el cliente precisamente pensando en este
escenario (comentario existente en `TransactionRequest`/`Transaction.create`: "creación offline, FR-011
[de la feature 003]"). Cambiar su semántica para todos los llamadores (formulario manual, futuros
clientes) para resolver un caso que el propio cliente ya puede distinguir es innecesario.

**Alternatives considered**: endpoint de creación "upsert" (devolver el existente en vez de 409) —
descartado por el motivo anterior; agregar una tabla de deduplicación de requests (idempotency keys) al
estilo Stripe — descartado por sobredimensionado para un único usuario/dispositivo sin escritura
concurrente real.

## 4. Modelo de datos local en Android

**Decision**: Room como base de datos local, con una tabla que espeja los campos de `transactions` más
dos columnas propias de sincronización: `syncState` (`SYNCED` / `PENDING_CREATE` / `PENDING_UPDATE` /
`PENDING_DELETE`) y `updatedAt` (para poder ordenar/paginar igual que el backend). Paging 3, integrado
con Room (`PagingSource` generado desde el DAO), alimenta la lista de la UI con scroll progresivo leído
enteramente en local.

**Rationale**: Room es el estándar de persistencia local en Android/Compose, con soporte de primera
clase para `Flow`/`PagingSource`, evitando escribir SQL a mano o un ORM propio. Paging 3 resuelve
"cargar de a partes" sin que la UI tenga que manejar offsets manualmente.

**Alternatives considered**: DataStore (clave-valor) — descartado, no sirve para una colección grande
y consultable de movimientos; SQLite crudo sin Room — descartado, más código repetitivo para el mismo
resultado sin beneficio real dado el tamaño del equipo/proyecto.

## 5. Outbox de cambios pendientes

**Decision**: no se modela una cola de eventos separada. Cada fila local de movimiento tiene su propio
`syncState`. Crear/editar/eliminar localmente solo cambia el estado de esa fila (o la marca para borrar)
— si el usuario edita dos veces antes de sincronizar, solo queda un estado final `PENDING_UPDATE` con
los últimos valores, no un historial de pasos intermedios (edge case ya identificado en spec.md).

**Rationale**: alcanza para el caso de uso real (único usuario, único dispositivo, sin conflictos
concurrentes que resolver — ver Assumptions de spec.md). Es la opción más simple que cumple FR-005/FR-006.

**Alternatives considered**: outbox como tabla de eventos append-only (event sourcing local) —
descartado por resolver un problema (reordenar/replayar cambios concurrentes de múltiples orígenes)
que está fuera de alcance.

## 6. Disparo de la sincronización

**Decision**: WorkManager con un `PeriodicWorkRequest` (intervalo mínimo soportado, ~15 minutos) más un
disparo inmediato (`OneTimeWorkRequest`) al abrir la app, después de cada escritura local, y ante un
"pull to refresh" manual en la lista.

**Rationale**: WorkManager es el mecanismo estándar de Android para trabajo diferible que debe
sobrevivir al cierre de la app y reintentar automáticamente con backoff — cumple FR-007/FR-009 sin
reinventar un scheduler propio.

**Alternatives considered**: un `Service` en foreground corriendo todo el tiempo — descartado, consume
batería sin necesidad para un volumen de cambios bajo (uso personal); sincronizar solo al abrir la
pantalla de movimientos — descartado, no cumple FR-007 (reintento sin que el usuario tenga que volver a
esa pantalla).

## 7. Impacto en `TransactionRepository`/`TransactionViewModel` (Android)

**Decision**: `TransactionRepository` deja de llamar a Retrofit directamente desde los métodos que
consume la UI — pasa a leer/escribir Room, y es quien internamente encola el trabajo de sync.
`TransactionViewModel` pasa de pedir `list()` una vez a observar un `Flow` paginado expuesto por el
repositorio.

**Rationale**: mantiene el patrón MVVM ya establecido (`Composable → ViewModel → Repository → ...`) sin
que la UI necesite saber que ahora hay una capa de persistencia local — el cambio queda encapsulado en
el repositorio, igual que la constitución exige para esta capa.

**Alternatives considered**: que el ViewModel hable directamente con Room y con Retrofit — descartado,
rompe la responsabilidad ya establecida del Repository como único punto de acceso a datos.
