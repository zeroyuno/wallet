# Implementation Plan: Importar datos desde BudgetBakers Wallet

**Branch**: `005-budgetbakers-import` | **Date**: 2026-07-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-budgetbakers-import/spec.md`

## Summary

Backend únicamente esta ronda (ver Assumptions en spec.md — mismo criterio ya aplicado en la feature
003 para transacciones): un nuevo bounded context `walletimport` orquesta la importación, llamando a
la API REST externa de BudgetBakers Wallet (OpenAPI 1.4.0) con el token que el usuario provee en cada
corrida (nunca persistido), y creando cuentas/categorías/movimientos propios a través de las
capacidades ya existentes de los contextos `account` y `transaction` (capa `application`, el canal de
comunicación entre contextos permitido por la constitución). La importación corre en segundo plano
(no bloquea el request HTTP que la inicia) porque puede tardar e interrumpirse por el límite de uso de
la API externa (500 requests/hora); el progreso y resultado quedan persistidos y consultables.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.0), sin cambios respecto de las features anteriores.

**Primary Dependencies**: Spring Web (cliente HTTP saliente vía `RestClient` para consumir la API de
Wallet), Spring Data JPA, `@EnableAsync`/`@Async` de Spring para correr la importación en segundo plano
sin sumar un sistema de colas/jobs nuevo (ver Complexity Tracking). Depende en tiempo de compilación de
`account.application.{AccountService,CategoryService}` y `transaction.application.TransactionService`
(nuevos métodos de creación primitivos, ver Complexity Tracking).

**Storage**: PostgreSQL — nuevas tablas `imports` (estado y progreso de cada corrida),
`import_errors` (detalle de lo que no se pudo importar) e `import_external_refs` (mapeo
id-de-Wallet → id-propio, para la idempotencia exigida por FR-006).

**Testing**: JUnit 5 (`ImportService` con tests unitarios, mockeando `AccountService`/
`CategoryService`/`TransactionService` como colaboradores y el gateway de Wallet como puerto) +
Testcontainers Postgres (integración de `ImportController`, con foco en aislamiento por usuario y en
que una segunda corrida sobre los mismos datos no duplique nada) + ArchUnit (se agrega `walletimport`
a la lista de bounded contexts). El cliente HTTP real hacia Wallet se verifica manualmente contra la
API real (`quickstart.md`) — no se agrega una librería de mocking HTTP (ej. WireMock) nueva solo para
esto; la lógica de mapeo de la respuesta se cubre con tests unitarios sobre datos de ejemplo fijos.

**Target Platform**: Igual que las features anteriores — backend JVM. Sin target Android en esta ronda
(ver Assumptions en spec.md), consistente con el mismo criterio de la feature 003.

**Project Type**: web-service (monolito modular) — sin componente mobile en esta ronda.

**Performance Goals**: Ninguna meta de throughput propia; respetar el límite de 500 requests/hora de la
API de Wallet es una restricción externa, no un objetivo de performance nuestro.

**Constraints**: El token de Wallet NUNCA se persiste — vive solo en memoria durante la corrida en
curso (principio de no guardar credenciales de terceros en reposo, ver Assumptions de spec.md). Toda
consulta/mutación DEBE filtrar por el usuario autenticado (principio IV). La importación debe poder
interrumpirse por el rate limit externo y continuar después sin perder progreso ni duplicar datos
(FR-006, FR-008).

**Scale/Scope**: Historiales de hasta ~20,000 movimientos por usuario (límite máximo documentado por la
propia API de Wallet) — puede requerir varias corridas dado el rate limit externo.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. API Contract-First**: PASS — endpoints propios (`POST /api/imports`, `GET /api/imports/{id}`)
  definidos en `contracts/imports-api.yaml` antes de implementar. El contrato de la API externa de
  Wallet que consumimos no lo controlamos nosotros — se referencia (no se duplica) el OpenAPI oficial
  ya publicado por BudgetBakers, documentando en `research.md` exactamente qué endpoints/campos de esa
  API se usan.
- **II. Arquitectura hexagonal y DDD**: PASS con una decisión explícita documentada — `walletimport`
  depende de `account.application` y `transaction.application` (capa `application`, canal permitido),
  nunca al revés. Igual que en la feature 003, esa dependencia no puede arrastrar tipos de dominio
  ajenos (ver Complexity Tracking para los nuevos métodos primitivos de creación).
- **III. Tests y cobertura >80%**: PASS — `ImportService` con tests unitarios (incluye el flujo de
  idempotencia y el de interrupción por rate limit); `ImportController` con tests de integración
  (Testcontainers), con foco en aislamiento por usuario y en que una segunda corrida no duplique nada
  (FR-006, SC-003).
- **IV. Aislamiento de datos por usuario**: PASS — toda importación se asocia al `userId` del token
  JWT autenticado (nunca a un identificador provisto por el cliente), mismo patrón ya probado.
- **V. Simplicidad y alcance por spec (YAGNI)**: PASS — sin UI Android esta ronda (Assumptions), sin
  presupuestos/órdenes recurrentes/metas/labels/fotos/ubicación de Wallet (Assumptions), sin sistema de
  colas/jobs nuevo (se usa `@Async` de Spring, ya disponible en el framework), sin persistir el token
  de Wallet.

No hay violaciones que requieran justificación adicional más allá de lo ya explicado arriba y en
Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/005-budgetbakers-import/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── imports-api.yaml
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/walletapp/backend/walletimport/
├── domain/
│   ├── Import.java                       # aggregate root: estado, contadores, cursor de reanudación
│   ├── ImportId.java                     # value object
│   ├── ImportStatus.java                 # enum: IN_PROGRESS, COMPLETED, PAUSED_RATE_LIMIT, FAILED
│   ├── ImportError.java                  # value object: entityType, externalId, reason
│   ├── ExternalEntityType.java           # enum: ACCOUNT, CATEGORY, TRANSACTION
│   ├── ImportRepository.java             # puerto de salida
│   ├── ExternalReferenceRepository.java  # puerto — registra/consulta mapeos id-Wallet -> id-propio
│   └── exception/ (ImportNotFoundException, InvalidWalletTokenException)
├── application/
│   ├── ImportService.java                # start()/get(); orquesta el flujo completo (ver research.md)
│   ├── WalletImportGateway.java          # puerto de salida — abstrae la API externa de Wallet
│   └── dto/ (WalletAccountDto, WalletCategoryDto, WalletRecordDto, ImportView)
└── infrastructure/
    ├── persistence/ (ImportEntity, ExternalReferenceEntity, Spring Data repos, adaptadores JPA)
    ├── client/
    │   └── WalletApiHttpClient.java      # implementa WalletImportGateway con Spring RestClient
    └── web/
        ├── ImportController.java         # POST /api/imports, GET /api/imports/{id}
        ├── dto/ (StartImportRequest, ImportResponse)
        └── ImportExceptionHandler.java

backend/src/main/resources/db/migration/
└── V5__create_imports.sql

backend/src/test/java/com/walletapp/backend/
├── ArchitectureTest.java                 # se agrega "walletimport" a BOUNDED_CONTEXTS
├── account/application/
│   ├── AccountServiceTest.java           # + tests del nuevo método de creación primitivo
│   └── CategoryServiceTest.java          # + tests del nuevo método de creación primitivo
├── transaction/application/
│   └── TransactionServiceTest.java       # + tests del nuevo método de creación primitivo
└── walletimport/
    ├── application/ImportServiceTest.java
    └── infrastructure/web/ImportControllerIT.java
```

