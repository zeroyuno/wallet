# Implementation Plan: Interfaz Android para transacciones

**Branch**: `004-android-transactions-ui` | **Date**: 2026-07-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-android-transactions-ui/spec.md`

## Summary

Nueva sección `transactions/` en la app Android (mismo patrón MVVM que `accounts/`/`categories/`):
pantallas para registrar, listar/filtrar, editar y eliminar movimientos, consumiendo tal cual el
contrato ya implementado en `specs/003-transactions/contracts/transactions-api.yaml`. Además, se
modifica `AccountListScreen`/`AccountViewModel` (feature 002) para mostrar el saldo actual de cada
cuenta (`GET /api/accounts/{id}/balance`) en vez del `initialBalance` estático que muestran hoy — es
el cambio mínimo necesario para que la pantalla de cuentas deje de estar desactualizada en cuanto
existen movimientos (FR-007, US2).

## Technical Context

**Language/Version**: Kotlin 2.2.21 sobre el módulo `android/` ya existente (Jetpack Compose,
compileSdk 37, minSdk 26). Sin cambios de versión de plataforma.

**Primary Dependencies**: Reutiliza las ya configuradas en `android/app/build.gradle.kts` — Jetpack
Compose + Material3, Hilt (DI), Retrofit + `kotlinx-serialization` (HTTP), OkHttp, Coroutines/Flow. No
se agregan dependencias de producción nuevas. Se agregan dependencias de **test** (ver Complexity
Tracking) porque el módulo Android no tiene ninguna configurada todavía, pese a que la constitución
exige tests unitarios para todo ViewModel con lógica.

**Storage**: N/A — sin persistencia local en esta ronda (Assumptions de spec.md: sin soporte offline).
Cada pantalla consulta el backend directamente, igual que `accounts/`/`categories/`.

**Testing**: JUnit 4 + `kotlinx-coroutines-test` para `TransactionViewModel` (nuevo) y para los ajustes
de `AccountViewModel` (fetch de saldo). Verificación manual en dispositivo físico vía `adb`/
`uiautomator dump`, mismo procedimiento ya usado en las features 001/002.

**Target Platform**: Android (dispositivo físico en la misma red que el backend, mismo `BASE_URL` ya
configurado en `NetworkModule.kt`). Sin cambios de plataforma.

**Project Type**: mobile-app — nuevo feature module (`transactions/`) dentro del `android/app` ya
existente, más modificaciones puntuales a `accounts/` (feature 002).

**Performance Goals**: Expectativas estándar de una app de consumo — listas y formularios responden a
la interacción sin demoras perceptibles; sin metas de throughput (misma escala pequeña que el resto de
la app).

**Constraints**: Sigue el patrón MVVM ya establecido: `Composable → ViewModel → Repository → Retrofit`,
`UiState` sellado (Loading/Success/Error) por pantalla, y el patrón `Result<Unit>` para
crear/editar/eliminar (el ViewModel solo dispara la navegación de éxito si el `Result` es exitoso —
evita el bug de fallo silencioso ya corregido en la feature 002). `MainActivity.kt` agrega nuevas
entradas a `Screen` y a su mapa de `BackHandler` para las pantallas nuevas.

**Scale/Scope**: 2 pantallas nuevas (lista+filtros de movimientos, formulario de alta/edición) más el
ajuste de la pantalla de cuentas existente. Mismo alcance pequeño que las features Android anteriores.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. API Contract-First**: PASS — no se define ningún endpoint nuevo; se consume exactamente el
  contrato ya publicado en la feature 003 (`transactions-api.yaml`), sin improvisar campos ni rutas.
- **II. Arquitectura hexagonal y DDD**: N/A para esta feature — aplica solo al backend según la propia
  constitución ("Android: mantiene MVVM sin cambios... DDD/hexagonal aplica solo al backend"). Se seguirá
  igualmente el MVVM ya establecido.
- **III. Tests y cobertura >80%**: PASS con una acción explícita — el módulo Android no tiene tests
  configurados todavía (gap preexistente, no introducido por esta feature), así que se agregan las
  dependencias de test mínimas y se cubre `TransactionViewModel` con tests unitarios, cumpliendo "cada
  ViewModel con lógica lleva tests unitarios". No aplica el umbral JaCoCo del 80% (es exclusivo del
  backend).
- **IV. Aislamiento de datos por usuario**: PASS — no se agrega lógica de autorización nueva en el
  cliente; se apoya enteramente en que el backend (feature 003, ya validado) filtra por usuario
  autenticado vía el token ya adjuntado por `NetworkModule`'s interceptor.
- **V. Simplicidad y alcance por spec (YAGNI)**: PASS — sin soporte offline, sin gráficos/reportes, sin
  paginación, sin crear cuentas/categorías desde el formulario de movimiento (todo documentado en
  Assumptions de spec.md). El ajuste al saldo de cuentas es el mínimo necesario para que US2 sea
  correcta, no una refactorización más amplia de `accounts/`.

No hay violaciones que requieran justificación adicional más allá de la explicada en Complexity
Tracking (dependencias de test nuevas).

## Project Structure

### Documentation (this feature)

```text
specs/004-android-transactions-ui/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── transactions-ui-api.md   # referencia al contrato ya definido en 003, sin duplicarlo
└── tasks.md
```

### Source Code (repository root)

```text
android/app/src/
├── main/java/com/walletapp/android/
│   ├── transactions/
│   │   ├── TransactionApi.kt              # TransactionRequest/Response, BalanceResponse, endpoints
│   │   │                                    # de transactions-api.yaml (incluye GET .../balance)
│   │   ├── TransactionRepository.kt       # list(filtros)/create/update/delete/getBalance -> Result<T>
│   │   ├── TransactionViewModel.kt        # TransactionsUiState, TransactionFilterState
│   │   └── ui/
│   │       ├── TransactionListScreen.kt   # lista + chips de filtro (cuenta/categoría/fechas)
│   │       └── TransactionFormScreen.kt   # alta y edición (mismo screen, existingTransaction: Response?)
│   ├── accounts/
│   │   ├── AccountViewModel.kt            # [MODIFICADO] AccountsUiState.Success ahora incluye saldo
│   │   │                                    # actual por cuenta (vía TransactionRepository.getBalance)
│   │   └── ui/AccountListScreen.kt        # [MODIFICADO] muestra balance actual, no initialBalance
│   ├── di/NetworkModule.kt                # [MODIFICADO] provideTransactionApi()
│   └── MainActivity.kt                    # [MODIFICADO] Screen.TransactionsList / .TransactionForm
│                                            # + entradas en el mapa de BackHandler
└── test/java/com/walletapp/android/       # [NUEVO] no existía ningún test Android todavía
    └── transactions/TransactionViewModelTest.kt
