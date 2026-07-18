# Implementation Plan: Transacciones (ingresos y gastos)

**Branch**: `003-transactions` | **Date**: 2026-07-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-transactions/spec.md`

## Summary

Backend únicamente (ver Assumptions en spec.md): API REST para registrar, listar/filtrar, editar y
eliminar transacciones (ingresos/gastos) asociadas a una cuenta propia y, opcionalmente, a una
categoría propia del mismo tipo. Se implementa como un nuevo bounded context `transaction`
(arquitectura hexagonal), que **depende de** `account` (vía su capa `application`, el único punto de
comunicación entre contextos permitido por la constitución) para validar cuentas/categorías — nunca al
revés. El saldo "actual" de una cuenta se calcula bajo demanda combinando el `initialBalance` de
`account` con la suma de transacciones, sin mutar ni duplicar ese estado en la tabla `accounts`.

## Technical Context

**Language/Version**: Backend: Java 25 (Spring Boot 4.1.0). Sin cambios en Android esta ronda.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security (reutiliza `JwtAuthFilter`/
`SecurityConfig` existentes), Flyway, `com.github.f4b6a3:uuid-creator` (generación de UUID v7 para
`TransactionId` — `java.util.UUID` no genera v7 nativamente, ver research.md). Depende en tiempo de
compilación de `account.application.{AccountService,CategoryService}` (nuevos métodos de solo
lectura, ver Complexity Tracking).

**Storage**: PostgreSQL — nueva tabla `transactions` con FK `ON DELETE RESTRICT` hacia `accounts.id`
y `categories.id` (ver data-model.md).

**Testing**: JUnit 5 (unitarios de `TransactionService`, mockeando `AccountService`/`CategoryService`
como colaboradores) + Testcontainers Postgres (integración de `TransactionController`, con foco en
aislamiento entre usuarios y en el efecto sobre el saldo) + ArchUnit (se agrega `transaction` a la
lista de bounded contexts ya generalizada en la feature 002).

**Target Platform**: Igual que las features anteriores — backend JVM. Sin target Android en esta
ronda (ver Assumptions en spec.md).

**Project Type**: web-service (monolito modular) — sin componente mobile en esta ronda.

**Performance Goals**: Sin metas de throughput agresivas (misma escala que features anteriores).

**Constraints**: Toda consulta/mutación DEBE filtrar por el usuario autenticado (principio IV). El
contexto `account` NUNCA debe depender de `transaction` (principio II) — el bloqueo de "no eliminar
cuenta/categoría con transacciones" (FR-010) se resuelve con una restricción `ON DELETE RESTRICT` a
nivel de base de datos más un manejador de excepción genérico, sin que `account` tenga que conocer la
existencia de `transaction` (ver Complexity Tracking).

**Scale/Scope**: Misma escala pequeña que features anteriores.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. API Contract-First**: PASS — endpoints definidos en `contracts/transactions-api.yaml` antes de
  implementar.
- **II. Arquitectura hexagonal y DDD**: PASS con una decisión explícita documentada — `transaction`
  depende de `account.application` (capa application, canal de comunicación explícitamente permitido
  por la constitución); `account` no depende de `transaction` en ningún sentido. Ver Complexity
  Tracking para el detalle de cómo se evita que esa dependencia arrastre transitivamente tipos de
  `account.domain` (lo que sí violaría la regla ArchUnit vigente de privacidad de `domain` por
  contexto).
- **III. Tests y cobertura >80%**: PASS — `TransactionService` con tests unitarios (incluye
  validación de cuenta/categoría ajena y de tipo no coincidente); `TransactionController` con tests de
  integración (Testcontainers), con foco en aislamiento entre usuarios y en que el saldo calculado
  sea correcto tras crear/editar/eliminar (FR-002, FR-005, FR-006, SC-002).
- **IV. Aislamiento de datos por usuario**: PASS — mismo patrón ya probado (`findByIdAndUserId` /
  `AuthenticatedUser` vía `JwtAuthFilter`), aplicado también a los nuevos métodos de solo lectura que
  `account.application` expone para `transaction`.
- **V. Simplicidad y alcance por spec (YAGNI)**: PASS — sin paginación (Assumptions), sin
  conversión de moneda (Assumptions), sin UI Android esta ronda (Assumptions). El saldo se calcula
  bajo demanda (suma en la propia consulta) en vez de mantener un campo de saldo desnormalizado en
  `accounts` — evita el riesgo de que ese campo se desincronice de la suma real de transacciones, que
  es justamente lo que pide garantizar SC-002.

No hay violaciones que requieran justificación adicional más allá de lo ya explicado arriba y en
Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/003-transactions/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── transactions-api.yaml
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/walletapp/backend/transaction/
├── domain/
│   ├── Transaction.java               # aggregate root
│   ├── TransactionId.java             # value object
│   ├── TransactionType.java           # enum: INCOME, EXPENSE (propio de este contexto, ver research.md)
│   ├── TransactionRepository.java     # puerto de salida
│   └── exception/ (TransactionNotFoundException, InvalidTransactionAccountException,
│                    InvalidTransactionCategoryException, CategoryTypeMismatchException)
├── application/
│   ├── TransactionService.java        # create/list(+filtros)/update/delete/getAccountBalance
│   └── dto/ (TransactionCommand, TransactionUpdateCommand, TransactionView, TransactionFilter)
└── infrastructure/
    ├── persistence/
    │   ├── TransactionEntity.java                # entidad JPA (no el dominio)
    │   ├── SpringDataTransactionRepository.java   # incluye la query de suma neta por cuenta
    │   └── JpaTransactionRepository.java          # implementa el puerto del dominio
    └── web/
        ├── TransactionController.java   # POST/GET/PUT/DELETE /api/transactions[...]
        ├── BalanceController.java       # GET /api/accounts/{id}/balance (ver nota abajo)
        ├── dto/ (TransactionRequest, TransactionUpdateRequest, TransactionResponse, BalanceResponse)
        └── TransactionExceptionHandler.java

backend/src/main/resources/db/migration/
└── V3__create_transactions.sql

backend/src/test/java/com/walletapp/backend/
├── ArchitectureTest.java              # se agrega "transaction" a BOUNDED_CONTEXTS
├── account/application/
│   ├── AccountServiceTest.java        # + tests de los nuevos métodos de solo lectura
│   └── CategoryServiceTest.java       # + tests de los nuevos métodos de solo lectura
└── transaction/
    ├── application/TransactionServiceTest.java
    └── infrastructure/web/TransactionControllerIT.java
```

