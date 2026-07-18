---

description: "Task list for feature 004: Interfaz Android para transacciones"
---

# Tasks: Interfaz Android para transacciones

**Input**: Design documents from `/specs/004-android-transactions-ui/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/transactions-ui-api.md, quickstart.md

**Tests**: Se incluyen tareas de test (unitarios de ViewModel) porque el principio III de la
constitución las exige explícitamente para todo ViewModel con lógica — no es opcional para esta
feature aunque las features Android anteriores (001/002) no las tuvieran.

**Organization**: Tareas agrupadas por user story (spec.md): US1/US2 son P1, US3 es P2, US4 es P3.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)
- **[Story]**: A qué user story pertenece (US1, US2, US3, US4)

## Path Conventions

Proyecto mobile existente: `android/app/src/main/java/com/walletapp/android/...` (producción),
`android/app/src/test/java/com/walletapp/android/...` (tests unitarios, carpeta nueva — ver research.md #5).

---

## Phase 1: Setup

**Purpose**: Habilitar tests unitarios en el módulo Android (no existen todavía) antes de escribir el
primer ViewModel de esta feature.

- [ ] T001 Agregar `junit` y `kotlinx-coroutines-test` (misma versión que `kotlinx-coroutines-android`)
      a `android/gradle/libs.versions.toml` y como dependencias `testImplementation` en
      `android/app/build.gradle.kts`
- [ ] T002 [P] Crear el paquete `android/app/src/main/java/com/walletapp/android/transactions/` (y su
      subpaquete `ui/`) como esqueleto vacío

**Checkpoint**: `./gradlew :app:testDebugUnitTest` corre (sin tests todavía) sin errores de configuración.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Cliente HTTP de transacciones compartido por US1 (movimientos) y US2 (saldo de cuentas) —
ninguna historia puede avanzar sin esto.

**⚠️ CRITICAL**: Bloquea todas las user stories.

- [ ] T003 Crear `transactions/TransactionApi.kt`: DTOs `TransactionRequest`, `TransactionUpdateRequest`,
      `TransactionResponse`, `BalanceResponse` (ver data-model.md; `type` reutiliza `CategoryType`, ver
      research.md #1) + interfaz Retrofit (`GET /api/transactions` con query params opcionales
      `accountId`/`categoryId`/`dateFrom`/`dateTo`, `POST`, `GET/PUT/DELETE /api/transactions/{id}`,
      `GET /api/accounts/{id}/balance`)
- [ ] T004 Crear `transactions/TransactionRepository.kt`: `list(filter)`, `create(...)`, `update(id, ...)`,
      `delete(id)`, `getBalance(accountId)`, todos devolviendo `Result<T>` vía `runCatching` (mismo
      patrón que `AccountRepository`/`CategoryRepository`) — depende de T003
- [ ] T005 [P] Registrar `provideTransactionApi()` en `di/NetworkModule.kt` — depende de T003

**Checkpoint**: `TransactionRepository` compila e inyecta correctamente vía Hilt; listo para consumirse
desde cualquier ViewModel.

---

## Phase 3: User Story 1 - Registrar y ver mis movimientos (Priority: P1) 🎯 MVP

**Goal**: Un usuario registra un ingreso o gasto desde la app y lo ve en su lista de movimientos.

**Independent Test**: Con sesión iniciada y una cuenta propia, registrar un gasto desde el formulario y
verificar que aparece en la lista de movimientos.

### Implementation for User Story 1

- [ ] T006 [US1] Implementar `TransactionsUiState` (Loading/Success/Error) y `TransactionViewModel`
      (carga inicial de lista vía `TransactionRepository.list()`, `create(...)` con callback
      `Result<Unit>`) en `transactions/TransactionViewModel.kt` — depende de T004
- [ ] T007 [P] [US1] Tests unitarios de `TransactionViewModel` (lista carga bien / error de red, alta
      exitosa dispara `Result.success` / alta fallida no navega) usando un `TransactionRepository` fake,
      en `android/app/src/test/java/com/walletapp/android/transactions/TransactionViewModelTest.kt` —
      depende de T006
- [ ] T008 [US1] Implementar `transactions/ui/TransactionListScreen.kt`: lista de movimientos (tipo,
      monto, fecha, cuenta), distinguiendo visualmente ingreso/gasto (FR-003), estado vacío, FAB para
      nuevo movimiento — depende de T006
- [ ] T009 [US1] Implementar `transactions/ui/TransactionFormScreen.kt` (alta): campos tipo, monto,
      fecha (`DatePicker`, research.md #2), cuenta (selector entre cuentas propias), categoría opcional
      (filtrada por tipo elegido), descripción opcional; validación de monto/fecha/cuenta en el propio
      formulario antes de llamar al servidor (FR-002) — depende de T006
- [ ] T010 [US1] Agregar `Screen.TransactionsList`/`Screen.TransactionForm`, su entrada en el mapa de
      `BackHandler`, y el botón "Mis movimientos" en `HomeScreen` de `MainActivity.kt` — depende de
      T008, T009
- [ ] T011 [US1] Guardia de "sin cuentas propias": si el usuario no tiene ninguna cuenta, el punto de
      entrada a nuevo movimiento muestra un mensaje y dirige a crear una cuenta en vez de abrir el
      formulario (FR-010, edge case) — depende de T009

**Checkpoint**: User Story 1 funcional de punta a punta de forma independiente.

---

## Phase 4: User Story 2 - Ver el saldo actualizado de mis cuentas (Priority: P1)

**Goal**: La pantalla de cuentas muestra el saldo actual (inicial + movimientos), no el saldo inicial
estático.

**Independent Test**: Con una cuenta con saldo inicial conocido, registrar un movimiento y verificar que
el saldo mostrado en "Mis cuentas" cambia en consecuencia.

### Implementation for User Story 2

- [ ] T012 [US2] Agregar `AccountWithBalance` y modificar `AccountsUiState.Success` para exponer
      `List<AccountWithBalance>`, resolviendo el saldo de cada cuenta en paralelo vía
      `TransactionRepository.getBalance` (`async`/`awaitAll`) en `accounts/AccountViewModel.kt` —
      depende de T004 (Foundational), independiente de US1
- [ ] T013 [P] [US2] Tests unitarios de `AccountViewModel` (saldo se resuelve por cuenta; error al
      resolver saldo de una cuenta no rompe la lista completa) en
      `android/app/src/test/java/com/walletapp/android/accounts/AccountViewModelTest.kt` — depende de
      T012
- [ ] T014 [US2] Actualizar `AccountRow` en `accounts/ui/AccountListScreen.kt` para mostrar
      `AccountWithBalance.balance` en vez de `account.initialBalance` — depende de T012

**Checkpoint**: User Stories 1 y 2 funcionan de forma independiente entre sí.

---

## Phase 5: User Story 3 - Editar y eliminar mis movimientos (Priority: P2)

**Goal**: Un usuario corrige o elimina un movimiento propio ya registrado.

**Independent Test**: Editar el monto de un movimiento existente y verificar que la lista y el saldo de
la cuenta se actualizan; eliminarlo y verificar que ambos vuelven a su estado previo.

### Implementation for User Story 3

- [ ] T015 [US3] Agregar `update(id, ...)` y `delete(id)` con callback `Result<Unit>` a
      `TransactionViewModel.kt` (mismo patrón que evita el bug de fallo silencioso de la feature 002) —
      depende de T006
- [ ] T016 [P] [US3] Tests unitarios de edición/eliminación (éxito y error) en
      `TransactionViewModelTest.kt` — depende de T015
- [ ] T017 [US3] Extender `TransactionFormScreen.kt` a modo edición: parámetro
      `existingTransaction: TransactionResponse?`, deshabilita tipo/cuenta cuando no es `null`, botón
      eliminar con diálogo de confirmación (FR-006, Acceptance Scenario US2.3 de spec.md) — depende de
      T009, T015
- [ ] T018 [US3] Conectar el tap sobre una fila de `TransactionListScreen.kt` a
      `Screen.TransactionForm(transaction)` en `MainActivity.kt` — depende de T010, T017

**Checkpoint**: User Stories 1, 2 y 3 funcionan de forma independiente entre sí.

---

## Phase 6: User Story 4 - Filtrar mis movimientos (Priority: P3)

**Goal**: Un usuario filtra su lista de movimientos por cuenta, categoría o rango de fechas.

**Independent Test**: Con movimientos en distintas cuentas/categorías/fechas, aplicar cada filtro por
separado y combinado, y verificar que la lista muestra solo lo que corresponde.

### Implementation for User Story 4

- [ ] T019 [US4] Agregar `TransactionFilterState` (accountId/categoryId/dateFrom/dateTo) al
      `TransactionViewModel.kt`; cualquier cambio dispara una nueva carga con esos filtros como query
      params de `TransactionRepository.list()` — depende de T006
- [ ] T020 [P] [US4] Tests unitarios de las transiciones de estado al aplicar/quitar filtros en
      `TransactionViewModelTest.kt` — depende de T019
- [ ] T021 [US4] Agregar fila de `FilterChip` (cuenta/categoría/rango de fechas) con
      `horizontalScroll(rememberScrollState())` a `TransactionListScreen.kt` (research.md #3) — depende
      de T008, T019

**Checkpoint**: Las 4 user stories funcionan de forma independiente entre sí.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T022 Ejecutar en un dispositivo físico los 7 escenarios de `quickstart.md`, incluyendo los edge
      cases de error de red (#6) y usuario sin cuentas (#7)
- [ ] T023 [P] Revisar consistencia de mensajes de error entre `TransactionFormScreen`,
      `TransactionListScreen` y `AccountListScreen` (mismo tono/formato ya usado en cuentas/categorías)
- [ ] T024 Confirmar `./gradlew :app:testDebugUnitTest` en verde con todos los tests de esta feature

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias
- **Foundational (Phase 2)**: depende de Setup — bloquea todas las user stories
- **US1 (Phase 3)** y **US2 (Phase 4)**: ambas P1, dependen solo de Foundational; independientes entre
  sí (US2 no usa nada de la UI de US1, solo `TransactionRepository` de Foundational)
- **US3 (Phase 5)**: depende de que existan `TransactionViewModel`/`TransactionFormScreen` (T006, T009
  de US1) para extenderlos con edición/eliminación
- **US4 (Phase 6)**: depende de que exista `TransactionListScreen`/`TransactionViewModel` (T006, T008 de
  US1) para agregar filtros sobre la lista ya funcional
- **Polish (Phase 7)**: depende de todas las user stories que se quieran incluir en esta ronda

### Parallel Opportunities

- T002 (Setup) es paralelo a T001
- T005 (Foundational) es paralelo respecto de T004 una vez creado T003
- T012 (US2) puede empezar en paralelo con toda la Phase 3 (US1) una vez terminada la Foundational —
  ambas dependen solo de T004
- Los tests unitarios marcados [P] (T007, T013, T016, T020) son paralelos a cualquier otro archivo de
  producción que no sea el que están probando

---

## Parallel Example: tras completar Foundational (Phase 2)

```bash
# US1 y US2 pueden avanzar en paralelo (no comparten archivos de producción):
Task: "Implementar TransactionsUiState y TransactionViewModel en transactions/TransactionViewModel.kt"
Task: "Agregar AccountWithBalance y modificar AccountViewModel.kt para resolver saldo por cuenta"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Completar Phase 1 (Setup) y Phase 2 (Foundational)
2. Completar Phase 3 (US1) y Phase 4 (US2) — ambas P1, la app ya es útil con solo estas dos: se puede
   registrar un movimiento y ver el saldo real de las cuentas
3. **Validar** con los escenarios 1 y 2 de `quickstart.md`

### Incremental Delivery

1. Setup + Foundational → base lista
2. US1 + US2 → MVP funcional (registrar movimientos, ver saldo real)
3. US3 → corregir/eliminar movimientos
4. US4 → filtros sobre la lista

---

## Notes

- Sigue el mismo patrón `Result<Unit>` ya establecido en `accounts/`/`categories/` para evitar el bug de
  fallo silencioso ya corregido en la feature 002 — ningún `onSaved`/`onDeleted` se dispara sin
  verificar el `Result` primero.
- Por la regla de git del proyecto, la implementación de estas tareas se hace en la rama
  `004-android-transactions-ui` (ya creada por el hook de branch), con un solo PR al finalizar.
