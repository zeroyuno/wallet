# Wallet Constitution

## Core Principles

### I. API Contract-First
El backend expone una API REST cuyo contrato (endpoints, request/response DTOs, códigos de error) se
define en la spec/plan de cada feature antes de implementarse. La app Android consume ese contrato
tal cual — ningún endpoint se improvisa durante la implementación sin reflejarse primero en el plan.

### II. Arquitectura hexagonal y DDD (monolito modular)
El backend es un único desplegable Spring Boot organizado como **monolito modular**: dividido en
módulos por *bounded context* (ej. `auth`, `accounts`, `categories`, y los que vengan después). Cada
módulo sigue arquitectura hexagonal (ports & adapters):

- `domain/`: entidades, value objects, agregados y reglas de negocio puras, más las interfaces de
  repositorio (puertos de salida). Sin dependencias de Spring, JPA ni de ningún framework.
- `application/`: casos de uso (application services) que orquestan el dominio; los puertos de
  entrada que consume la infraestructura.
- `infrastructure/`: adaptadores — controllers REST (adaptador de entrada), repositorios JPA
  (adaptador de salida), y cualquier cliente externo.

Un módulo/bounded context NUNCA importa clases de `domain` o `infrastructure` de otro módulo
directamente; la comunicación entre contextos pasa por la capa `application` (casos de uso expuestos)
o eventos de dominio. Esta regla se verifica con tests de arquitectura (ArchUnit), no solo por
convención.

Este diseño existe para poder extraerse a microservicios reales en el futuro si el proyecto lo
justifica, cambiando solo los adaptadores de infraestructura — sin reescribir el dominio. Mientras
tanto, se despliega como un único servicio (evita la complejidad operativa de microservicios que el
alcance actual no justifica).

Android: mantiene MVVM sin cambios — `Composable (UI) → ViewModel → Repository → API (Retrofit)`. Es
una capa de presentación consumidora de la API; DDD/hexagonal aplica solo al backend.

### III. Tests sobre lógica de negocio y cobertura mínima (NON-NEGOTIABLE)
Backend: `domain` y `application` de cada módulo DEBEN tener cobertura de línea **mayor al 80%**
(medida con JaCoCo, verificada en el build — un build que baje del umbral falla). Los adaptadores de
`infrastructure` (controllers, repositorios JPA) se cubren con tests de integración con Testcontainers
(Postgres real, no mocks de BD). Tests de arquitectura (ArchUnit) verifican que `domain` no dependa de
frameworks y que no haya imports cruzados entre bounded contexts.

Android: cada ViewModel con lógica lleva tests unitarios.

No se implementa una feature como "completa" sin sus tests correspondientes y sin cumplir el umbral de
cobertura.

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
PostgreSQL, Flyway (migraciones), JUnit 5 + Testcontainers, ArchUnit (reglas de arquitectura), JaCoCo
(cobertura, umbral 80% en `domain`/`application`).

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
- Convención de paquetes por bounded context en el backend:
  `com.walletapp.backend.<contexto>.{domain,application,infrastructure}`. El `plan.md` de cada
  feature DEBE mapear sus entidades/casos de uso a esta estructura antes de implementarse.

## Governance

Esta constitución tiene prioridad sobre decisiones ad-hoc durante la implementación. Cualquier
excepción a un principio debe justificarse explícitamente en el `plan.md` del feature correspondiente.
Enmiendas a este documento requieren bump de versión y quedan documentadas en el historial de git.

**Version**: 2.0.0 | **Ratified**: 2026-07-17 | **Last Amended**: 2026-07-17
