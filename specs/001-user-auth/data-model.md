# Data Model: Autenticación de usuario

Modelado como `domain` del bounded context `auth` (clases planas, sin JPA — ver research.md). Cada
entidad de dominio tiene su contraparte de persistencia (`*Entity`) en `auth/infrastructure/persistence`.

## User (aggregate root)

| Campo | Tipo (dominio) | Reglas |
|---|---|---|
| `id` | `UserId` (value object, envuelve UUID) | generado por el sistema |
| `email` | `Email` (value object) | formato de email válido (FR-003); único; usado como identificador de login |
| `passwordHash` | `PasswordHash` (value object) | producido por el puerto `PasswordHasher`; nunca se expone fuera del agregado |
| `displayName` | `String` | no vacío |
| `createdAt` | `Instant` | fecha de creación |
| `failedLoginAttempts` | `int` | regla de negocio de bloqueo (FR-010, research.md) |
| `lockedUntil` | `Instant` (nullable) | si está en el futuro, `LoginUseCase` rechaza aunque las credenciales sean correctas |

**Comportamiento del agregado** (métodos de negocio, no getters/setters anémicos):
- `User.register(email, passwordHash, displayName)` — factory estático, valida invariantes de creación.
- `user.verifyPassword(rawPassword, hasher)` — encapsula la verificación.
- `user.registerFailedLogin()` / `user.registerSuccessfulLogin()` — aplican la regla de bloqueo
  (5 fallos en 15 min → bloqueo de 15 min).

## RevokedToken (entidad)

| Campo | Tipo | Reglas |
|---|---|---|
| `jti` | `String` | identificador único del JWT revocado |
| `expiresAt` | `Instant` | igual al `exp` original; permite purgar filas irrelevantes más adelante |

## Puertos de salida (interfaces en `auth/domain`)

- `UserRepository`: `save(User)`, `findByEmail(Email)`, `findById(UserId)`.
- `RevokedTokenRepository`: `save(RevokedToken)`, `existsByJti(String)`.
- `PasswordHasher`: `hash(String rawPassword)`, `matches(String rawPassword, PasswordHash hash)`.

## Puertos de salida (interfaces en `auth/application`)

- `TokenIssuer`: `issue(UserId, Email)` → token + `jti` + `expiresAt`. Detalle de JWT (firma,
  claims) es responsabilidad exclusiva del adaptador `JjwtTokenIssuer` en `infrastructure`.

## Persistencia (auth/infrastructure/persistence, no es "el dominio")

- `UserEntity` (`@Entity`, tabla `users`): mismos campos que `User` pero con anotaciones JPA.
- `RevokedTokenEntity` (`@Entity`, tabla `revoked_tokens`): `jti` como `@Id`.
- `JpaUserRepository`/`JpaRevokedTokenRepository`: implementan los puertos del dominio, mapean
  `UserEntity ↔ User` y `RevokedTokenEntity ↔ RevokedToken`.

## Relaciones

`RevokedToken` no referencia a `User` — el `jti` por sí solo basta para la verificación de revocación
(igual que en el diseño original, ver research.md de la versión anterior de esta spec).
