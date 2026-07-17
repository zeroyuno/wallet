# Implementation Plan: Autenticación de usuario

**Branch**: `001-user-auth` | **Date**: 2026-07-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-user-auth/spec.md`

## Summary

Registro e inicio de sesión con email/contraseña, emitiendo un JWT que identifica al usuario en cada
petición posterior. Se implementa como el bounded context `auth` del monolito modular (arquitectura
hexagonal: `domain` → `application` → `infrastructure`), consumido por pantallas de registro/login en
`android/`. Esta feature es la base de aislamiento de datos por usuario que usarán todos los contextos
siguientes.

## Technical Context

**Language/Version**: Backend: Java 25 (Spring Boot 4.1.0). Android: Kotlin 2.2.21 (Jetpack Compose,
AGP 8.13.2).

**Primary Dependencies**: Backend: Spring Web, Spring Data JPA, Spring Security, Flyway, driver
PostgreSQL, `io.jsonwebtoken:jjwt` (ver research.md), ArchUnit + JaCoCo (verificación de arquitectura y
cobertura). Android: Retrofit + OkHttp, Hilt, kotlinx Coroutines/Flow, kotlinx.serialization.

**Storage**: PostgreSQL — tabla `users` y tabla `revoked_tokens` (ver data-model.md), propiedad
exclusiva del contexto `auth` (ningún otro módulo las consulta directamente).

**Testing**: Backend: JUnit 5 (unitarios sobre `domain`/`application`, objetivo >80% cobertura JaCoCo)
+ Testcontainers Postgres (integración de los adaptadores `infrastructure`) + ArchUnit (reglas de
capas y de no-dependencia entre módulos). Android: unit tests de `AuthViewModel`.

**Target Platform**: Backend: servicio JVM único (contenedor Linux en prod, local en dev vía
`./mvnw spring-boot:run`). Android: apps minSdk 26+.

**Project Type**: web-service (monolito modular) + mobile-app.

**Performance Goals**: Sin metas de throughput agresivas para v1 (app personal/multi-usuario de baja
escala) — SC-002 solo exige login en <10s en condiciones normales de red.

**Constraints**: Contraseñas nunca en texto plano (FR-009); aislamiento estricto de datos por usuario
vía el `sub` del JWT (constitución, principio IV); rate limiting de intentos fallidos de login
(FR-010); `domain`/`application` sin dependencias de framework (constitución, principio II);
cobertura >80% en esas capas (constitución, principio III).

**Scale/Scope**: Escala inicial pequeña (uso personal / pocos usuarios simultáneos).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. API Contract-First**: PASS — el contrato de los 4 endpoints de auth se define en
  `contracts/auth-api.yaml` antes de cualquier implementación.
- **II. Arquitectura hexagonal y DDD**: PASS — ver Project Structure: `auth/{domain,application,
  infrastructure}`. Única excepción documentada: un módulo compartido `shared/security` para el
  filtro JWT que se aplica a *todas* las requests del backend (ver Complexity Tracking — es
  infraestructura transversal, no lógica de negocio de ningún contexto).
- **III. Tests y cobertura >80%**: PASS — `AuthUseCases` y las entidades/value objects de `domain`
  llevan tests unitarios; el objetivo de cobertura se verifica con `mvn verify` (JaCoCo, ya configurado
  en el proyecto). Adaptadores de `infrastructure` llevan tests de integración con Testcontainers.
- **IV. Aislamiento de datos por usuario**: PASS — esta feature es la que *establece* el mecanismo (el
  `sub` del JWT) que los contextos siguientes usarán para filtrar datos por usuario.
- **V. Simplicidad y alcance por spec (YAGNI)**: PASS — sin recuperación de contraseña, sin login
  social/SSO, sin refresh tokens (ver research.md); solo lo que pide la spec.

## Project Structure

### Documentation (this feature)

```text
specs/001-user-auth/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── auth-api.yaml
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/walletapp/backend/
├── auth/
│   ├── domain/
│   │   ├── User.java                    # aggregate root
│   │   ├── UserId.java                  # value object (wraps UUID)
│   │   ├── Email.java                   # value object (formato + igualdad)
│   │   ├── PasswordHash.java            # value object
│   │   ├── RevokedToken.java            # entidad (jti, expiresAt)
│   │   ├── UserRepository.java          # puerto de salida (interfaz)
│   │   ├── RevokedTokenRepository.java  # puerto de salida (interfaz)
│   │   ├── PasswordHasher.java          # puerto de salida (interfaz)
│   │   └── exception/ (EmailAlreadyInUseException, InvalidCredentialsException,
│   │                    AccountLockedException)
│   ├── application/
│   │   ├── RegisterUserUseCase.java
│   │   ├── LoginUseCase.java
│   │   ├── LogoutUseCase.java
│   │   ├── GetCurrentUserUseCase.java
│   │   ├── TokenIssuer.java             # puerto de salida (interfaz; JWT es detalle de infra)
│   │   └── dto/ (RegisterCommand, LoginCommand, AuthResult, UserView)
│   └── infrastructure/
│       ├── web/AuthController.java      # adaptador de entrada (REST)
│       ├── web/dto/ (RegisterRequest, LoginRequest, AuthResponse, UserResponse)
│       ├── persistence/JpaUserRepository.java       # adaptador de salida (implementa UserRepository)
│       ├── persistence/JpaRevokedTokenRepository.java
│       ├── persistence/UserEntity.java, RevokedTokenEntity.java  # entidades JPA (no el dominio)
│       ├── security/BCryptPasswordHasher.java       # implementa PasswordHasher
│       └── security/JjwtTokenIssuer.java            # implementa TokenIssuer
├── shared/
│   └── security/
│       ├── JwtAuthFilter.java   # adaptador transversal: valida el JWT en cada request
│       ├── JwtTokenValidator.java  # verifica firma/expiración (usa la misma clave que JjwtTokenIssuer)
│       └── SecurityConfig.java  # registra el filtro y las reglas de acceso
└── (otros contextos futuros: account/, category/, ...)
```

**Structure Decision**: Nuevo contexto `auth` con las tres capas de la constitución. `shared/security`
es la única infraestructura transversal (ver Complexity Tracking). Android reutiliza el paquete
`auth` ya planeado (`ui`, `AuthViewModel`, `AuthRepository`, `AuthApi`, `data/TokenStore`).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| Módulo `shared/security` fuera de cualquier bounded context | El filtro que valida el JWT corre en *cada* request de *todos* los contextos (auth, accounts, categories...); ponerlo dentro de `auth/infrastructure` obligaría a que todo módulo futuro dependa de `auth/infrastructure`, violando la regla de no-dependencia entre módulos | Duplicar el filtro en cada contexto — rechazado, sería repetir la misma lógica de seguridad N veces y desincronizarse fácilmente |