```

**Structure Decision**: Nuevo paquete `transactions/` en `android/app`, calcado del patrón ya usado por
`accounts/`/`categories/` (feature 002). El saldo de cuenta se resuelve desde `TransactionRepository`
(vive junto al resto de la lógica de transacciones, igual que en el backend donde `BalanceController`
vive en el contexto `transaction` aunque su URL cuelga de `/api/accounts`) y `AccountViewModel` lo
consume directamente — en Android no hay una regla de aislamiento entre paquetes equivalente a ArchUnit,
así que esta dependencia cruzada simple es aceptable y evita introducir una capa de indirección
adicional solo para replicar en el cliente una separación que en el backend existe por otra razón (DDD).

## Complexity Tracking

| Decisión | Por qué es necesaria | Alternativa más simple descartada porque |
|-----------|------------|---------------------------------------|
| Se agregan dependencias de test (JUnit 4, `kotlinx-coroutines-test`) al módulo `android/app`, inexistentes hasta ahora | La constitución exige tests unitarios para todo ViewModel con lógica (principio III); `TransactionViewModel` tiene lógica de filtrado/estado que debe cubrirse | No agregar tests y dejarlo solo con verificación manual en dispositivo — rechazado porque incumple explícitamente el principio III y porque las features anteriores (001/002) ya arrastran este mismo gap sin corregirlo; esta feature es la oportunidad de no seguir acumulándolo |
| `AccountViewModel` pasa a depender de `TransactionRepository` (además de `AccountRepository`) para resolver el saldo por cuenta | US2/FR-007 exigen mostrar el saldo actual, no el `initialBalance`, y ese cálculo lo expone el backend bajo el contexto `transaction` (`GET /api/accounts/{id}/balance`) | Duplicar el cálculo de saldo en el cliente sumando transacciones localmente — rechazado: requeriría traer todas las transacciones de cada cuenta solo para sumarlas, más lógica y más superficie de bugs que llamar al endpoint que el backend ya expone para ese propósito exacto (SC-002 exige que coincida exactamente con el servidor) |