**Nota sobre `BalanceController`**: el endpoint `GET /api/accounts/{id}/balance` vive bajo el mismo
prefijo de URL que `AccountController` (`/api/accounts`) por ser el path más natural para un cliente,
pero está implementado en `transaction.infrastructure.web` — el saldo es un concepto que depende de
las transacciones, y `account` no puede depender de `transaction` (principio II). `AccountController`
existente no se modifica.

**Structure Decision**: Nuevo contexto `transaction` (backend) siguiendo el mismo patrón hexagonal que
`auth`/`account`. `transaction.application` depende de `account.application` (permitido); ningún
código de `account` referencia `transaction`. Sin cambios en `android/` esta ronda.

## Complexity Tracking

| Decisión | Por qué es necesaria | Alternativa más simple descartada porque |
|-----------|------------|---------------------------------------|
| `transaction.application.TransactionService` llama a dos métodos nuevos de solo lectura en `AccountService`/`CategoryService` (`existsOwnedByUser`, `getInitialBalanceIfOwnedByUser`, `findTypeIfOwnedByUser`) que devuelven únicamente tipos primitivos (`boolean`, `UUID`, `BigDecimal`, `String`), nunca `AccountView`/`CategoryView` | `AccountView.type()`/`CategoryView.type()` devuelven los enums `AccountType`/`CategoryType` de `account.domain` — si `TransactionService` los consumiera directamente, arrastraría una dependencia real de bytecode hacia `account.domain`, violando la regla ArchUnit vigente `domain_is_private_to_its_own_context_except_shared` (ver `ArchitectureTest.java`) | Reutilizar `AccountView`/`CategoryView` tal cual — rechazado porque exponen tipos de dominio de `account` fuera de su contexto, exactamente lo que esa regla existe para impedir; no se relaja la regla porque sigue siendo válida y útil para el resto del código |
| `transaction.domain.TransactionType` (INCOME/EXPENSE) es un enum propio, duplicado en valores respecto de `account.domain.CategoryType` | Cada contexto modela su propio lenguaje ubicuo — mismo criterio ya aplicado en la feature 002 para `userId` (`UUID` plano en vez del `UserId` de `auth`) | Reutilizar `account.domain.CategoryType` desde `transaction` — rechazado por la misma regla ArchUnit y porque acoplaría el tipo de una transacción (que puede no tener categoría) a un concepto que pertenece a `account` |
| FR-010 (no eliminar cuenta/categoría con transacciones) se resuelve con `ON DELETE RESTRICT` en las FK de `transactions` + un `@ExceptionHandler(DataIntegrityViolationException.class)` genérico en `AccountExceptionHandler` (existente) | Es la única forma de bloquear la eliminación sin que `account` tenga que preguntarle a `transaction` si existen filas asociadas — evita cualquier dependencia de `account` hacia `transaction` | Un puerto de dominio tipo `TransactionLookupPort` implementado por `transaction` e inyectado en `AccountService`/`CategoryService` — rechazado: aunque técnicamente viable con inversión de dependencias, agrega una interfaz nueva y una capa de indirección solo para replicar lo que la base de datos ya garantiza de forma más simple; además ya se validó en la feature 002 que el patrón "restricción de BD + manejador de excepción específico" funciona bien (fue la corrección real que se hizo ahí para `categories.parent_category_id`) |
