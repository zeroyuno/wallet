# Data Model: Importar datos desde BudgetBakers Wallet

## `Import` (aggregate root, contexto `walletimport`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | |
| `userId` | UUID | Dueño de la importación (FR-010). |
| `status` | `ImportStatus` | `IN_PROGRESS`, `COMPLETED`, `PAUSED_RATE_LIMIT`, `FAILED`. |
| `accountsImported` / `categoriesImported` / `transactionsImported` | int | Contadores acumulados (persisten entre corridas si se reanuda). |
| `cursorPhase` | enum interno | Qué fase se completó: `ACCOUNTS`, `CATEGORIES`, `TRANSACTIONS`, `DONE` — determina por dónde continuar al reanudar. |
| `cursorRecordDate` | LocalDate, nullable | Último `recordDate` de Wallet procesado con éxito (fase de transacciones, ver research.md #5). |
| `errors` | lista de `ImportError` | Ver abajo. |
| `startedAt` / `lastActivityAt` | Instant | |

## `ImportError` (value object, embebido en `Import`)

| Campo | Tipo | Notas |
|---|---|---|
| `entityType` | `ExternalEntityType` | `ACCOUNT`, `CATEGORY`, `TRANSACTION`. |
| `externalId` | String | Id en Wallet de lo que no se pudo importar. |
| `reason` | String | Motivo legible (ej. "cuenta asociada no encontrada"). |
| `occurredAt` | Instant | |

## `ImportExternalRef` (tabla de mapeo, infraestructura de `walletimport`)

No es un concepto de dominio visible para el usuario — existe solo para la idempotencia (FR-006,
research.md #6).

| Campo | Tipo | Notas |
|---|---|---|
| `userId` | UUID | |
| `entityType` | `ExternalEntityType` | |
| `externalId` | String | Id original en Wallet. |
| `internalId` | UUID | Id propio ya creado (cuenta/categoría/movimiento). |

Restricción única: (`userId`, `entityType`, `externalId`).

## DTOs de la respuesta de Wallet (capa `application`, mapeo de la API externa)

- `WalletAccountDto`: `id`, `name`, `accountType` (String), `currencyCode`, `initialBalance`.
- `WalletCategoryDto`: `id`, `name`, `parentId` (nullable), `groupId` (nullable, de `group.id`).
- `WalletRecordDto`: `id`, `accountId`, `amount` (BigDecimal), `currencyCode`, `recordDate`,
  `recordType` (income/expense), `categoryId` (nullable), `counterParty` (nullable), `note` (nullable).

## API propia — request/response

- `StartImportRequest`: `walletApiToken` (String, requerido — nunca se persiste, solo vive en memoria
  durante la corrida).
- `ImportResponse`: `id`, `status`, `accountsImported`, `categoriesImported`, `transactionsImported`,
  `errors` (lista de `{entityType, externalId, reason}`), `startedAt`, `lastActivityAt`.

## Reglas de mapeo (resumen, detalle en research.md)

- Cuenta: `AccountType` según tabla de mapeo (research.md #2); `currency` = `currencyCode` de Wallet
  tal cual; `initialBalance` = `initialBalance.value` de Wallet.
- Categoría: `type` inferido de `group.id` (research.md #3); `parentCategoryId` resuelto en una segunda
  pasada (research.md #4).
- Movimiento: `type` = `recordType` de Wallet directo (mismo vocabulario ingreso/gasto); `amount` =
  `amount.value`; `date` = `recordDate`; `accountId`/`categoryId` resueltos vía `ImportExternalRef`;
  `description` = `counterParty` y `note` concatenados (el que esté presente; si ambos, separados por
  " — ").
