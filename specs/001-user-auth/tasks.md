# Tasks: Autenticación de usuario

**Input**: Design documents from `/specs/001-user-auth/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/auth-api.yaml](contracts/auth-api.yaml),
[quickstart.md](quickstart.md)

**Tests**: incluidos — constitución, principio III: >80% cobertura en `domain`/`application`.

**Organization**: tareas agrupadas por historia de usuario; dentro de cada historia, siguiendo el
orden hexagonal `domain → application → infrastructure`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo
- **[Story]**: US1 = Registro, US2 = Login, US3 = Sesión persistente y logout

## Path Conventions

- Backend: `backend/src/main/java/com/walletapp/backend/auth/{domain,application,infrastructure}/...`
- Shared: `backend/src/main/java/com/walletapp/backend/shared/security/...`
- Android: `android/app/src/main/java/com/walletapp/android/auth/...`

---

## Phase 1: Setup

- [ ] T001 Crear migración Flyway `backend/src/main/resources/db/migration/V1__create_users_and_revoked_tokens.sql`
      con las tablas `users` y `revoked_tokens` (ver [data-model.md](data-model.md))
- [ ] T002 [P] Añadir dependencias `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` en
      `backend/pom.xml`
- [ ] T003 [P] Añadir dependencia `androidx.security:security-crypto` en
      `android/gradle/libs.versions.toml` y `android/app/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: el dominio del contexto `auth` y la infraestructura de seguridad transversal.

**⚠️ CRITICAL**: ninguna historia de usuario puede empezar hasta que esta fase esté completa.

### Domain (`auth/domain`) — sin dependencias de Spring/JPA

- [ ] T004 [P] Crear value objects `UserId`, `Email`, `PasswordHash` en
      `backend/src/main/java/com/walletapp/backend/auth/domain/`
- [ ] T005 [P] Crear agregado `User` (factory `register`, `verifyPassword`,
      `registerFailedLogin`/`registerSuccessfulLogin`) en `auth/domain/User.java`
- [ ] T006 [P] Crear entidad `RevokedToken` en `auth/domain/RevokedToken.java`
- [ ] T007 [P] Crear puertos de salida `UserRepository`, `RevokedTokenRepository`, `PasswordHasher`
      (interfaces) en `auth/domain/`
- [ ] T008 [P] Crear excepciones de dominio (`EmailAlreadyInUseException`,
      `InvalidCredentialsException`, `AccountLockedException`) en `auth/domain/exception/`
- [ ] T009 [P] [ARQUITECTURA] Test ArchUnit: `auth.domain` no importa `org.springframework.*` ni
      `jakarta.persistence.*` en `backend/src/test/java/com/walletapp/backend/ArchitectureTest.java`
      (regla reusable para todos los contextos futuros)

### Application (`auth/application`)

- [ ] T010 Crear puerto `TokenIssuer` (interfaz) en `auth/application/TokenIssuer.java`
- [ ] T011 [P] Crear DTOs de aplicación (`RegisterCommand`, `LoginCommand`, `AuthResult`, `UserView`)
      en `auth/application/dto/`

### Infrastructure — persistencia y seguridad transversal

- [ ] T012 [P] Crear `UserEntity`/`RevokedTokenEntity` (JPA) y `JpaUserRepository`/
      `JpaRevokedTokenRepository` (implementan los puertos de T007) en
      `auth/infrastructure/persistence/` (depende de T004-T007)
- [ ] T013 [P] Implementar `BCryptPasswordHasher` (implementa `PasswordHasher`) en
      `auth/infrastructure/security/BCryptPasswordHasher.java`
- [ ] T014 Implementar `JjwtTokenIssuer` (implementa `TokenIssuer`, genera JWT con `jti`) en
      `auth/infrastructure/security/JjwtTokenIssuer.java` (depende de T010)
- [ ] T015 Implementar `JwtTokenValidator` (valida firma/`exp`, consulta `RevokedTokenRepository`) en
      `backend/src/main/java/com/walletapp/backend/shared/security/JwtTokenValidator.java` (depende de
      T007, T014 — misma clave de firma)
- [ ] T016 Implementar `JwtAuthFilter` + `SecurityConfig` (registra el filtro, reglas públicas vs.
      protegidas) en `backend/src/main/java/com/walletapp/backend/shared/security/` (depende de T015)
