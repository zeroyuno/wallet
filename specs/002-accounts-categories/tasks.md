# Tasks: Gestión de cuentas y categorías

**Input**: Design documents from `/specs/002-accounts-categories/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/accounts-categories-api.yaml](contracts/accounts-categories-api.yaml),
[quickstart.md](quickstart.md). Requiere la feature 001 (Autenticación) ya implementada.

**Tests**: incluidos — constitución, principio III: >80% cobertura en `domain`/`application`.

**Organization**: tareas agrupadas por historia de usuario; dentro de cada historia, siguiendo el
orden hexagonal `domain → application → infrastructure`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo
- **[Story]**: US1 = Crear/ver cuentas, US2 = Editar/eliminar cuentas, US3 = Crear/gestionar
  categorías, US4 = Subcategorías

## Path Conventions

- Backend: `backend/src/main/java/com/walletapp/backend/account/{domain,application,infrastructure}/...`
- Android: `android/app/src/main/java/com/walletapp/android/{accounts,categories}/...`

---

## Phase 1: Setup

- [ ] T001 Crear migración Flyway `backend/src/main/resources/db/migration/V2__create_accounts_and_categories.sql`
      con las tablas `accounts` y `categories` (ver [data-model.md](data-model.md))

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: el dominio del contexto `account` y la generalización de las reglas ArchUnit.

**⚠️ CRITICAL**: ninguna historia de usuario puede empezar hasta que esta fase esté completa.

### Domain (`account/domain`) — sin dependencias de Spring/JPA

- [ ] T002 [P] Crear value objects `AccountId`, `CurrencyCode` y el enum `AccountType` en
      `backend/src/main/java/com/walletapp/backend/account/domain/`
- [ ] T003 [P] Crear agregado `Account` (factory `create`, método `rename`) en
      `account/domain/Account.java` (depende de T002)
- [ ] T004 [P] Crear value object `CategoryId` y el enum `CategoryType` en `account/domain/`
- [ ] T005 [P] Crear agregado `Category` (factory `create`, método `rename`) en
      `account/domain/Category.java` (depende de T004)
- [ ] T006 [P] Crear puertos de salida `AccountRepository`, `CategoryRepository` (interfaces) en
      `account/domain/` (depende de T003, T005)
- [ ] T007 [P] Crear excepciones de dominio (`AccountNotFoundException`, `CategoryNotFoundException`,
      `DuplicateCategoryException`, `InvalidCategoryHierarchyException`) en `account/domain/exception/`
- [ ] T008 [ARQUITECTURA] Generalizar `backend/src/test/java/com/walletapp/backend/ArchitectureTest.java`
      para iterar sobre una lista de bounded contexts (`auth`, `account`) en vez de reglas hardcodeadas
      solo para `auth` (ver research.md)

### Application (`account/application`)

- [ ] T009 [P] Crear DTOs (`AccountCommand`, `AccountView`, `CategoryCommand`, `CategoryView`) en
      `account/application/dto/`

### Infrastructure — persistencia

- [ ] T010 [P] Crear `AccountEntity` (JPA) y `SpringDataAccountRepository`/`JpaAccountRepository`
      (implementa el puerto de T006) en `account/infrastructure/persistence/` (depende de T003, T006)
- [ ] T011 [P] Crear `CategoryEntity` (JPA) y `SpringDataCategoryRepository`/`JpaCategoryRepository`
      (implementa el puerto de T006) en `account/infrastructure/persistence/` (depende de T005, T006)

**Checkpoint**: dominio y persistencia de `account` listos — las historias de usuario pueden empezar.

---

## Phase 3: User Story 1 - Crear y ver mis cuentas (Priority: P1) 🎯 MVP

**Goal**: un usuario autenticado crea cuentas y ve únicamente las suyas.

**Independent Test**: `POST /api/accounts` crea una cuenta; `GET /api/accounts` la incluye
(quickstart.md, escenario 1).

### Tests for User Story 1

- [ ] T012 [P] [US1] Test unitario de `AccountService.create`/`list` (validación de nombre y saldo
      numérico, FR-012; sin Spring ni DB) en
      `backend/src/test/java/com/walletapp/backend/account/application/AccountServiceTest.java`
- [ ] T013 [P] [US1] Test de integración de `POST`/`GET /api/accounts` (incluyendo que un usuario
      nunca ve cuentas de otro) con Testcontainers en
      `backend/src/test/java/com/walletapp/backend/account/infrastructure/web/AccountControllerIT.java`

### Implementation for User Story 1

- [ ] T014 [US1] Implementar `AccountService.create()` y `.list()` en
      `account/application/AccountService.java` (depende de T003, T006, T009)
- [ ] T015 [US1] Implementar `POST`/`GET /api/accounts` en
      `account/infrastructure/web/AccountController.java` + DTOs web (`AccountRequest`,
      `AccountResponse`) + `AccountExceptionHandler` (depende de T014)
- [ ] T016 [P] [US1] Crear `AccountApi.kt` (Retrofit) y `AccountRepository.kt` en
      `android/app/src/main/java/com/walletapp/android/accounts/`
- [ ] T017 [P] [US1] Crear `AccountListScreen.kt` en
      `android/app/src/main/java/com/walletapp/android/accounts/ui/AccountListScreen.kt`
- [ ] T018 [US1] Implementar `AccountViewModel` (listar y crear) en
      `android/app/src/main/java/com/walletapp/android/accounts/AccountViewModel.kt` (depende de T016)

**Checkpoint**: User Story 1 funcional; verificar cobertura con `./mvnw verify` antes de seguir.

---

## Phase 4: User Story 2 - Editar y eliminar mis cuentas (Priority: P2)

**Goal**: un usuario corrige o elimina sus propias cuentas, sin poder tocar las de otros.

**Independent Test**: `PUT`/`DELETE /api/accounts/{id}` funciona sobre una cuenta propia y devuelve
404 sobre una cuenta ajena (quickstart.md, escenarios 2 y 3).

### Tests for User Story 2

- [ ] T019 [P] [US2] Test unitario de `AccountService.update`/`delete` (verifica que usan
      `findByIdAndUserId`) en `AccountServiceTest.java`
- [ ] T020 [P] [US2] Test de integración de `PUT`/`DELETE /api/accounts/{id}` (éxito propio, 404 sobre
      cuenta ajena — el caso más crítico de esta historia) en `AccountControllerIT.java`

### Implementation for User Story 2

- [ ] T021 [US2] Implementar `AccountService.update()` y `.delete()` en `AccountService.java`
      (depende de T014)
- [ ] T022 [US2] Implementar `PUT`/`DELETE /api/accounts/{id}` en `AccountController.java` (depende de
      T021)
- [ ] T023 [P] [US2] Crear `AccountFormScreen.kt` (editar) en
      `android/app/src/main/java/com/walletapp/android/accounts/ui/AccountFormScreen.kt`
- [ ] T024 [US2] Extender `AccountViewModel`/`AccountRepository` con editar y eliminar (depende de
      T016)

**Checkpoint**: US1 y US2 de cuentas funcionan de forma independiente.

---

## Phase 5: User Story 3 - Crear y gestionar categorías (Priority: P1)

**Goal**: un usuario autenticado crea, edita y elimina sus categorías de ingreso/gasto.

**Independent Test**: `POST /api/categories` crea una categoría; una segunda con mismo nombre+tipo
devuelve 409 (quickstart.md, escenario 4).

### Tests for User Story 3

- [ ] T025 [P] [US3] Test unitario de `CategoryService` (unicidad `userId+type+name`, FR-007; sin
      Spring ni DB) en
      `backend/src/test/java/com/walletapp/backend/account/application/CategoryServiceTest.java`
- [ ] T026 [P] [US3] Test de integración de `POST`/`GET`/`PUT`/`DELETE /api/categories` (incluyendo
      409 por duplicado y aislamiento entre usuarios) con Testcontainers en
      `backend/src/test/java/com/walletapp/backend/account/infrastructure/web/CategoryControllerIT.java`

### Implementation for User Story 3

- [ ] T027 [US3] Implementar `CategoryService.create/list/update/delete()` (sin la validación de
      jerarquía todavía, eso es US4) en `account/application/CategoryService.java` (depende de T005,
      T006, T009)
- [ ] T028 [US3] Implementar `POST/GET/PUT/DELETE /api/categories` en
      `account/infrastructure/web/CategoryController.java` + DTOs web (`CategoryRequest`,
      `CategoryResponse`) (depende de T027)
- [ ] T029 [P] [US3] Crear `CategoryApi.kt` y `CategoryRepository.kt` en
      `android/app/src/main/java/com/walletapp/android/categories/`
- [ ] T030 [P] [US3] Crear `CategoryListScreen.kt` y `CategoryFormScreen.kt` en
      `android/app/src/main/java/com/walletapp/android/categories/ui/`
- [ ] T031 [US3] Implementar `CategoryViewModel` (CRUD) en
      `android/app/src/main/java/com/walletapp/android/categories/CategoryViewModel.kt` (depende de
      T029)

**Checkpoint**: gestión de categorías funcional de forma independiente de las cuentas.

---

## Phase 6: User Story 4 - Organizar categorías con subcategorías (Priority: P3)

**Goal**: un usuario agrupa categorías bajo una categoría padre del mismo tipo.

**Independent Test**: asignar `parentCategoryId` al crear/editar una categoría y verificar que se
refleja al consultarla; un ciclo o auto-referencia es rechazado (quickstart.md, sección 4; spec.md
Edge Cases).

### Tests for User Story 4

- [ ] T032 [P] [US4] Test unitario de la validación de ciclos/auto-referencia de
      `CategoryService.validateParent()` en `CategoryServiceTest.java`
- [ ] T033 [P] [US4] Test de integración de creación/edición con `parentCategoryId` válido, inválido
      (otro usuario/tipo) y cíclico en `CategoryControllerIT.java`

### Implementation for User Story 4

- [ ] T034 [US4] Añadir validación de `parentCategoryId` (mismo usuario, mismo tipo, sin ciclos —
      recorre la cadena de padres vía `CategoryRepository`) en `CategoryService.create()`/`update()`
      (depende de T027; ver research.md)
- [ ] T035 [P] [US4] Mostrar/seleccionar la categoría padre en `CategoryListScreen.kt`/
      `CategoryFormScreen.kt` (depende de T030)

**Checkpoint**: las cuatro historias de usuario funcionan de forma independiente.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T036 Ejecutar `./mvnw verify` y confirmar que `domain`/`application` de `account` superan el 80%
      de cobertura (constitución, principio III)
- [ ] T037 [P] Documentar los nuevos endpoints en `README.md`
- [ ] T038 Ejecutar [quickstart.md](quickstart.md) de punta a punta (incluyendo el escenario de
      aislamiento entre dos usuarios) contra la implementación real

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias
- **Foundational (Fase 2)**: depende de Setup — bloquea las cuatro historias
- **US1/US3 (Fases 3, 5)**: pueden avanzar en paralelo entre sí tras Foundational (cuentas y
  categorías son subsistemas independientes dentro del mismo contexto)
- **US2 (Fase 4)**: depende de que exista la implementación base de US1 (mismo `AccountService`)
- **US4 (Fase 6)**: depende de que exista la implementación base de US3 (mismo `CategoryService`)
- **Polish (Fase 7)**: depende de las historias que se quieran incluir en el release

### Parallel Opportunities

- T002-T007, T009-T011 (Foundational) en paralelo — archivos distintos
- US1 (cuentas) y US3 (categorías) pueden implementarse en paralelo por ser subsistemas
  independientes dentro de `account`
- Tareas Android [P] de cada historia en paralelo con las de backend de la misma historia

---

## Implementation Strategy

### MVP First

1. Setup + Foundational
2. US1 (crear/ver cuentas) — validar con quickstart.md, escenario 1
3. US3 (crear/gestionar categorías) — validar con quickstart.md, escenario 4

### Incremental Delivery

1. Setup + Foundational → base lista
2. US1 → MVP de cuentas
3. US3 → MVP de categorías (en paralelo con US1 si hay capacidad)
4. US2 → edición/eliminación de cuentas
5. US4 → subcategorías
6. Polish

---

## Notes

- El escenario de aislamiento entre usuarios (quickstart.md, sección 3) es el más importante de
  validar manualmente antes de dar por cerrada cualquier historia — es el requisito central de la
  constitución (principio IV) para esta feature.
- Verificar que los tests fallan antes de implementar (TDD) según el principio III de la constitución.
- T008 (generalizar ArchUnit) es la única tarea de esta feature que toca código de la feature 001 —
  hacerla primero y confirmar que `ArchitectureTest` sigue en verde para `auth` antes de seguir.
