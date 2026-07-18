# Research: Interfaz Android para transacciones

## 1. Tipo de movimiento: reutilizar `CategoryType` en vez de un `TransactionType` propio

**Decision**: El campo `type` de `TransactionRequest`/`TransactionResponse` en el cliente Android usa
el `CategoryType` (`INCOME`/`EXPENSE`) ya definido en `categories/CategoryApi.kt`, en vez de declarar un
enum `TransactionType` propio dentro de `transactions/`.

**Rationale**: En el backend (`specs/003-transactions/research.md`), `transaction.domain.TransactionType`
se duplicó deliberadamente respecto de `account.domain.CategoryType` porque la regla ArchUnit de
privacidad de `domain` por bounded context lo exige — cada contexto debe modelar su propio lenguaje
ubicuo sin importar tipos de otro. En Android **no existe esa regla**: la propia constitución aclara
que DDD/hexagonal aplica solo al backend y que Android es una capa de presentación MVVM sin
bounded contexts. Ambos enums son estructuralmente idénticos (mismos dos valores, mismo nombre de
serialización JSON), así que declarar un segundo enum solo agregaría un `displayLabel()` duplicado y un
punto más para que ambos se desincronicen si algún día cambian los valores.

**Alternatives considered**: Declarar `transactions/TransactionType` como espejo exacto de
`CategoryType` — descartado por violar YAGNI (principio V) sin ninguna regla de arquitectura que lo
justifique del lado Android.

## 2. Selector de fecha

**Decision**: `DatePickerDialog`/`DatePicker` de Jetpack Compose Material3 (ya incluido en el
`compose-bom` que el proyecto ya usa) para el campo `date` del formulario de movimiento.

**Rationale**: Cero dependencias nuevas; es el componente estándar de Material3 para este caso, y las
demás pantallas del proyecto ya siguen Material3 sin librerías de terceros para widgets de formulario.

**Alternatives considered**: Un campo de texto libre con validación de formato — descartado porque
delega en el usuario un formato de fecha exacto (`YYYY-MM-DD`, el que espera el backend) en vez de
garantizarlo por construcción, aumentando el riesgo de rechazos por FR-009 del backend por un problema
evitable de UX.

## 3. Filtros de la lista de movimientos (cuenta, categoría, rango de fechas)

**Decision**: Fila de `FilterChip` dentro de un `Row` con `horizontalScroll(rememberScrollState())`,
igual patrón ya usado y ya corregido (bug de text-wrapping) en la lista de categorías de la feature 002.
El estado de filtros vive en `TransactionViewModel` como un `TransactionFilterState` con
`accountId`, `categoryId`, `dateFrom`, `dateTo` opcionales, y cualquier cambio dispara una nueva carga
de la lista.

**Rationale**: Reutilizar un patrón de UI ya validado en producción (probado en dispositivo físico)
reduce el riesgo de reintroducir el mismo bug, y mantiene consistencia visual entre pantallas.

**Alternatives considered**: Un diálogo/bottom sheet de filtros aparte — descartado por agregar una
pantalla/estado adicional para un conjunto pequeño de filtros (3 criterios) que ya cabe cómodo como
chips en la propia lista, igual que already funciona para categorías.

## 4. Saldo actual por cuenta en la pantalla de cuentas

**Decision**: Tras listar las cuentas (`AccountRepository.list()`), `AccountViewModel` pide en paralelo
el saldo de cada una (`TransactionRepository.getBalance(accountId)`, usando `async`/`awaitAll` de
Coroutines) y expone `AccountsUiState.Success(accounts: List<AccountWithBalance>)`, donde
`AccountWithBalance` es un data class simple `(account: AccountResponse, balance: Double)`.

**Rationale**: Es el cambio mínimo sobre el `AccountViewModel` ya existente para cumplir FR-007/US2 sin
tocar el contrato de `AccountApi` (feature 002) ni introducir un estado global de saldos compartido que
esta feature no necesita (YAGNI). El paralelismo evita que el tiempo de carga crezca linealmente con la
cantidad de cuentas del usuario.

**Alternatives considered**: Pedir el saldo bajo demanda solo al entrar al detalle de cada cuenta —
descartado porque la spec (FR-007, Acceptance Scenario US2.1/US2.2) pide ver el saldo actualizado
directamente en la lista de cuentas, no en una pantalla de detalle que hoy no existe.

## 5. Tests unitarios para `TransactionViewModel` sin infraestructura de test previa

**Decision**: Se agregan `junit:junit` y `org.jetbrains.kotlinx:kotlinx-coroutines-test` (misma versión
que `kotlinx-coroutines-android`, ya fijada en el catálogo de versiones) como dependencias de test del
módulo `android/app`. Los tests usan un `TransactionRepository` de prueba implementado a mano (una
subclase/fake simple que devuelve `Result` fijos) en vez de una librería de mocking, ya que el proyecto
no tiene ninguna configurada todavía.

**Rationale**: Es el mínimo necesario para cumplir el principio III de la constitución ("cada ViewModel
con lógica lleva tests unitarios"), gap que arrastran las features 001/002 sin tests Android. Un fake
manual alcanza para el número reducido de métodos de `TransactionRepository` sin necesitar agregar una
dependencia de mocking (Mockito/MockK) solo para esto.

**Alternatives considered**: Agregar MockK (más idiomático para Kotlin/coroutines) — descartado por
ahora para no introducir una dependencia nueva de mayor superficie cuando un fake manual cubre el caso
de uso actual; queda como mejora futura si los tests de ViewModels crecen en complejidad.