- [ ] T017 [P] [ARQUITECTURA] Test ArchUnit: ningún paquete de un contexto (`auth`, futuros) importa
      `infrastructure`/`domain` de otro contexto, salvo `shared.*` (mismo archivo de T009)

**Checkpoint**: infraestructura de auth y seguridad transversal listas — las historias pueden empezar.

---

## Phase 3: User Story 1 - Registro de nueva cuenta (Priority: P1) 🎯 MVP

**Goal**: una persona nueva puede crear su cuenta con email y contraseña.

**Independent Test**: `POST /api/auth/register` con email/contraseña válidos crea la cuenta; repetir
con el mismo email devuelve 409 (quickstart.md, escenario 1).

### Tests for User Story 1

- [ ] T018 [P] [US1] Test unitario de `User.register`/`RegisterUserUseCase` (sin Spring ni DB — mocks
      de los puertos) en `backend/src/test/java/com/walletapp/backend/auth/application/RegisterUserUseCaseTest.java`
- [ ] T019 [P] [US1] Test de integración de `POST /api/auth/register` (éxito y email duplicado) con
      Testcontainers en `backend/src/test/java/com/walletapp/backend/auth/infrastructure/AuthControllerIT.java`

### Implementation for User Story 1

- [ ] T020 [US1] Implementar `RegisterUserUseCase` en `auth/application/RegisterUserUseCase.java`
      (depende de T005, T007, T008, T011)
- [ ] T021 [US1] Implementar `POST /api/auth/register` en `auth/infrastructure/web/AuthController.java`
      (mapea DTOs web ↔ DTOs de aplicación; depende de T020)
- [ ] T022 [P] [US1] Crear `RegisterScreen.kt` (Compose) en
      `android/app/src/main/java/com/walletapp/android/auth/ui/RegisterScreen.kt`
- [ ] T023 [US1] Implementar `AuthApi.register` (Retrofit) y `AuthRepository.register` en Android
- [ ] T024 [US1] Implementar `AuthViewModel` con el estado de registro (depende de T023)

**Checkpoint**: User Story 1 funcional; verificar cobertura de `auth/domain` + `auth/application` con
`./mvnw verify` antes de seguir.

---

## Phase 4: User Story 2 - Inicio de sesión (Priority: P1)

**Goal**: un usuario registrado inicia sesión y recibe un token que le da acceso a endpoints
protegidos.

**Independent Test**: `POST /api/auth/login` con credenciales correctas devuelve un `accessToken`
válido; con credenciales incorrectas devuelve 401 genérico (quickstart.md, escenario 2).

### Tests for User Story 2

- [ ] T025 [P] [US2] Test unitario de `LoginUseCase` incluyendo el conteo de intentos fallidos y
      bloqueo temporal (FR-010) en `auth/application/LoginUseCaseTest.java`
- [ ] T026 [P] [US2] Test de integración de `POST /api/auth/login` (éxito, credenciales inválidas,
      bloqueo por rate limiting) en `AuthControllerIT.java`

### Implementation for User Story 2

- [ ] T027 [US2] Implementar `LoginUseCase` (valida credenciales vía `user.verifyPassword`, aplica
      `registerFailedLogin`/`registerSuccessfulLogin`, emite token vía `TokenIssuer`) en
      `auth/application/LoginUseCase.java` (depende de T005, T007, T010)
- [ ] T028 [US2] Implementar `POST /api/auth/login` en `AuthController.java` (depende de T027)
- [ ] T029 [P] [US2] Crear `TokenStore.kt` (EncryptedSharedPreferences) en
      `android/app/src/main/java/com/walletapp/android/data/TokenStore.kt`
- [ ] T030 [US2] Implementar `AuthApi.login` y `AuthRepository.login` (persiste el token vía
      `TokenStore`) (depende de T029)
- [ ] T031 [P] [US2] Crear `LoginScreen.kt` en
      `android/app/src/main/java/com/walletapp/android/auth/ui/LoginScreen.kt`
- [ ] T032 [US2] Extender `AuthViewModel` con el estado de login (depende de T030)

**Checkpoint**: US1 y US2 funcionan de forma independiente; cobertura >80% se mantiene.

---

