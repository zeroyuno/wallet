---

description: "Task list for feature 007: Caché local y sincronización de movimientos en Android"
---

# Tasks: Caché local y sincronización de movimientos en Android

**Input**: Design documents from `/specs/007-transactions-local-sync/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/transactions-sync-api.yaml,
quickstart.md

**Tests**: Se incluyen tareas de test — el principio III de la constitución exige cobertura >80% en
`domain`/`application` del backend y tests unitarios para todo ViewModel/lógica con estado en Android;
no es opcional para esta feature.

**Organization**: Tareas agrupadas por user story (spec.md): US1 y US2 son P1, US3 es P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)
- **[Story]**: A qué user story pertenece (US1, US2, US3)

## Path Conventions

- Backend: `backend/src/main/java/com/walletapp/backend/transaction/...` (módulo ya existente),
  `backend/src/main/resources/db/migration/`, tests en
  `backend/src/test/java/com/walletapp/backend/transaction/...`
- Android: `android/app/src/main/java/com/walletapp/android/transactions/...` (módulo ya existente),
  tests en `android/app/src/test/java/com/walletapp/android/transactions/...`

---

## Phase 1: Setup

**Purpose**: Prerrequisitos de infraestructura antes de tocar dominio/aplicación.

- [X] T001 [P] Crear migración `V10__add_transaction_sync_support.sql` en
      `backend/src/main/resources/db/migration/`: columna `updated_at` (TIMESTAMP WITH TIME ZONE) en
      `transactions`, backfill `updated_at = created_at` para filas existentes, y tabla nueva
      `deleted_transactions (id UUID, user_id UUID, deleted_at TIMESTAMP WITH TIME ZONE)` con índice
      por `(user_id, deleted_at)` (data-model.md)
- [X] T002 [P] Agregar Room (runtime + ktx + compiler vía KSP), Paging 3 (runtime +
      `paging-compose` + integración con Room) y WorkManager a
      `android/gradle/libs.versions.toml` y como dependencias de `android/app/build.gradle.kts`
      (research.md #4, #6)

**Checkpoint**: `./mvnw -q compile` (backend) y `./gradlew :app:compileDebugKotlin` (Android) siguen
en verde tras agregar la migración/dependencias, sin código nuevo todavía.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Endpoint de sincronización en el backend + base de datos local en Android — ninguna user
story puede avanzar sin esto.

**⚠️ CRITICAL**: Bloquea todas las user stories.

### Backend

- [X] T003 Agregar campo `updatedAt` (Instant) a `Transaction` (dominio): `= createdAt` en `create()`,
      actualizado en `update()`, en
      `backend/src/main/java/com/walletapp/backend/transaction/domain/Transaction.java` — depende de
      T001
- [X] T004 [P] Agregar a `TransactionRepository` (puerto de dominio) los métodos
      `findChangedSince(userId, since, limit)` y `findDeletedSince(userId, since, limit)`, y un método
      para registrar un tombstone al borrar, en
      `backend/src/main/java/com/walletapp/backend/transaction/domain/TransactionRepository.java`
      (data-model.md: `DeletedTransactionTombstone`) — depende de T001
- [X] T005 Implementar T003/T004 en infraestructura: columna `updated_at` en `TransactionEntity`,
      nueva `DeletedTransactionEntity`/`SpringDataDeletedTransactionRepository`, e insertar el
      tombstone dentro de `JpaTransactionRepository.deleteByIdAndUserId` (o donde corresponda) en
      `backend/src/main/java/com/walletapp/backend/transaction/infrastructure/persistence/` — depende
      de T003, T004
- [X] T006 Agregar DTO `TransactionSyncCursor` y el caso de uso `TransactionService.sync(userId, since,
      limit)`: combina `findChangedSince`+`findDeletedSince`, ordena por `updatedAt` (desempate por
      `id`), recorta a `limit`, calcula `nextSince`/`hasMore` (research.md #1) en
      `backend/src/main/java/com/walletapp/backend/transaction/application/` — depende de T005
- [X] T007 Agregar `GET /api/transactions/sync` a `TransactionController` + `TransactionSyncResponse`
      (web dto), siguiendo `contracts/transactions-sync-api.yaml`, en
      `backend/src/main/java/com/walletapp/backend/transaction/infrastructure/web/` — depende de T006
- [X] T008 [P] Tests backend: unitarios de `TransactionServiceTest` (orden estable por
      updatedAt+id, respeta `limit`, incluye tombstones, `hasMore` correcto) + test de integración en
      `TransactionControllerIT` (Testcontainers) que valida el contrato completo (creación → aparece en
      `/sync`; edición → vuelve a aparecer; borrado → aparece en `deletedIds`) — depende de T007

### Android

- [X] T009 Crear `transactions/local/TransactionEntity.kt` (entidad Room: espejo de
      `TransactionResponse` + `updatedAt` + `syncState`) y `transactions/local/TransactionDao.kt`
      (`PagingSource<Int, TransactionEntity>` ordenado por fecha, upsert/delete, queries de filas con
      `syncState != SYNCED`) — depende de T002
- [X] T010 [P] Crear `db/AppDatabase.kt` (Room database raíz) y `di/DatabaseModule.kt` (provee
      `AppDatabase`/`TransactionDao` vía Hilt) — depende de T009
- [X] T011 [P] Crear `transactions/sync/TransactionSyncApi.kt` (Retrofit: `GET /api/transactions/sync`,
      DTOs de la respuesta) y registrar `provideTransactionSyncApi()` en `di/NetworkModule.kt` —
      depende de T007
- [X] T012 [P] Crear `transactions/local/SyncCursorStore.kt` (persiste el último `nextSince` recibido
      con éxito; `null` antes de la primera sincronización) — depende de T010

**Checkpoint**: Room compila e inyecta vía Hilt; `GET /api/transactions/sync` responde correctamente en
el backend (validado por T008). Listo para que las user stories consuman ambos.

---

## Phase 3: User Story 1 - Abrir la pantalla de movimientos sin esperas ni fallos (Priority: P1) 🎯 MVP

**Goal**: La pantalla de movimientos lee de Room (paginado) en vez de pedir la lista completa al
backend en cada apertura.

**Independent Test**: Con una cuenta de miles de movimientos ya sincronizados una vez, abrir la
pantalla repetidas veces (incluso sin red) y verificar que carga al instante con scroll progresivo.

### Implementation for User Story 1

- [X] T013 [US1] Implementar el pull incremental (research.md "Pull"): llama a `TransactionSyncApi`,
      aplica `upserts`/`deletedIds` a `TransactionDao`, repite hasta `hasMore=false`, y recién entonces
      persiste el cursor final en `SyncCursorStore` (si una página falla a mitad, el cursor anterior se
      conserva) en `transactions/sync/TransactionSyncEngine.kt` — depende de T009, T011, T012
- [X] T014 [P] [US1] Test unitario del pull (aplica upserts/deletes correctamente; conserva el cursor
      previo ante un fallo a mitad de página) con fakes de `TransactionSyncApi`/`TransactionDao`, en
      `android/app/src/test/java/com/walletapp/android/transactions/TransactionSyncEngineTest.kt` —
      depende de T013
- [X] T015 [US1] Modificar `transactions/TransactionRepository.kt`: expone
      `Flow<PagingData<TransactionResponse>>` leído desde `TransactionDao` (Paging 3) en vez de
      `list()` contra Retrofit — depende de T009
- [X] T016 [US1] Modificar `transactions/TransactionViewModel.kt` para observar ese `Flow` paginado
      (`.cachedIn(viewModelScope)`) — depende de T015
- [X] T017 [P] [US1] Actualizar `TransactionViewModelTest.kt` (existente, feature 004) al nuevo modelo
      basado en `Flow<PagingData<...>>` (usar `asSnapshot()` de Paging 3 para verificar contenido en
      tests) — depende de T016
- [X] T018 [US1] Modificar `transactions/ui/TransactionListScreen.kt` para consumir
      `LazyPagingItems` (scroll progresivo con Paging 3 Compose) en vez de la lista completa en memoria
      — depende de T016
- [X] T019 [US1] Disparar el pull (T013) al abrir la pantalla de movimientos y agregar "pull to
      refresh" manual sobre la lista — depende de T013, T018

**Checkpoint**: User Story 1 funcional de forma independiente — abrir la pantalla de movimientos carga
al instante y funciona sin red con lo ya sincronizado (Escenarios 4 y 5 de `quickstart.md`).

---

## Phase 4: User Story 2 - Crear, editar o eliminar sin esperar al servidor (Priority: P1)

**Goal**: Las escrituras se reflejan de inmediato en la base local y se envían al backend en segundo
plano.

**Independent Test**: Crear un movimiento y verificar que aparece en la lista al instante; confirmar
por separado (contra el backend) que también quedó guardado ahí una vez sincronizado.

### Implementation for User Story 2

- [X] T020 [US2] Modificar `transactions/TransactionRepository.kt`: `create`/`update`/`delete` escriben
      primero en `TransactionDao` marcando `syncState` (`PENDING_CREATE`/`PENDING_UPDATE`/
      `PENDING_DELETE`, con id generado en el cliente para las creaciones) y devuelven de inmediato, sin
      esperar respuesta de red — depende de T009, T015
- [X] T021 [US2] Implementar el push de pendientes (research.md "Push"): lee filas con
      `syncState != SYNCED` de `TransactionDao` y las envía a `TransactionApi.create/update/delete`
      (feature 003, sin cambios); al confirmar marca `SYNCED`; si la operación era `PENDING_CREATE` y la
      respuesta es 409, también marca `SYNCED` (research.md #3 — ya se sincronizó en un intento
      anterior) en `transactions/sync/TransactionSyncEngine.kt` — depende de T020
- [X] T022 [P] [US2] Test unitario del push (éxito → `SYNCED`; 409 sobre `PENDING_CREATE` → también
      `SYNCED`; error de red → estado sin cambios, sigue pendiente) con fakes de
      `TransactionApi`/`TransactionDao`, en
      `android/app/src/test/java/com/walletapp/android/transactions/TransactionSyncEngineTest.kt` —
      depende de T021
- [X] T023 [US2] Disparar el push (T021) inmediatamente después de cada escritura local (T020) —
      depende de T020, T021

**Checkpoint**: User Stories 1 y 2 funcionan de forma independiente entre sí — crear/editar/eliminar se
refleja al instante y llega al backend (Escenario 5, primera mitad, de `quickstart.md`).

---

## Phase 5: User Story 3 - Reintento automático cuando falla el envío (Priority: P2)

**Goal**: Un cambio pendiente que no logra enviarse se reintenta automáticamente más adelante, sin
intervención del usuario.

**Independent Test**: Crear un movimiento sin conexión, esperar sin reconectar (nada se pierde ni
bloquea), reconectar y verificar que se sincroniza sin que el usuario repita la acción.

### Implementation for User Story 3

- [X] T024 [US3] Crear `transactions/sync/TransactionSyncWorker.kt` (`CoroutineWorker`): corre pull
      (T013) + push (T021) y usa `Result.retry()`/backoff propio de WorkManager ante fallo — depende de
      T013, T021
- [X] T025 [P] [US3] Test unitario del worker (fallo → `Result.retry()`; éxito → `Result.success()`) en
      `android/app/src/test/java/com/walletapp/android/transactions/TransactionSyncWorkerTest.kt` —
      depende de T024
- [X] T026 [US3] Agendar un `PeriodicWorkRequest` (intervalo mínimo soportado, ~15 min, con constraint
      de red conectada) más un `OneTimeWorkRequest` inmediato al iniciar la app, en
      `WalletApplication.kt` — depende de T024
- [X] T027 [US3] Reemplazar el disparo directo del push (T023) por encolar un `OneTimeWorkRequest`
      inmediato del mismo worker (T024) tras cada escritura local — depende de T024, T023

**Checkpoint**: Las 3 user stories funcionan de forma independiente entre sí (Escenario 6 de
`quickstart.md`).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T028 Ejecutar los 7 escenarios de `quickstart.md` (backend por curl + manual en dispositivo con
      modo avión) end-to-end
- [X] T029 [P] Confirmar `./mvnw verify` (backend, cobertura >80% en `domain`/`application` de
      `transaction`) y `./gradlew :app:testDebugUnitTest` (Android) en verde
- [X] T030 [P] Revisar que ningún caller de Android quede usando `TransactionApi.list()` sin paginar
      tras el cambio (feature 004) — el endpoint `GET /api/transactions` sin paginar se deja intacto en
      el backend (lo siguen usando los scripts de verificación manual y otros contextos), solo deja de
      ser el camino de lectura principal de la app

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias
- **Foundational (Phase 2)**: depende de Setup — bloquea todas las user stories
- **US1 (Phase 3)** y **US2 (Phase 4)**: ambas P1, dependen solo de Foundational; US2 reutiliza
  `TransactionRepository`/`TransactionDao` de US1 (T009, T015) pero no su lógica de pull — pueden
  avanzar en paralelo una vez lista la Foundational
- **US3 (Phase 5)**: depende de que existan el pull (T013, de US1) y el push (T021, de US2) para
  envolverlos en el worker programado
- **Polish (Phase 6)**: depende de todas las user stories que se quieran incluir en esta ronda

### Parallel Opportunities

- T001 (Setup, backend) y T002 (Setup, Android) son paralelos entre sí
- T004 es paralelo a T003 dentro de Foundational (ambos dependen solo de T001)
- T010, T011, T012 son paralelos entre sí una vez creado T009/T007
- Los tests marcados [P] (T008, T014, T017, T022, T025) son paralelos a cualquier archivo de producción
  que no sea el que están probando
- T030 (Polish) es paralelo a T028/T029

---

## Parallel Example: tras completar Foundational (Phase 2)

```bash
# US1 y US2 pueden avanzar en paralelo (comparten TransactionDao/TransactionRepository de Foundational
# pero tocan métodos distintos):
Task: "Implementar el pull incremental en transactions/sync/TransactionSyncEngine.kt"
Task: "Modificar create/update/delete de TransactionRepository.kt para escribir primero en Room"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Completar Phase 1 (Setup) y Phase 2 (Foundational)
2. Completar Phase 3 (US1) y Phase 4 (US2) — ambas P1: la pantalla ya carga rápido y las escrituras se
   sienten instantáneas, aunque el reintento automático (US3) todavía no exista
