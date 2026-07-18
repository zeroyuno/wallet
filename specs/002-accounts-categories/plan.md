# Implementation Plan: Gestión de cuentas y categorías

**Branch**: `002-accounts-categories` | **Date**: 2026-07-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-accounts-categories/spec.md`

## Summary

CRUD de cuentas (efectivo, banco, tarjeta) y categorías de ingreso/gasto (con subcategorías
opcionales) para un usuario ya autenticado por la feature 001. Se implementa como un nuevo bounded
context `account` (arquitectura hexagonal: `domain → application → infrastructure`), reutilizando el
mecanismo de identidad (`AuthenticatedUser` de `shared.security`) que estableció la feature 001. Todo
dato se filtra estrictamente por el usuario dueño.

## Technical Context

**Language/Version**: Backend: Java 25 (Spring Boot 4.1.0). Android: Kotlin 2.2.21 (Jetpack Compose,
AGP 9.3.0).

**Primary Dependencies**: Backend: Spring Web, Spring Data JPA, Spring Security (reutiliza
`JwtAuthFilter`/`SecurityConfig` ya existentes), Flyway. Android: Retrofit + OkHttp, Hilt, kotlinx
Coroutines/Flow.

**Storage**: PostgreSQL — tablas `accounts` y `categories` (ver data-model.md), ambas con FK a
`users` (creada por la feature 001), propiedad exclusiva del nuevo contexto `account`.

**Testing**: Backend: JUnit 5 (unitarios en `AccountService`/`CategoryService`, sin Spring ni DB) +
Testcontainers Postgres (integración de los controllers, con foco especial en el aislamiento entre
usuarios) + ArchUnit (mismas reglas de capas, generalizadas para dos bounded contexts). Android: unit
tests de los ViewModels correspondientes.

**Target Platform**: Igual que la feature 001 — backend JVM, Android minSdk 26+.

**Project Type**: web-service (monolito modular) + mobile-app.

**Performance Goals**: Sin metas de throughput agresivas (misma escala que la feature 001).

**Constraints**: Toda consulta/mutación DEBE filtrar por el usuario autenticado (constitución,
principio IV) — una cuenta o categoría de otro usuario responde 404, nunca 403 (FR-005). `domain`/
`application` sin dependencias de framework y >80% de cobertura (principios II y III).

**Scale/Scope**: Mismo alcance de escala pequeña que la feature 001.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. API Contract-First**: PASS — endpoints de cuentas y categorías definidos en
  `contracts/accounts-categories-api.yaml` antes de implementar.
- **II. Arquitectura hexagonal y DDD**: PASS — nuevo contexto `account/{domain,application,
  infrastructure}`, sin dependencias cruzadas con `auth` salvo `shared.security` (mismo patrón que la
  feature 001). Se generaliza el test ArchUnit (antes con reglas hardcodeadas solo para `auth`) para
  cubrir cualquier bounded context — ver Complexity Tracking.
- **III. Tests y cobertura >80%**: PASS — `AccountService`/`CategoryService` con tests unitarios;
  adaptadores con tests de integración; el caso más crítico a testear es el aislamiento entre usuarios
  (FR-005).
- **IV. Aislamiento de datos por usuario**: PASS — es el requisito central de esta feature; reutiliza
  el `sub` del JWT vía `AuthenticatedUser` ya resuelto por `JwtAuthFilter`.
- **V. Simplicidad y alcance por spec (YAGNI)**: PASS — sin transacciones, presupuestos ni reportes
  (ver Assumptions en spec.md). `Account` y `Category` se agrupan en un solo bounded context `account`
  en vez de dos separados: comparten el mismo alcance/spec y no hay lógica de negocio cruzada que
  justifique la separación todavía — se puede dividir después si un contexto crece lo suficiente.
  Por el mismo principio, se usa un `AccountService`/`CategoryService` con varios métodos (create/
  list/update/delete) en vez de una clase de caso de uso por operación: a diferencia de auth (donde
  register/login/logout tenían lógica de negocio sustancialmente distinta), acá las cuatro operaciones
  son CRUD simple sobre el mismo agregado y comparten dependencias.

No hay violaciones que requieran justificación adicional más allá de lo ya explicado arriba.

## Project Structure

### Documentation (this feature)

```text
specs/002-accounts-categories/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── accounts-categories-api.yaml
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/walletapp/backend/account/
├── domain/
│   ├── Account.java                  # aggregate root
│   ├── AccountId.java                # value object
│   ├── AccountType.java              # enum: CASH, BANK, CREDIT_CARD, OTHER
│   ├── CurrencyCode.java             # value object (código ISO 4217, ej. USD)
│   ├── AccountRepository.java        # puerto de salida
│   ├── Category.java                 # aggregate root
│   ├── CategoryId.java               # value object
│   ├── CategoryType.java             # enum: INCOME, EXPENSE
│   ├── CategoryRepository.java       # puerto de salida
│   └── exception/ (AccountNotFoundException, CategoryNotFoundException,
│                    DuplicateCategoryException, InvalidCategoryHierarchyException)
├── application/
│   ├── AccountService.java           # create/list/update/delete
│   ├── CategoryService.java          # create/list/update/delete (incluye validación de jerarquía)
│   └── dto/ (AccountCommand, AccountView, CategoryCommand, CategoryView)
└── infrastructure/
    ├── persistence/
    │   ├── AccountEntity.java, CategoryEntity.java   # entidades JPA (no el dominio)
    │   ├── SpringDataAccountRepository.java, SpringDataCategoryRepository.java
    │   └── JpaAccountRepository.java, JpaCategoryRepository.java  # implementan los puertos
    └── web/
        ├── AccountController.java, CategoryController.java
        ├── dto/ (AccountRequest, AccountResponse, CategoryRequest, CategoryResponse)
        └── AccountExceptionHandler.java

backend/src/main/resources/db/migration/
└── V2__create_accounts_and_categories.sql

backend/src/test/java/com/walletapp/backend/
├── ArchitectureTest.java             # generalizado a N bounded contexts (T0xx)
└── account/
    ├── application/ (AccountServiceTest.java, CategoryServiceTest.java)
    └── infrastructure/web/ (AccountControllerIT.java, CategoryControllerIT.java)

android/app/src/main/java/com/walletapp/android/
├── accounts/
│   ├── ui/ (AccountListScreen.kt, AccountFormScreen.kt)
│   ├── AccountViewModel.kt, AccountRepository.kt, AccountApi.kt
└── categories/
    ├── ui/ (CategoryListScreen.kt, CategoryFormScreen.kt)
    └── CategoryViewModel.kt, CategoryRepository.kt, CategoryApi.kt
```

**Structure Decision**: Nuevo contexto `account` (backend) siguiendo el mismo patrón hexagonal que
`auth`; nuevos paquetes `accounts`/`categories` en Android siguiendo el mismo patrón MVVM que `auth`.
No se crea ningún proyecto/módulo nuevo fuera de lo ya existente.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| `Account` y `Category` en un solo bounded context `account` en vez de dos | Ambas entidades nacen de la misma spec/alcance, sin lógica de negocio compartida entre sí pero tampoco con necesidad de aislarse una de otra todavía | Dos contextos separados (`account` y `category`) — rechazado por ahora como separación prematura (YAGNI); se puede dividir después si uno de los dos crece mucho más que el otro |
