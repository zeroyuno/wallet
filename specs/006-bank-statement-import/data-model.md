# Data Model: Importar movimientos desde estados de cuenta bancarios en PDF

## `StatementImport` (aggregate root, contexto `bankstatement`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | |
| `userId` | UUID | Dueño de la importación (FR-002, aislamiento por usuario). |
| `accountId` | UUID | Cuenta propia indicada por el usuario al subir el PDF. |
| `status` | `StatementImportStatus` | `IN_PROGRESS`, `COMPLETED`, `FAILED`. |
| `transactionsImported` | int | Contador de movimientos creados. |
| `errors` | lista de `StatementLineError` | Ver abajo (FR-008). |
| `failureReason` | String, nullable | Motivo si `status = FAILED` (ej. fallo de red hacia Anthropic, PDF sin contenido interpretable — FR-009). |
| `startedAt` / `lastActivityAt` | Instant | |

## `StatementLineError` (value object, embebido en `StatementImport`)

| Campo | Tipo | Notas |
|---|---|---|
| `rawText` | String | El texto o fragmento de la línea del PDF que no se pudo interpretar/crear, tal como lo devuelve el modelo. |
| `reason` | String | Motivo legible (ej. "no se pudo determinar el monto", "fecha ambigua"). |
| `occurredAt` | Instant | |

## `StatementImportLineHash` (tabla de idempotencia, infraestructura de `bankstatement`)

No es un concepto de dominio visible para el usuario — existe solo para la deduplicación (FR-006,
research.md #3).

| Campo | Tipo | Notas |
|---|---|---|
| `userId` | UUID | |
| `accountId` | UUID | |
| `hash` | String | SHA-256 de `accountId+date+amount+description` normalizado. |
| `internalTransactionId` | UUID | Id de la transacción ya creada. |

Restricción única: (`userId`, `hash`).

## DTOs de la extracción del LLM (capa `application`)

- `ExtractedTransactionDto`: `date` (LocalDate), `amount` (BigDecimal, siempre positivo), `type`
  (String, `INCOME`/`EXPENSE`), `description` (String).
- Línea no interpretada: `rawText` (String), `reason` (String) — mapea 1:1 a `StatementLineError`.

## API propia — request/response

- `POST /api/statement-imports` (multipart/form-data): `file` (el PDF), `accountId` (UUID) — responde
  `202` con `StatementImportResponse`.
- `StatementImportResponse`: `id`, `accountId`, `status`, `transactionsImported`, `errors` (lista de
  `{rawText, reason}`), `failureReason`, `startedAt`, `lastActivityAt`.

## Reglas de mapeo (resumen, detalle en research.md)

- Movimiento extraído → transacción real: `type` = el `type` que devuelve el modelo directo (mismo
  vocabulario `INCOME`/`EXPENSE` ya usado en el resto de la app); `amount` = magnitud positiva tal
  cual la devuelve el modelo; `date` = la fecha extraída; `description` = la descripción/concepto
  extraído; `categoryId` = `null` (FR-012); `accountId` = la cuenta indicada por el usuario al subir
  el PDF (no se re-detecta ni se valida contra el contenido del PDF, ver Assumptions de spec.md).