3. **Validar** con los escenarios 1-5 de `quickstart.md`

### Incremental Delivery

1. Setup + Foundational → base lista (endpoint de sync + Room)
2. US1 → pantalla de movimientos rápida y usable sin red (lectura)
3. US2 → escritura optimista local + envío en segundo plano (sin reintento resiliente todavía)
4. US3 → reintento automático y disparo periódico/en background real

---

## Notes

- El endpoint `GET /api/transactions` (sin paginar, feature 003) no se modifica ni se retira — sigue
  disponible para otros usos (scripts de verificación, futuros clientes); esta feature solo cambia qué
  usa la app Android para poblar su pantalla principal de movimientos.
- La idempotencia de reintento (FR-008) se resuelve enteramente del lado del cliente (T021) — el
  backend no cambia su comportamiento ante un id duplicado (research.md #3).
- Por la convención de git del proyecto, la implementación de estas tareas se hace en la rama
  `007-transactions-local-sync`, con un solo PR al finalizar.
- **Desvío respecto al plan original (T024/T025)**: `CoroutineWorker`/`WorkerParameters` no se pueden
  instanciar en un test JUnit puro (sin Robolectric, que no forma parte del stack de este proyecto). La
  lógica de `TransactionSyncWorker` se extrajo a una clase aparte sin dependencias de Android,
  `TransactionSyncRunner` (`run(): ListenableWorker.Result`), testeada en
  `TransactionSyncRunnerTest.kt`; `TransactionSyncWorker` quedó como un envoltorio de una línea sin
  test directo. También se agregó `TransactionSyncScheduler` (interfaz + `WorkManagerTransactionSyncScheduler`)
  para poder testear `TransactionViewModel` sin depender de `WorkManager`/`Context`.
- **T028**: los escenarios de backend (1-3, más el equivalente al 7) se validaron con curl contra un
  backend local real (Postgres, no solo Testcontainers) — ver resultado en el historial de esta sesión.
  Los escenarios 4-6 (verificación visual en un dispositivo Android: apertura instantánea, modo avión,
  reconexión) requieren un dispositivo/emulador real y `adb`, no disponibles en este entorno — quedan
  pendientes de que el usuario los corra manualmente.