**Structure Decision**: Nuevo contexto `walletimport` (backend) siguiendo el mismo patrón hexagonal que
los contextos existentes. Depende de `account.application` y `transaction.application` (permitido);
ningún código de esos contextos referencia `walletimport`. Sin cambios en `android/` esta ronda.

## Complexity Tracking

| Decisión | Por qué es necesaria | Alternativa más simple descartada porque |
|-----------|------------|---------------------------------------|
| `AccountService`, `CategoryService` y `TransactionService` ganan un método de creación nuevo, específico para llamadores cross-context, que recibe tipos primitivos (`String` para el tipo de cuenta/categoría en vez de `AccountType`/`CategoryType`) y devuelve solo el `UUID` creado | Los métodos `create(...)` existentes exigen como parámetro el enum de dominio propio del contexto (`AccountType`, `CategoryType`) — construir ese valor desde `walletimport` ya arrastraría una dependencia real de bytecode hacia `account.domain`, violando la misma regla ArchUnit que motivó el patrón de métodos primitivos en la feature 003 (ahí solo para lecturas; acá se extiende a creación) | Reutilizar los métodos `create(...)` existentes tal cual — rechazado por la razón de arriba; agregar un puerto de dominio con inversión de dependencia — rechazado por ser más indirección de la que este caso necesita, cuando ya existe el patrón más simple (método primitivo dedicado) validado en la feature 003 |
| La importación corre con `@Async` de Spring (un hilo del pool por corrida) en vez de un sistema de colas/jobs dedicado (ej. Quartz, Spring Batch) | Se necesita que `POST /api/imports` responda de inmediato y el trabajo real siga en segundo plano, pero la escala esperada (una corrida por usuario a la vez, acotada además por el rate limit externo) no justifica la complejidad operativa de un scheduler dedicado | Spring Batch — rechazado por YAGNI: resuelve problemas de checkpointing/particionado a una escala que esta feature no tiene; el propio `cursor` guardado en la entidad `Import` ya resuelve la reanudación sin necesitar ese framework |
| La idempotencia (FR-006) se resuelve con una tabla propia de `walletimport` (`import_external_refs`: id-Wallet → id-propio) en vez de agregar una columna de referencia externa directamente en `accounts`/`categories`/`transactions` | Mantiene a los contextos `account`/`transaction` libres de un concepto que solo le importa a la integración con Wallet — cada contexto modela su propio lenguaje ubicuo, mismo criterio ya aplicado repetidas veces en features anteriores | Agregar una columna `external_ref` a las tablas existentes — rechazado porque acopla el modelo de dominio central de la app a una integración externa específica que podría no ser la única en el futuro (ej. importar de otra app similar) |
