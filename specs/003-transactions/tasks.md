# Tasks: Transacciones (ingresos y gastos)

**Input**: Design documents from `/specs/003-transactions/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/transactions-api.yaml](contracts/transactions-api.yaml),
[quickstart.md](quickstart.md). Requiere las features 001 (Autenticación) y 002 (Cuentas y
categorías) ya implementadas.

**Tests**: incluidos — constitución, principio III: >80% cobertura en `domain`/`application`.

**Scope**: solo backend esta ronda (ver Assumptions en spec.md) — sin tareas de Android.

**Organization**: tareas agrupadas por historia de usuario; dentro de cada historia, siguiendo el
orden hexagonal `domain → application → infrastructure`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo
- **[Story]**: US1 = Registrar y ver movimientos, US2 = Editar/eliminar movimientos, US3 = Filtrar
  movimientos

## Path Conventions

- Backend: `backend/src/main/java/com/walletapp/backend/transaction/{domain,application,infrastructure}/...`
- Cambios (aditivos) en `backend/src/main/java/com/walletapp/backend/account/application/` y
  `account/infrastructure/web/AccountExceptionHandler.java`

---

## Phase 1: Setup

- [x] T001 Agregar la dependencia `com.github.f4b6a3:uuid-creator` a `backend/pom.xml` (generación de
      UUID v7 para `TransactionId`, ver research.md)
- [ ] T002 Crear migración Flyway `backend/src/main/resources/db/migration/V3__create_transactions.sql`
      con la tabla `transactions` (FK `ON DELETE RESTRICT` hacia `accounts`/`categories`, ver
      [data-model.md](data-model.md))

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: el dominio del nuevo contexto `transaction`, los métodos de solo lectura que expone
`account` para que `transaction` los consuma, y el manejo genérico de FR-010.

**⚠️ CRITICAL**: ninguna historia de usuario puede empezar hasta que esta fase esté completa.

### Domain (`transaction/domain`) — sin dependencias de Spring/JPA ni de `account.domain`

- [ ] T003 [P] Crear value object `TransactionId` (genera UUID **v7** vía `uuid-creator` cuando no se
      provee uno — ver research.md) y el enum `TransactionType` (INCOME, EXPENSE — propio de este
      contexto) en `backend/src/main/java/com/walletapp/backend/transaction/domain/` (depende de T001)
- [ ] T004 [P] Crear agregado `Transaction` (factory `create(Optional<TransactionId> id, userId,
      type, amount, date, description, accountId, categoryId)` — genera un `TransactionId` v7 si
      `id` está vacío; método `update`; `type` y `accountId` inmutables tras la creación) en
      `transaction/domain/Transaction.java` (depende de T003)
- [ ] T005 [P] Crear puerto de salida `TransactionRepository` (incluye `existsByIdAndUserId` para
      detectar un `id` duplicado provisto por el cliente, FR-011, y `sumNetAmountForAccount`) en
      `transaction/domain/TransactionRepository.java` (depende de T004)
- [ ] T006 [P] Crear excepciones de dominio (`TransactionNotFoundException`,
      `InvalidTransactionAccountException`, `InvalidTransactionCategoryException`,
      `CategoryTypeMismatchException`, `DuplicateTransactionIdException`) en
      `transaction/domain/exception/`
- [ ] T007 [ARQUITECTURA] Agregar `"transaction"` a `BOUNDED_CONTEXTS` en
      `backend/src/test/java/com/walletapp/backend/ArchitectureTest.java`

### Application (`transaction/application`)

- [ ] T008 [P] Crear DTOs (`TransactionCommand` — incluye `id` opcional, `TransactionUpdateCommand`,
      `TransactionView`, `TransactionFilter`) en `transaction/application/dto/`

### Infrastructure — persistencia

- [ ] T009 [P] Crear `TransactionEntity` (JPA), `SpringDataTransactionRepository` (incluye
      `existsByIdAndUserId` y la query de agregación para `sumNetAmountForAccount`) y
      `JpaTransactionRepository` (implementa el puerto de T005) en
      `transaction/infrastructure/persistence/` (depende de T004, T005)

### Cross-context: métodos de solo lectura en `account.application` (ver research.md)

- [ ] T010 [P] Agregar `AccountService.existsOwnedByUser(UUID, UUID): boolean` y
      `AccountService.getInitialBalanceIfOwnedByUser(UUID, UUID): Optional<BigDecimal>` (devuelven
      solo tipos primitivos, nunca `AccountView`) en
      `backend/src/main/java/com/walletapp/backend/account/application/AccountService.java`, con sus
      tests unitarios en `AccountServiceTest.java`
- [ ] T011 [P] Agregar `CategoryService.findTypeIfOwnedByUser(UUID, UUID): Optional<String>` (el tipo
      como `String`, nunca `CategoryType`) en `account/application/CategoryService.java`, con su test
      unitario en `CategoryServiceTest.java`

### FR-010: no eliminar cuenta/categoría con transacciones asociadas

- [ ] T012 Agregar `@ExceptionHandler(DataIntegrityViolationException.class)` → `409` genérico en
      `account/infrastructure/web/AccountExceptionHandler.java` (depende de T002; el test de
      integración que ejercita este handler se agrega en la Fase 3, una vez que existe
      `POST /api/transactions` para crear la transacción que bloquea el delete — ver T016)

**Checkpoint**: dominio, persistencia y puentes de solo lectura hacia `account` listos — las
historias de usuario pueden empezar.

---

## Phase 3: User Story 1 - Registrar y ver mis movimientos (Priority: P1) 🎯 MVP

**Goal**: un usuario autenticado registra un ingreso o gasto sobre una cuenta propia (y,
opcionalmente, una categoría propia del mismo tipo), con un `id` opcional provisto por el cliente
(FR-011), ve sus movimientos, y el saldo de la cuenta refleja el efecto correcto.

**Independent Test**: `POST /api/transactions` crea un movimiento; `GET /api/transactions` lo
incluye; `GET /api/accounts/{id}/balance` refleja el efecto (quickstart.md, escenario 1).

### Tests for User Story 1

- [ ] T013 [P] [US1] Test unitario de `TransactionService.create()`/`list()` (cuenta ajena →
      excepción, categoría ajena o de tipo distinto → excepción, monto ≤ 0 → excepción, `id`
      provisto por el cliente se respeta, `id` duplicado → `DuplicateTransactionIdException`; sin
      `id` se genera uno nuevo; mockeando `AccountService`/`CategoryService`/`TransactionRepository`)
      en
      `backend/src/test/java/com/walletapp/backend/transaction/application/TransactionServiceTest.java`
- [ ] T014 [P] [US1] Test de integración de `POST`/`GET /api/transactions` y
      `GET /api/accounts/{id}/balance` (creación con y sin `id` propio, listado, aislamiento entre
      usuarios, 404 sobre cuenta/categoría ajena, 400 por tipo no coincidente o monto inválido, 409
      por `id` duplicado) con Testcontainers en
      `backend/src/test/java/com/walletapp/backend/transaction/infrastructure/web/TransactionControllerIT.java`
- [ ] T015 [US1] Test unitario adicional: dos `TransactionId` generados sin `id` explícito son
      temporalmente ordenables (UUID v7 — comparar que el generado después sea mayor) en
      `TransactionServiceTest.java` o un test dedicado de `TransactionId`
- [ ] T016 [US1] Test de integración: crear una transacción sobre una cuenta (o con una categoría)
      propia y verificar que `DELETE /api/accounts/{id}` / `DELETE /api/categories/{id}` responden
      `409` (FR-010) en `AccountControllerIT.java`/`CategoryControllerIT.java` (depende de T012, T018)

### Implementation for User Story 1

- [ ] T017 [US1] Implementar `TransactionService.create()` (respeta el `id` del cliente si viene,
      responde con `DuplicateTransactionIdException` si ya existe, genera uno v7 si no viene) y
      `.list()` (sin filtros todavía, eso es US3) en
      `transaction/application/TransactionService.java` (depende de T004, T005, T008, T010, T011)
- [ ] T018 [US1] Implementar `GET /api/accounts/{id}/balance` en
      `transaction/infrastructure/web/BalanceController.java` + `BalanceResponse` DTO (depende de
      T005, T010)
- [ ] T019 [US1] Implementar `POST`/`GET /api/transactions` en
      `transaction/infrastructure/web/TransactionController.java` + DTOs web (`TransactionRequest`
      con `id` opcional, `TransactionResponse`) + `TransactionExceptionHandler.java` (mapea
      `DuplicateTransactionIdException` → 409) (depende de T017)

**Checkpoint**: User Story 1 funcional; verificar cobertura con `./mvnw verify` antes de seguir.

---

## Phase 4: User Story 2 - Editar y eliminar mis movimientos (Priority: P2)

**Goal**: un usuario corrige o elimina sus propios movimientos, y el saldo de la cuenta queda
correcto en ambos casos.

**Independent Test**: `PUT`/`DELETE /api/transactions/{id}` funciona sobre un movimiento propio,
devuelve 404 sobre uno ajeno, y `GET /api/accounts/{id}/balance` refleja el ajuste (quickstart.md,
escenarios 3 y 4).

### Tests for User Story 2

- [ ] T020 [P] [US2] Test unitario de `TransactionService.update()`/`delete()` (el saldo tras un
      update refleja solo el nuevo monto, no viejo+nuevo; delete revierte el efecto; ambos rechazan
      un movimiento ajeno) en `TransactionServiceTest.java`
- [ ] T021 [P] [US2] Test de integración de `PUT`/`DELETE /api/transactions/{id}` (éxito propio, 404
      sobre movimiento ajeno, y verificación del saldo antes/después vía
      `GET /api/accounts/{id}/balance`) en `TransactionControllerIT.java`

### Implementation for User Story 2

- [ ] T022 [US2] Implementar `TransactionService.update()` y `.delete()` en
      `TransactionService.java` (depende de T017)
- [ ] T023 [US2] Implementar `PUT`/`DELETE /api/transactions/{id}` + `TransactionUpdateRequest` DTO
      (sin `id`, `type` ni `accountId` — todos inmutables tras la creación, ver research.md) en
      `TransactionController.java` (depende de T022)

**Checkpoint**: US1 y US2 funcionan de forma independiente.

---

## Phase 5: User Story 3 - Filtrar mis movimientos (Priority: P3)

**Goal**: un usuario filtra su lista de movimientos por cuenta, categoría o rango de fechas, de
forma independiente o combinada.

**Independent Test**: filtrar por cada criterio por separado y combinado devuelve únicamente los
movimientos que cumplen el filtro (quickstart.md, escenario 2).

### Tests for User Story 3

- [ ] T024 [P] [US3] Test unitario de `TransactionService.list()` con `TransactionFilter` (por
      cuenta, por categoría, por rango de fechas, y combinaciones) en `TransactionServiceTest.java`
- [ ] T025 [P] [US3] Test de integración de `GET /api/transactions` con query params `accountId`,
      `categoryId`, `dateFrom`, `dateTo` (por separado y combinados) en `TransactionControllerIT.java`

### Implementation for User Story 3

- [ ] T026 [US3] Extender la query de `SpringDataTransactionRepository`/`JpaTransactionRepository`
      para aplicar `TransactionFilter` en `transaction/infrastructure/persistence/` (depende de T005,
      T009)
- [ ] T027 [US3] Aceptar los query params `accountId`, `categoryId`, `dateFrom`, `dateTo` en
      `GET /api/transactions` (mapeo a `TransactionFilter`) en `TransactionController.java` (depende
      de T019, T026)

**Checkpoint**: las tres historias de usuario funcionan de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T028 Ejecutar `./mvnw verify` y confirmar que `domain`/`application` de `transaction` superan
      el 80% de cobertura (constitución, principio III)
- [ ] T029 [P] Documentar los nuevos endpoints en `README.md` (incluyendo el `id` opcional de
      `POST /api/transactions`, FR-011)
- [ ] T030 Ejecutar [quickstart.md](quickstart.md) de punta a punta (los 7 escenarios, incluyendo
      aislamiento entre usuarios y el bloqueo de FR-010) contra la implementación real

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias
- **Foundational (Fase 2)**: depende de Setup — bloquea las tres historias
- **US1 (Fase 3)**: depende de Foundational — es el MVP
- **US2 (Fase 4)**: depende de que exista la implementación base de US1 (mismo `TransactionService`/
  `TransactionController`)
- **US3 (Fase 5)**: depende de que exista la implementación base de US1 (mismo
  `TransactionService.list()`); independiente de US2
- **Polish (Fase 6)**: depende de las historias que se quieran incluir en el release

### Parallel Opportunities

- T003-T011 (Foundational) en paralelo — archivos distintos, sin dependencias cruzadas entre sí
  salvo lo ya anotado
- US2 y US3 pueden implementarse en paralelo una vez completa US1 (tocan partes distintas de
  `TransactionService`/`TransactionController`, aunque conviene coordinarlas si se editan los mismos
  archivos)

---

## Implementation Strategy

### MVP First

1. Setup + Foundational
2. US1 (registrar y ver movimientos, con saldo y con `id` opcional del cliente) — validar con
   quickstart.md, escenarios 1 y 6

### Incremental Delivery

1. Setup + Foundational → base lista, incluye el bloqueo de FR-010
2. US1 → MVP: registrar (con `id` opcional), ver, y consultar saldo
3. US2 → editar/eliminar con saldo correcto
4. US3 → filtros
5. Polish

---

## Notes

- El escenario de aislamiento entre usuarios y el de FR-010 (quickstart.md, secciones 5 y 6) son los
  más importantes de validar manualmente — FR-010 en particular fue exactamente el tipo de bug (401
  en vez de 409 por una excepción de integridad no traducida) que se corrigió en la feature 002 para
  subcategorías; T012/T016 verifican que acá se maneja bien desde el principio.
- El soporte de `id` provisto por el cliente (FR-011) existe para una futura app Android con
  capacidad offline — esta ronda no implementa esa app, solo el soporte del backend (ver Assumptions
  en spec.md).
- Verificar que los tests fallan antes de implementar (TDD) según el principio III de la
  constitución.
- Sin tareas de Android en esta spec — queda para una fase/spec posterior (ver Assumptions en
  spec.md).
