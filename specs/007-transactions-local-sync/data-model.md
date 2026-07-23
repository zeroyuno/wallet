# Data Model: Caché local y sincronización de movimientos en Android

## Backend (`transaction`, contexto ya existente)

### `Transaction` (agregado) — cambios

Todos los campos existentes se mantienen (ver `specs/003-transactions/data-model.md`). Se agrega:

| Campo | Tipo | Notas |
|---|---|---|
| `updatedAt` | Instant | Nuevo. `= createdAt` al crear; se actualiza en cada `update()`. Es el campo por el que ordena/pagina el feed de sincronización (research.md #1). |

### `DeletedTransactionTombstone` (nuevo, tabla lateral — no es un agregado de dominio visible)

Existe solo para que el feed de sincronización pueda informar borrados (research.md #2).

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | Id de la transacción borrada (no hay FK — la fila original ya no existe). |
| `userId` | UUID | Dueño, para filtrar el feed por usuario. |
| `deletedAt` | Instant | Momento del borrado; participa en el mismo orden de cursor que `updatedAt`. |

### `TransactionSyncCursor` (DTO de aplicación, entrada/salida del caso de uso `sync`)

| Campo | Dirección | Tipo | Notas |
|---|---|---|---|
| `since` | entrada | Instant, nullable | `null`/ausente = traer desde el principio (sync inicial). |
| `limit` | entrada | int | Tamaño de página; el servidor aplica un máximo razonable si se omite o es excesivo. |
| `upserts` | salida | lista de `TransactionView` | Creados o editados desde `since`, con su `updatedAt`. |
| `deletedIds` | salida | lista de UUID | Borrados desde `since`. |
| `nextSince` | salida | Instant | Cursor a usar en la próxima llamada (timestamp del último elemento devuelto). |
| `hasMore` | salida | boolean | Si quedan más cambios más allá de esta página. |

## Android — caché local (Room)

### `TransactionEntity` (tabla local, espejo del backend + metadatos de sync)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | String (UUID) | Mismo id que en el backend — generado en el cliente al crear (ya soportado por el backend, research.md #3). |
| `type`, `amount`, `date`, `description`, `accountId`, `categoryId` | — | Espejo de `TransactionResponse` ya existente. |
| `updatedAt` | Instant/Long | Espejo del campo del backend una vez sincronizado; se actualiza también en cada escritura local pendiente. |
| `syncState` | enum | `SYNCED` / `PENDING_CREATE` / `PENDING_UPDATE` / `PENDING_DELETE` (research.md #5). |

### `SyncCursorEntity` (o entrada única en Room/DataStore)

| Campo | Tipo | Notas |
|---|---|---|
| `lastSyncedAt` | Instant, nullable | Último `nextSince` recibido con éxito; `null` antes de la primera sincronización. |

## API propia — request/response

- `GET /api/transactions/sync?since={ISO-8601 Instant, opcional}&limit={int, opcional}`, autenticado
  igual que el resto de `/api/transactions`. Responde `200` con `TransactionSyncResponse`:
  - `upserts`: lista de `TransactionResponse` (mismo shape ya usado por `GET/POST/PUT /api/transactions`,
    ver `specs/003-transactions/contracts/`) + `updatedAt`.
  - `deletedIds`: lista de UUID.
  - `nextSince`: Instant.
  - `hasMore`: boolean.
- Los endpoints de escritura (`POST`/`PUT`/`DELETE /api/transactions[/{id}]`) **no cambian** — el
  worker de sync de Android los reutiliza tal cual (research.md #3).

## Reglas de sincronización (resumen, detalle en research.md)

- **Pull**: el cliente pide `/sync?since=<cursor local>` en un loop hasta `hasMore=false`, aplicando
  cada página a Room (`upserts` como upsert por id, `deletedIds` como delete), y guarda `nextSince`
  como nuevo cursor recién al terminar la página completa (para no perder progreso si una página falla
  a mitad de aplicarse).
- **Push**: toda fila local con `syncState != SYNCED` se envía al endpoint de escritura correspondiente;
  al confirmar éxito (o un 409 sobre una `PENDING_CREATE`, ver research.md #3) pasa a `SYNCED`;
  al fallar por red, se reintenta en la próxima corrida del worker sin cambiar de estado.
