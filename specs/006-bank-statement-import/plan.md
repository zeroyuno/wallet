# Implementation Plan: Importar movimientos desde estados de cuenta bancarios en PDF

**Branch**: `006-bank-statement-import` | **Date**: 2026-07-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-bank-statement-import/spec.md`

## Summary

Nuevo bounded context `bankstatement`: el usuario sube un PDF de estado de cuenta indicando a qué
cuenta propia pertenece; el PDF se envía directamente (como documento, sin extracción de texto
propia) a la API de Claude (Anthropic) pidiendo una extracción estructurada de movimientos vía
tool-use forzado (JSON garantizado, no parseo de texto libre). Cada movimiento extraído se crea como
transacción real sin categoría, deduplicando por hash de (cuenta + fecha + monto + descripción). Se
procesa en `@Async`, igual patrón que la importación de Wallet (feature 005): `POST` responde `202`
de inmediato, `GET` consulta el progreso y los errores por línea.

## Technical Context

**Language/Version**: Java 25 (ya establecido en el proyecto)

**Primary Dependencies**: Spring Web (multipart file upload), Spring `RestClient` (llamadas a la API
de Anthropic — mismo patrón que `WalletApiHttpClient` de la feature 005, sin librería nueva de
cliente HTTP). No se agrega una librería de extracción de PDF (ej. PDFBox): el PDF se manda tal cual
(bytes, base64) a Claude, que soporta documentos PDF nativamente — evita mantener un extractor de
texto propio y es más robusto a layouts variados entre bancos (la razón original para elegir LLM).

**Storage**: PostgreSQL (Flyway) — tablas nuevas `statement_imports`, `statement_import_errors`,
`statement_import_line_hashes` (idempotencia, ver data-model.md).

**Testing**: JUnit 5 + Mockito (unit), Testcontainers + Postgres real (integración) — mismo stack ya
usado en el resto del backend. La llamada real a la API de Anthropic no se testea automáticamente
(análogo a `WalletApiHttpClient`): se usa un fake (`FakePdfExtractionGateway`) en los tests, y una
validación manual con un PDF real queda documentada en quickstart.md.

**Target Platform**: Mismo backend Spring Boot (monolito modular) ya desplegado.

**Project Type**: Web service — extensión del backend existente, sin cambios en Android esta ronda
(ver Assumptions de spec.md).

**Performance Goals**: Un PDF de estado de cuenta típico (hasta unas pocas decenas de movimientos) se
procesa en menos de 2 minutos (SC-001) — una sola llamada a Claude por PDF es suficiente para ese
volumen, sin necesidad de paginar ni trocear el documento.

**Constraints**: Tamaño máximo de archivo subido: 20 MB (cubre estados de cuenta de varias páginas
con margen; ver `spring.servlet.multipart.max-file-size`). Requiere una `ANTHROPIC_API_KEY` propia
configurada como variable de entorno (mismo patrón que `JWT_SECRET`/`DB_PASSWORD`), sin la cual el
endpoint de subida falla con un error claro en vez de un fallo silencioso.

**Scale/Scope**: Uso personal, mismo orden de magnitud que el resto de la app — no se optimiza para
múltiples usuarios subiendo en paralelo ni para volúmenes masivos de PDFs.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. API Contract-First**: PASS — contrato definido en `contracts/statement-imports-api.yaml` antes
  de implementar.
- **II. Arquitectura hexagonal y DDD**: PASS — nuevo bounded context `bankstatement`
  (`domain/application/infrastructure`), sin acceso directo a `domain`/`infrastructure` de
  `transaction`/`account`. Reutiliza `AccountService.existsOwnedByUser` (ya primitivo) y agrega un
  método de escritura genérico en `TransactionService` (ver Complexity Tracking) — mismo patrón ya
  establecido en la feature 005.
- **III. Tests y cobertura >80%**: PASS — mismo enfoque que `walletimport`: unitarios de
  domain/application con mocks/fake, integración con Testcontainers para los endpoints HTTP.
- **IV. Aislamiento de datos por usuario**: PASS — el `accountId` se valida contra el usuario
  autenticado antes de procesar cualquier PDF (FR-002); `GET` de un `StatementImport` ajeno responde
  `404`, mismo criterio que el resto de la API.
- **V. Simplicidad y alcance por spec (YAGNI)**: PASS — sin revisión/aprobación previa (FR-011), sin
  categorización automática (FR-012), sin extracción de texto propia — decisiones ya tomadas en
  spec.md para mantener el alcance mínimo viable.

## Project Structure

### Documentation (this feature)

```text
specs/006-bank-statement-import/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/walletapp/backend/
├── bankstatement/                              # NUEVO bounded context
│   ├── domain/
│   │   ├── StatementImportId.java
│   │   ├── StatementImportStatus.java          # IN_PROGRESS/COMPLETED/FAILED
│   │   ├── StatementLineError.java             # value object
│   │   ├── StatementImport.java                # aggregate
│   │   ├── StatementImportRepository.java      # puerto
│   │   ├── StatementLineHashRepository.java     # puerto (idempotencia)
│   │   └── exception/
│   │       ├── StatementImportNotFoundException.java
│   │       └── InvalidStatementAccountException.java
│   ├── application/
│   │   ├── StatementImportService.java          # start()/get(), sin @Transactional de clase
│   │   ├── StatementImportProcessor.java         # @Async, orquesta la extracción + creación
│   │   ├── PdfExtractionGateway.java             # puerto hacia el LLM
│   │   └── dto/
│   │       ├── ExtractedTransactionDto.java
│   │       ├── StatementImportView.java
│   │       └── StatementLineErrorView.java
│   └── infrastructure/
│       ├── persistence/                          # JPA: entities + Spring Data + adaptadores
│       ├── llmclient/
│       │   └── AnthropicPdfExtractionClient.java  # RestClient hacia api.anthropic.com
│       └── web/
│           ├── StatementImportController.java     # POST (multipart) / GET
│           ├── StatementImportExceptionHandler.java
│           └── dto/
│               ├── StatementImportResponse.java
│               └── StatementLineErrorResponse.java
└── transaction/application/TransactionService.java  # +1 método genérico (ver Complexity Tracking)

