# Wallet Constitution

## Core Principles

### I. API Contract-First
El backend expone una API REST cuyo contrato (endpoints, request/response DTOs, códigos de error) se
define en la spec/plan de cada feature antes de implementarse. La app Android consume ese contrato
tal cual — ningún endpoint se improvisa durante la implementación sin reflejarse primero en el plan.

### II. Arquitectura en capas
Backend: `Controller → Service → Repository`. Los controllers no contienen lógica de negocio; la
lógica vive en la capa de servicio; el acceso a datos vive solo en repositories (Spring Data JPA).
Android: MVVM — `Composable (UI) → ViewModel → Repository → API (Retrofit)`. Los Composables no
llaman directamente a la red ni contienen lógica de negocio.

### III. Tests sobre lógica de negocio (NON-NEGOTIABLE)
Backend: cada servicio con lógica de negocio lleva tests unitarios (JUnit 5); los endpoints y el
acceso a datos llevan tests de integración con Testcontainers (Postgres real, no mocks de BD).
Android: cada ViewModel con lógica lleva tests unitarios. No se implementa una feature como
"completa" sin sus tests correspondientes.

### IV. Aislamiento de datos por usuario
La app es multi-usuario. Toda entidad de dominio (cuentas, categorías, y las que vengan después)
pertenece a un usuario dueño. Ninguna consulta puede devolver ni modificar datos de un usuario que no
sea el autenticado; esto se aplica en la capa de servicio, no se confía solo en filtros de UI.

### V. Simplicidad y alcance por spec (YAGNI)
Cada feature se implementa según el alcance definido en su propia spec (`specs/<NNN>-<feature>/`).
No se construyen abstracciones ni modelos de datos para funcionalidades futuras (transacciones,
presupuestos, recurrencias, reportes) hasta que tengan su propia spec aprobada. Empezar simple.

## Stack tecnológico

**Backend**: Java 25, Spring Boot 4.1.0, Maven, Spring Web, Spring Data JPA, Spring Security (JWT),
PostgreSQL, Flyway (migraciones), JUnit 5 + Testcontainers.

**Android**: Kotlin, Jetpack Compose (nativo, sin Kotlin Multiplatform por ahora), arquitectura MVVM,
Hilt (DI), Retrofit + OkHttp, Coroutines/Flow.

**Metodología**: Spec-Driven Development con Spec Kit. Todo feature nuevo pasa por
`/speckit-specify` → (`/speckit-clarify` opcional) → `/speckit-plan` → `/speckit-tasks` →
`/speckit-implement`. No se escribe código de negocio sin una spec y un plan aprobados primero.

## Flujo de desarrollo

- Cada feature vive en `specs/<NNN>-<nombre>/` con su `spec.md`, `plan.md` y `tasks.md`.
- El primer feature es autenticación (registro/login, JWT) por ser prerrequisito de todo lo demás.
- El backend y el Android app son proyectos independientes dentro del mismo repo (`backend/`,
  `android/`), acoplados solo por el contrato de API — pueden evolucionar y desplegarse por separado.

## Governance

Esta constitución tiene prioridad sobre decisiones ad-hoc durante la implementación. Cualquier
excepción a un principio debe justificarse explícitamente en el `plan.md` del feature correspondiente.
Enmiendas a este documento requieren bump de versión y quedan documentadas en el historial de git.

**Version**: 1.0.0 | **Ratified**: 2026-07-17 | **Last Amended**: 2026-07-17
