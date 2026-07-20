---

description: "Task list for feature 005: Importar datos desde BudgetBakers Wallet"
---

# Tasks: Importar datos desde BudgetBakers Wallet

**Input**: Design documents from `/specs/005-budgetbakers-import/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/imports-api.yaml, quickstart.md

**Tests**: Se incluyen tareas de test (unitarios de dominio/aplicación + integración con Testcontainers)
porque el principio III de la constitución exige cobertura >80% en `domain`/`application`, igual que en
todas las features anteriores del backend.

**Organization**: Tareas agrupadas por user story (spec.md): US1 y US2 son P1, US3 es P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)
- **[Story]**: A qué user story pertenece (US1, US2, US3)

## Path Conventions

Proyecto backend existente: `backend/src/main/java/com/walletapp/backend/walletimport/...` (nuevo
contexto), `backend/src/test/java/com/walletapp/backend/walletimport/...` (tests nuevos), más cambios
puntuales en `account/` y `transaction/` (nuevos métodos de creación cross-context).

---

## Phase 1: Setup

**Purpose**: Esqueleto del nuevo contexto y su migración de base de datos.

- [ ] T001 Crear migración `backend/src/main/resources/db/migration/V5__create_imports.sql`: tablas
      `imports` (estado, contadores, cursor), `import_errors` (detalle por importación) e
      `import_external_refs` (mapeo id-Wallet → id-propio, único por `user_id`+`entity_type`+
      `external_id`, ver data-model.md)
- [ ] T002 [P] Crear el esqueleto de paquetes
      `backend/src/main/java/com/walletapp/backend/walletimport/{domain,application/dto,infrastructure/{persistence,client,web/dto}}`

**Checkpoint**: `./mvnw compile` compila con los paquetes vacíos y la migración aplicada.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Dominio, puertos, cliente HTTP hacia Wallet y el fixture de test que necesitan todas las
user stories.

**⚠️ CRITICAL**: Bloquea todas las user stories.

- [ ] T003 Dominio: `ImportId`, `ImportStatus` (IN_PROGRESS/COMPLETED/PAUSED_RATE_LIMIT/FAILED),
      `ExternalEntityType` (ACCOUNT/CATEGORY/TRANSACTION), `ImportError` (value object) en
      `walletimport/domain/`
- [ ] T004 Dominio: agregado `Import` (factory `start(userId)`, `recordAccountImported()`,
      `recordCategoryImported()`, `recordTransactionImported()`, `recordError(entityType, externalId,
      reason)`, `pauseForRateLimit(cursorPhase, cursorRecordDate)`, `complete()`, `fail()`,
      `reconstitute(...)`) — depende de T003
- [ ] T005 Dominio: puertos `ImportRepository`, `ExternalReferenceRepository` (registrar/consultar
      mapeo id-Wallet → id-propio) y excepciones `ImportNotFoundException`,
      `InvalidWalletTokenException`, `RateLimitExceededException` — depende de T004
- [ ] T006 [P] Infraestructura: `ImportEntity` (JPA, incluye `ImportError` embebido/tabla hija),
      `SpringDataImportRepository`, `JpaImportRepository` (implementa el puerto) — depende de T005
- [ ] T007 [P] Infraestructura: `ExternalReferenceEntity` (JPA), `SpringDataExternalReferenceRepository`,
      `JpaExternalReferenceRepository` (implementa el puerto) — depende de T005
- [ ] T008 Application: puerto `WalletImportGateway` (métodos `listAccounts`, `listCategories`,
      `listRecords(fromDate, offset, limit)`) + DTOs `WalletAccountDto`/`WalletCategoryDto`/
      `WalletRecordDto`/`WalletPageDto` en `walletimport/application/dto/` — depende de T002
- [ ] T009 Infraestructura: `WalletApiHttpClient` (implementa `WalletImportGateway` con `RestClient`
      contra la API real de Wallet, ver research.md #1; mapea `429` a `RateLimitExceededException` y
      `401` a `InvalidWalletTokenException`) — depende de T008
- [ ] T010 [P] Config: `WalletImportAsyncConfig` (`@EnableAsync` + `TaskExecutor`) en
      `walletimport/infrastructure/`
- [ ] T011 [P] Test fixture: `FakeWalletImportGateway` (`@TestConfiguration`, `@Primary` en perfil de
      test, datos fijos configurables por test) para los tests de integración, sin llamar a la API real
      de Wallet (ver research.md — Testing) — depende de T008
- [ ] T012 ArchUnit: agregar `"walletimport"` a `BOUNDED_CONTEXTS` en `ArchitectureTest.java`

**Checkpoint**: El contexto `walletimport` compila, tiene sus puertos y su cliente HTTP listo para
inyectarse; el fixture de test está disponible para las user stories.

---

## Phase 3: User Story 1 - Importar mis cuentas y categorías (Priority: P1) 🎯 MVP

**Goal**: El usuario provee su token de Wallet y trae sus cuentas y categorías (con jerarquía) a esta
app.

**Independent Test**: Con una API key válida de Wallet con al menos una cuenta y una categoría con
subcategoría, iniciar la importación y verificar que ambas aparecen en "Mis cuentas"/"Mis categorías".

### Implementation for User Story 1

- [ ] T013 [US1] Agregar `AccountService.createFromExternalImport(userId, name, accountTypeName,
      currency, initialBalance): UUID` — mapea el `String` de tipo de cuenta al `AccountType` propio
      según la tabla de research.md #2 (fallback a `OTHER`) — depende de Foundational
- [ ] T014 [P] [US1] Tests unitarios del nuevo método en `AccountServiceTest.java` (cada fila de la
      tabla de mapeo, incluido el fallback) — depende de T013
- [ ] T015 [US1] Agregar `CategoryService.createFromExternalImport(userId, name, categoryTypeName,
      parentCategoryId): UUID` — depende de Foundational
- [ ] T016 [P] [US1] Tests unitarios del nuevo método en `CategoryServiceTest.java` — depende de T015
- [ ] T017 [US1] `ImportService.importAccounts(...)`: trae cuentas de Wallet vía el gateway,
      verifica idempotencia contra `ExternalReferenceRepository`, mapea y crea vía
      `AccountService.createFromExternalImport`, registra la referencia externa y el contador
      (FR-002, FR-006) — depende de T004, T005, T009, T013
- [ ] T018 [US1] `ImportService.importCategories(...)` en dos pasadas (research.md #4): primera
      pasada crea sin padre e infiere el tipo desde `group.id` (research.md #3); segunda pasada resuelve
      `parentCategoryId` vía las referencias ya registradas (FR-003) — depende de T015, T017
- [ ] T019 [US1] `ImportController`: `POST /api/imports` (valida token no vacío, crea `Import` en
      `IN_PROGRESS`, dispara `ImportService.processImport(...)` de forma async, responde `202` con el
      `id`) + `StartImportRequest`/`ImportResponse` + `ImportExceptionHandler` (FR-001, FR-009) —
      depende de T017, T018
- [ ] T020 [P] [US1] Tests unitarios de `ImportService` para `importAccounts`/`importCategories`
      (mockeando `AccountService`/`CategoryService`/`WalletImportGateway`/los repos) — depende de T017,
      T018
- [ ] T021 [US1] Test de integración `ImportControllerIT`: inicia una importación con
      `FakeWalletImportGateway` y verifica que las cuentas y categorías (con jerarquía) quedan creadas
      correctamente — depende de T011, T019

**Checkpoint**: User Story 1 funcional de punta a punta de forma independiente.

---

## Phase 4: User Story 2 - Importar mi historial de movimientos (Priority: P1)

**Goal**: El usuario trae su historial de movimientos desde Wallet, asociado a las cuentas/categorías
ya importadas, tolerando el rate limit externo sin perder progreso ni duplicar datos.

**Independent Test**: Con una cuenta ya importada (US1) que tiene movimientos en Wallet, importar el
historial y verificar que aparecen en "Mis movimientos" con monto, fecha, tipo y cuenta correctos.

### Implementation for User Story 2

- [ ] T022 [US2] Agregar `TransactionService.createFromExternalImport(userId, type, amount, date,
      description, accountId, categoryId): UUID` — depende de Foundational
- [ ] T023 [P] [US2] Tests unitarios del nuevo método en `TransactionServiceTest.java` — depende de
      T022
- [ ] T024 [US2] `ImportService.importTransactions(...)`: pagina movimientos de Wallet desde el cursor
      (`recordDate`, ver research.md #5), resuelve `accountId`/`categoryId` vía
      `ExternalReferenceRepository` (si no resuelve, registra `ImportError` y continúa con el resto en
      vez de interrumpir — Edge Cases de spec.md), arma la descripción desde `counterParty`+`note`
      (data-model.md), aplica idempotencia (FR-004, FR-005, FR-006) — depende de T022
- [ ] T025 [US2] `ImportService`: al recibir `RateLimitExceededException` desde el gateway, pausa la
      importación (`pauseForRateLimit` con el cursor tal cual quedó) y termina la corrida sin marcarla
      como fallida (FR-008) — depende de T024
- [ ] T026 [US2] `ImportService`/`ImportController`: al iniciar una importación para un usuario que ya
      tiene una en estado `PAUSED_RATE_LIMIT`, continuar esa misma importación desde su cursor en vez
      de crear una nueva (FR-008) — depende de T019, T025
- [ ] T027 [P] [US2] Tests unitarios: idempotencia de movimientos, movimiento con cuenta/categoría no
      resuelta se omite y queda registrado como error, pausa por rate limit con el cursor correcto, y
      reanudación que continúa desde ese cursor sin repetir lo ya importado — depende de T024, T025,
      T026
- [ ] T028 [US2] Test de integración: correr la importación dos veces sobre los mismos datos de
      `FakeWalletImportGateway` no duplica ninguna cuenta, categoría ni movimiento (FR-006, SC-003,
      quickstart escenario 3) — depende de T021, T027

**Checkpoint**: User Stories 1 y 2 funcionan de forma independiente entre sí.

---

## Phase 5: User Story 3 - Ver el progreso y el resultado de la importación (Priority: P2)

**Goal**: El usuario puede consultar el estado de una importación en curso o finalizada, con el detalle
de qué se importó y qué falló.

**Independent Test**: Iniciando una importación con datos de prueba conocidos, el resumen final debe
coincidir exactamente con lo que había en la cuenta de Wallet usada.

### Implementation for User Story 3

- [ ] T029 [US3] `ImportController`: `GET /api/imports/{id}` (propio del usuario autenticado; `404` si
      no existe o pertenece a otro usuario, no `403` — mismo patrón ya usado en el resto de la API)
      (FR-007, FR-010) — depende de T019
- [ ] T030 [P] [US3] Tests unitarios de `ImportService.get(...)` (aislamiento por usuario) — depende de
      T029
- [ ] T031 [US3] Test de integración: `GET` expone `errors[]` con `entityType`/`externalId`/`reason`
      cuando hubo movimientos omitidos durante la importación; verifica aislamiento entre usuarios
      (`404`, no `403`) — depende de T029

**Checkpoint**: Las 3 user stories funcionan de forma independiente entre sí.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T032 Verificar cobertura JaCoCo >80% en `domain`/`application` de `walletimport`
- [ ] T033 [P] Correr los 6 escenarios de `quickstart.md` manualmente contra la API real de
      BudgetBakers Wallet, con datos de prueba propios en una cuenta de Wallet real
- [ ] T034 Revisar que los mensajes de `ImportError` sean legibles para el usuario (sin detalles
      técnicos), consistente con el resto de la app

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias
- **Foundational (Phase 2)**: depende de Setup — bloquea todas las user stories
- **US1 (Phase 3)**: depende solo de Foundational
- **US2 (Phase 4)**: depende de Foundational y de que existan `ImportService`/`ImportController`
  básicos de US1 (T017-T019) para extenderlos con la fase de movimientos, reanudación y pausa
- **US3 (Phase 5)**: depende de que exista `ImportController` (T019 de US1) para agregarle el endpoint
  de consulta
- **Polish (Phase 6)**: depende de todas las user stories que se quieran incluir en esta ronda

### Parallel Opportunities

- T002 (Setup) es paralelo a T001
- T006, T007, T010, T011 (Foundational) son paralelos entre sí una vez creados los puertos (T005/T008)
- Los tests unitarios marcados [P] (T014, T016, T020, T023, T027, T030) son paralelos a cualquier otro
  archivo de producción que no sea el que están probando

---

## Parallel Example: Foundational (Phase 2)

```bash
# Tras crear los puertos (T005, T008), estos 4 pueden avanzar en paralelo:
Task: "Infraestructura: ImportEntity + SpringDataImportRepository + JpaImportRepository"
Task: "Infraestructura: ExternalReferenceEntity + SpringDataExternalReferenceRepository + JpaExternalReferenceRepository"
Task: "Config: WalletImportAsyncConfig (@EnableAsync + TaskExecutor)"
Task: "Test fixture: FakeWalletImportGateway"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Completar Phase 1 (Setup) y Phase 2 (Foundational)
2. Completar Phase 3 (US1) y Phase 4 (US2) — ambas P1: sin esto la importación no trae lo único que
   realmente le importa al usuario (su historial de movimientos)
3. **Validar** con los escenarios 1, 2 y 3 de `quickstart.md`

### Incremental Delivery

1. Setup + Foundational → base lista (dominio, puertos, cliente HTTP, fixture de test)
2. US1 → cuentas y categorías importables
3. US2 → historial de movimientos importable, con idempotencia y reanudación
4. US3 → visibilidad de progreso/resultado

---

## Notes

- Sigue el mismo patrón de puertos primitivos cross-context ya establecido en la feature 003 (ver
  Complexity Tracking en plan.md) — ningún tipo de dominio de `account`/`transaction` se filtra hacia
  `walletimport`.
- Por la regla de git del proyecto, la implementación de estas tareas se hace en la rama
  `005-budgetbakers-import` (a crear justo antes de `/speckit-implement`), con un solo PR al finalizar.
