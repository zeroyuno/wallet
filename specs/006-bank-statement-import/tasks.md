---

description: "Task list for feature 006: Importar movimientos desde estados de cuenta bancarios en PDF"
---

# Tasks: Importar movimientos desde estados de cuenta bancarios en PDF

**Input**: Design documents from `/specs/006-bank-statement-import/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/statement-imports-api.yaml, quickstart.md

**Tests**: Se incluyen tareas de test (unitarios de dominio/aplicación + integración con Testcontainers)
porque el principio III de la constitución exige cobertura >80% en `domain`/`application`, igual que en
todas las features anteriores del backend.

**Organization**: Tareas agrupadas por user story (spec.md): US1 y US2 son P1, US3 es P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)
- **[Story]**: A qué user story pertenece (US1, US2, US3)

## Path Conventions

Proyecto backend existente: `backend/src/main/java/com/walletapp/backend/bankstatement/...` (nuevo
contexto), `backend/src/test/java/com/walletapp/backend/bankstatement/...` (tests nuevos), más un
método nuevo en `transaction/application/TransactionService.java` (ver plan.md, Complexity Tracking).

---

## Phase 1: Setup

**Purpose**: Esqueleto del nuevo contexto, migración de base de datos y configuración transversal.

- [X] T001 Crear migración `backend/src/main/resources/db/migration/V7__create_statement_imports.sql`:
      tablas `statement_imports` (estado, contador, `failure_reason`), `statement_import_errors`
      (detalle por importación) y `statement_import_line_hashes` (idempotencia, único por
      `user_id`+`hash`, ver data-model.md)
- [X] T002 [P] Crear el esqueleto de paquetes
      `backend/src/main/java/com/walletapp/backend/bankstatement/{domain,application/dto,infrastructure/{persistence,llmclient,web/dto}}`
- [X] T003 [P] Configurar `spring.servlet.multipart.max-file-size`/`max-request-size` (20MB) y la
      propiedad `app.anthropic.api-key=${ANTHROPIC_API_KEY:}` en `application.properties`
      (research.md #2, #6)

**Checkpoint**: `./mvnw compile` compila con los paquetes vacíos y la migración aplicada.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Dominio, puertos, cliente hacia Anthropic y el fixture de test que necesitan todas las
user stories.

**⚠️ CRITICAL**: Bloquea todas las user stories.

- [X] T004 Dominio: `StatementImportId`, `StatementImportStatus` (IN_PROGRESS/COMPLETED/FAILED),
      `StatementLineError` (value object: `rawText`, `reason`, `occurredAt`) en
      `bankstatement/domain/`
- [X] T005 Dominio: agregado `StatementImport` (factory `start(userId, accountId)`,
      `recordTransactionImported()`, `recordLineError(rawText, reason)`, `markCompleted()`,
      `markFailed(reason)`, `reconstitute(...)`) — depende de T004
- [X] T006 Dominio: puertos `StatementImportRepository`, `StatementLineHashRepository` (consultar/
      registrar hash → id de transacción) y excepciones `StatementImportNotFoundException`,
      `InvalidStatementAccountException`, `PdfExtractionException` — depende de T005
- [X] T007 [P] Infraestructura: `StatementImportEntity` (JPA, `StatementLineError` embebido igual
      patrón que `ImportEntity.errors` de la feature 005 — EAGER, mismo motivo),
      `SpringDataStatementImportRepository`, `JpaStatementImportRepository` — depende de T006
- [X] T008 [P] Infraestructura: `StatementImportLineHashEntity`,
      `SpringDataStatementImportLineHashRepository`, `JpaStatementImportLineHashRepository` —
      depende de T006
- [X] T009 Application: puerto `PdfExtractionGateway` (método `extract(byte[] pdfBytes): List<
      ExtractedTransactionDto>` + lista de líneas no interpretadas) + DTOs `ExtractedTransactionDto`
      en `bankstatement/application/dto/` — depende de T002
- [X] T010 Infraestructura: `AnthropicPdfExtractionClient` (implementa `PdfExtractionGateway` con
      `RestClient` contra `api.anthropic.com/v1/messages`, tool-use forzado para JSON garantizado;
      mapea errores de red/`429`/API a `PdfExtractionException`, research.md #1, #4) — depende de T009
- [X] T011 [P] Agregar `TransactionService.createFromImportedTransaction(userId, transactionTypeName,
      amount, date, description, accountId, categoryId): UUID` en
      `transaction/application/TransactionService.java` — método genérico sin los campos propios de
      Wallet, delega en el `createFromExternalImport` existente (plan.md, Complexity Tracking)
- [X] T012 [P] Test fixture: `FakePdfExtractionGateway` (datos fijos configurables por test, sin
      llamar a la API real de Anthropic) — depende de T009
- [X] T013 ArchUnit: agregar `"bankstatement"` a `BOUNDED_CONTEXTS` en `ArchitectureTest.java`

**Checkpoint**: El contexto `bankstatement` compila, tiene sus puertos y su cliente hacia Anthropic
listo para inyectarse; el fixture de test está disponible para las user stories.

---

## Phase 3: User Story 1 - Importar un estado de cuenta en PDF (Priority: P1) 🎯 MVP

**Goal**: El usuario sube un PDF de estado de cuenta indicando la cuenta propia y los movimientos se
crean como transacciones reales de esa cuenta.

**Independent Test**: Con un PDF real de estado de cuenta de una cuenta ya creada, subirlo indicando
esa cuenta y verificar que los movimientos aparecen en "Mis movimientos" con monto, fecha, tipo y
descripción correctos.

### Implementation for User Story 1

- [X] T014 [US1] `StatementImportService.start(userId, accountId, pdfBytes)`: valida que la cuenta
      exista y sea del usuario (`AccountService.existsOwnedByUser`, ya primitivo — FR-002), crea
      `StatementImport` en `IN_PROGRESS`, dispara `StatementImportProcessor.run(...)` de forma async
      (mismo patrón sin `@Transactional` de clase que `ImportService` de la feature 005, por la misma
      razón: el `@Async` no debe arrancar antes de que el registro esté commiteado) — depende de
      Foundational
- [X] T015 [US1] `StatementImportProcessor.run(...)`: llama a `PdfExtractionGateway.extract(...)`,
      para cada movimiento extraído calcula el hash (research.md #3), verifica si ya existe en
      `StatementLineHashRepository` (si existe, lo omite), si no existe lo crea vía
      `TransactionService.createFromImportedTransaction` y registra el hash; cada línea no
      interpretada por el modelo se registra como `StatementLineError` sin interrumpir el resto
      (FR-008); si la llamada al gateway falla entera, marca `StatementImport` como `FAILED` con el
      motivo (research.md #4) — depende de T005, T006, T010, T011
- [X] T016 [US1] `StatementImportController`: `POST /api/statement-imports` (multipart: `file`,
      `accountId`; responde `202` con el `id`) + `StatementImportResponse`/
      `StatementLineErrorResponse` + `StatementImportExceptionHandler` (404 si la cuenta no es del
      usuario, 400 si falta el archivo) — depende de T014, T015
- [X] T017 [P] [US1] Tests unitarios de `StatementImportProcessor` con `FakePdfExtractionGateway`:
      extracción exitosa crea las transacciones esperadas, cuenta no propia rechazada antes de llamar
      al gateway, fallo total del gateway deja el import en `FAILED` con motivo — depende de T015
- [X] T018 [P] [US1] Tests unitarios de `StatementImportService` (start valida cuenta, aislamiento en
      get) — depende de T014
- [X] T019 [US1] Test de integración `StatementImportControllerIT`: sube un PDF (bytes de prueba) con
      `FakePdfExtractionGateway` inyectado, verifica que las transacciones quedan creadas en la cuenta
      indicada, sin categoría — depende de T012, T016

**Checkpoint**: User Story 1 funcional de punta a punta de forma independiente.

---

## Phase 4: User Story 2 - Ver el progreso y el resultado de la importación (Priority: P1)

**Goal**: El usuario puede consultar el estado de una importación de PDF, incluyendo qué líneas no se
pudieron interpretar.

**Independent Test**: Con un PDF de prueba con líneas deliberadamente ambiguas, el resultado final
distingue los movimientos importados de los no interpretados, con un motivo legible para cada uno.

### Implementation for User Story 2

- [X] T020 [US2] `StatementImportController`: `GET /api/statement-imports/{id}` (propio del usuario
      autenticado; `404` si no existe o pertenece a otro usuario, no `403` — mismo patrón ya usado en
      el resto de la API) (FR-007) — depende de T016
- [X] T021 [P] [US2] Tests unitarios de `StatementImportService.get(...)` (aislamiento por usuario) —
      depende de T020
- [X] T022 [US2] Test de integración: un PDF con líneas no interpretadas expone `errors[]` con
      `rawText`/`reason`, y `status` sigue `COMPLETED` (el error puntual no interrumpe el resto) —
      depende de T019, T020

**Checkpoint**: User Stories 1 y 2 funcionan de forma independiente entre sí.

---

## Phase 5: User Story 3 - Subir varios PDFs sin duplicar movimientos (Priority: P2)

**Goal**: Subir el mismo estado de cuenta más de una vez (o dos con fechas solapadas) no duplica
movimientos.

**Independent Test**: Subir el mismo PDF dos veces y verificar que la cantidad de movimientos de la
cuenta no cambia entre la primera y la segunda subida.

### Implementation for User Story 3

- [X] T023 [P] [US3] Test unitario: dos corridas de `StatementImportProcessor` sobre el mismo PDF (o
      dos con un movimiento de hash idéntico) no llaman a `TransactionService.
      createFromImportedTransaction` la segunda vez para ese movimiento — depende de T015
- [X] T024 [US3] Test de integración: subir el mismo PDF dos veces contra `StatementImportControllerIT`
      no duplica movimientos en `GET /api/transactions` (FR-006, SC-003) — depende de T019, T023

**Checkpoint**: Las 3 user stories funcionan de forma independiente entre sí.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T025 Verificar cobertura JaCoCo >80% en `domain`/`application` de `bankstatement`
- [X] T026 [P] Correr manualmente los 5 escenarios de `quickstart.md` contra la API real de Anthropic,
      con un PDF real de estado de cuenta y una `ANTHROPIC_API_KEY` válida. Corrido por el usuario
      contra un estado de cuenta real (49 movimientos, monto/fecha/descripción correctos) — encontró
      y permitió corregir 3 bugs reales: request mal serializado hacia Anthropic (400), tipo
      ingreso/gasto inferido solo de la descripción en vez de la columna del documento, y
      deduplicación huérfana al borrar transacciones manualmente (ver research.md #7)
- [X] T027 Revisar que `StatementLineError.reason` y `StatementImport.failureReason` sean legibles
      para el usuario (sin detalles técnicos de la API de Anthropic), consistente con el resto de la
      app

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias
- **Foundational (Phase 2)**: depende de Setup — bloquea todas las user stories
- **US1 (Phase 3)**: depende solo de Foundational
- **US2 (Phase 4)**: depende de que exista `StatementImportController` (T016 de US1) para agregarle
  el endpoint de consulta
- **US3 (Phase 5)**: depende de que exista `StatementImportProcessor` (T015 de US1) — la
  deduplicación ya se construye ahí, esta fase la cubre con tests dedicados de extremo a extremo
- **Polish (Phase 6)**: depende de todas las user stories que se quieran incluir en esta ronda

### Parallel Opportunities

- T002, T003 (Setup) son paralelos entre sí
- T007, T008, T011, T012 (Foundational) son paralelos entre sí una vez creados los puertos (T006, T009)
- Los tests unitarios marcados [P] (T017, T018, T021, T023) son paralelos a cualquier otro archivo de
  producción que no sea el que están probando

---

## Parallel Example: Foundational (Phase 2)

```bash
# Tras crear los puertos (T006, T009), estos 4 pueden avanzar en paralelo:
Task: "Infraestructura: StatementImportEntity + SpringDataStatementImportRepository + JpaStatementImportRepository"
Task: "Infraestructura: StatementImportLineHashEntity + SpringDataStatementImportLineHashRepository + JpaStatementImportLineHashRepository"
Task: "TransactionService.createFromImportedTransaction(...)"
Task: "Test fixture: FakePdfExtractionGateway"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Completar Phase 1 (Setup) y Phase 2 (Foundational)
2. Completar Phase 3 (US1) y Phase 4 (US2) — ambas P1: sin US2 el usuario no puede confiar en una
   extracción hecha por un LLM sin saber qué falló
3. **Validar** con los escenarios 1 y 2 de `quickstart.md`

### Incremental Delivery

1. Setup + Foundational → base lista (dominio, puertos, cliente hacia Anthropic, fixture de test)
2. US1 → un PDF sube y se convierte en movimientos reales
3. US2 → visibilidad de progreso/errores por línea
4. US3 → subir varios PDFs de forma segura, sin duplicar

---

## Notes

- Sigue el mismo patrón de puertos primitivos cross-context ya establecido en las features 003 y 005
  (ver Complexity Tracking en plan.md) — ningún tipo de dominio de `transaction`/`account` se filtra
  hacia `bankstatement`, y viceversa.
- Por la regla de git del proyecto, la implementación de estas tareas se hace en la rama
  `006-bank-statement-import` (ya creada), con un solo PR al finalizar.
