# Data Model: Interfaz Android para transacciones

Modelos del lado cliente (`android/app/src/main/java/com/walletapp/android/transactions/`), reflejando
uno a uno el contrato ya definido en `specs/003-transactions/contracts/transactions-api.yaml`. No se
introduce ningún campo ni entidad que el backend no exponga ya.

## `TransactionResponse` (DTO de red, `@Serializable`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `String` (UUID) | Siempre presente en la respuesta del servidor. |
| `type` | `CategoryType` | Reutiliza el enum ya existente (`INCOME`/`EXPENSE`), ver research.md #1. |
| `amount` | `Double` | Mayor a cero, validado también en el formulario (FR-002). |
| `date` | `String` (formato `YYYY-MM-DD`) | Elegida vía `DatePicker` (research.md #2), no texto libre. |
| `description` | `String?` | Opcional. |
| `accountId` | `String` (UUID) | Cuenta propia del usuario. |
| `categoryId` | `String?` (UUID) | Opcional; si se indica, del mismo tipo que `type`. |

## `TransactionRequest` (DTO de red, alta)

Igual que `TransactionResponse` pero sin `id` obligatorio: `id: String? = null`. Esta feature **no**
genera ids en el cliente (eso queda para una futura capacidad offline, fuera de alcance — ver
Assumptions en spec.md); siempre se envía `id = null` y el servidor genera un UUID v7.

## `TransactionUpdateRequest` (DTO de red, edición)

`amount`, `date`, `description`, `categoryId` — sin `type` ni `accountId` (inmutables tras la creación,
igual que en el contrato del backend). El formulario de edición reutiliza la misma pantalla que el de
alta, pero deshabilita los campos tipo/cuenta cuando `existingTransaction != null`.

## `BalanceResponse` (DTO de red)

`accountId: String`, `balance: Double` — consumido por `TransactionRepository.getBalance(accountId)`.

## `TransactionFilterState` (estado de UI, no serializado)

```kotlin
data class TransactionFilterState(
    val accountId: String? = null,
    val categoryId: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
)
```

Vive en `TransactionViewModel`; cualquier cambio dispara una nueva carga de la lista con esos filtros
como query params (mapea directo a los parámetros ya soportados por `GET /api/transactions`).

## `AccountWithBalance` (estado de UI, no serializado)

```kotlin
data class AccountWithBalance(val account: AccountResponse, val balance: Double)
```

Nuevo tipo que reemplaza `List<AccountResponse>` dentro de `AccountsUiState.Success` (feature 002,
modificado por esta feature). Ver research.md #4.

## Estados de pantalla

- `TransactionsUiState`: `Loading` / `Success(transactions: List<TransactionResponse>, filter: TransactionFilterState)` / `Error(message: String)` — mismo patrón sellado ya usado por `AccountsUiState`/`CategoriesUiState`.
- `AccountsUiState.Success` (modificado): pasa a `Success(accounts: List<AccountWithBalance>)`.