## Phase 5: User Story 3 - Sesión persistente y cierre de sesión (Priority: P2)

**Goal**: el usuario permanece autenticado entre usos de la app y puede cerrar sesión explícitamente.

**Independent Test**: un token emitido sigue siendo aceptado por `GET /api/auth/me` hasta que expira o
hasta `POST /api/auth/logout`, tras lo cual `GET /api/auth/me` devuelve 401 (quickstart.md, escenarios
3 y 4).

### Tests for User Story 3

- [ ] T033 [P] [US3] Test unitario de `LogoutUseCase` (inserta `jti` vía `RevokedTokenRepository`) en
      `auth/application/LogoutUseCaseTest.java`
- [ ] T034 [P] [US3] Test de integración de `GET /api/auth/me` y `POST /api/auth/logout` (incluyendo
      reutilización de un token ya invalidado) en `AuthControllerIT.java`

### Implementation for User Story 3

- [ ] T035 [US3] Implementar `GetCurrentUserUseCase` en `auth/application/GetCurrentUserUseCase.java`
- [ ] T036 [US3] Implementar `GET /api/auth/me` en `AuthController.java` (usa la identidad resuelta
      por `JwtAuthFilter`, T016; depende de T035)
- [ ] T037 [US3] Implementar `LogoutUseCase` en `auth/application/LogoutUseCase.java` (depende de T006,
      T007)
- [ ] T038 [US3] Implementar `POST /api/auth/logout` en `AuthController.java` (depende de T037)
- [ ] T039 [P] [US3] Añadir interceptor Retrofit que adjunta el token de `TokenStore` a cada request
      en `android/app/src/main/java/com/walletapp/android/di/NetworkModule.kt` (depende de T029)
- [ ] T040 [US3] Implementar logout en `AuthRepository`/`AuthViewModel` (limpia `TokenStore`) (depende
      de T039)

**Checkpoint**: las tres historias funcionan de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T041 Ejecutar `./mvnw verify` y confirmar que `domain`/`application` de `auth` superan el 80% de
      cobertura (constitución, principio III) — completar tests donde falte antes de cerrar la feature
- [ ] T042 [P] Documentar configuración de JWT (secreto, expiración) como variables de entorno en
      `backend/src/main/resources/application.properties` y en `README.md`
- [ ] T043 Ejecutar [quickstart.md](quickstart.md) de punta a punta contra la implementación real
- [ ] T044 [P] Documentar como mejora futura (no bloqueante) la purga periódica de filas expiradas en
      `revoked_tokens`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias
- **Foundational (Fase 2)**: depende de Setup — bloquea las tres historias; dentro de la fase,
  `domain` (T004-T009) antes que `infrastructure` (T012-T017), y `application` (T010-T011) puede ir en
  paralelo con `infrastructure` de persistencia una vez el dominio existe
- **User Stories (Fases 3-5)**: todas dependen de Foundational
- **Polish (Fase 6)**: depende de las historias completas, incluyendo la verificación de cobertura

### Parallel Opportunities

- T004-T009 (domain, Foundational) en paralelo — archivos distintos, sin dependencias entre sí salvo
  donde se indica
- Tests de cada historia ([P]) en paralelo entre sí antes de su implementación
- Tareas Android [P] en paralelo con las de backend de la misma historia

---

## Implementation Strategy

### MVP First (User Story 1 solamente)

1. Fase 1: Setup
2. Fase 2: Foundational (bloqueante) — domain → application/infrastructure
3. Fase 3: User Story 1 (Registro)
4. `./mvnw verify` (cobertura) + quickstart.md, escenario 1

### Incremental Delivery

1. Setup + Foundational → base lista, con las reglas ArchUnit ya protegiendo la arquitectura desde el
   día 1
2. US1 (Registro) → MVP
3. US2 (Login) → cobertura y quickstart
4. US3 (Sesión persistente/logout)
5. Polish (incluye la verificación final de cobertura 80%)

---

## Notes

- Escribir primero los tests de `domain`/`application` (no requieren Spring ni Testcontainers, corren
  en milisegundos) antes que los de integración — acelera el ciclo TDD y ayuda a llegar al 80%.
- `docker compose up -d` antes de correr los tests de integración
- Detenerse en cada checkpoint para validar la historia y la cobertura antes de continuar