backend/src/main/resources/db/migration/
└── V7__create_statement_imports.sql

backend/src/test/java/com/walletapp/backend/bankstatement/
├── application/
│   ├── FakePdfExtractionGateway.java
│   ├── StatementImportProcessorTest.java
│   └── StatementImportServiceTest.java
└── infrastructure/web/StatementImportControllerIT.java
```

**Structure Decision**: Nuevo bounded context `bankstatement` dentro del monolito backend existente,
mismo layout hexagonal que `walletimport` (feature 005). Sin cambios en `android/` esta ronda.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| Nuevo método `TransactionService.createFromImportedTransaction(...)` (7 params, sin los campos propios de Wallet) además del `createFromExternalImport(...)` de la feature 005 | `bankstatement` necesita crear transacciones vía el mismo mecanismo de escritura primitivo cross-context, pero sin campos que no le pertenecen semánticamente (`walletTransferId`, `paymentType`, etc. son de Wallet) | Reusar el método de 12 parámetros pasando `null` en los campos de Wallet — rechazado: filtra un nombre de campo (`walletTransferId`) ajeno a la fuente real de los datos en cada call site, confuso de leer/mantener. El nuevo método delega en el existente (mismo helper interno), así que no duplica la lógica de validación ni de creación, solo agrega una fachada con nombres correctos. |
| Deduplicación por hash (cuenta+fecha+monto+descripción) en vez de un id externo real | No existe un id de movimiento estable en un PDF (a diferencia del `id` que sí da la API de Wallet) | Ya evaluado y decidido en spec.md (Pregunta 1 de `/speckit-specify`) — es la única opción viable sin id externo real; la limitación (colisión entre movimientos legítimos idénticos) queda documentada como aceptada. |
