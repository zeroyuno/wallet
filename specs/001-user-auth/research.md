# Research: Autenticación de usuario

## Estrategia de token y revocación (FR-006, FR-007, FR-008)

**Decision**: Un único JWT de acceso (sin refresh tokens), con expiración de 7 días, más una tabla
`revoked_tokens` en Postgres que guarda el `jti` (JWT ID) de tokens invalidados explícitamente por
logout. El puerto `TokenIssuer` (capa `application`) emite el token; su verificación (firma + `exp` +
consulta de revocación) vive en `shared/security` porque se ejecuta en cada request de cualquier
contexto, no solo de `auth`.

**Rationale**: Satisface FR-006, FR-007 y FR-008 sin infraestructura extra (ej. Redis) ni la
complejidad de rotación de refresh tokens — apropiado para el alcance y escala de esta v1
(constitución, principio V: YAGNI).

**Alternatives considered**:
- *JWT stateless puro sin revocación*: rechazado — no cumple FR-007.
- *Refresh token rotation completo*: rechazado por ahora — complejidad no justificada por los
  requisitos actuales.

## Hashing de contraseñas (FR-009)

**Decision**: Puerto `PasswordHasher` en `auth/domain`, implementado en `auth/infrastructure` con
`BCryptPasswordEncoder` de Spring Security.

**Rationale**: El dominio depende de una interfaz propia (no de Spring Security directamente),
cumpliendo el principio II (domain sin dependencias de framework); la implementación sí puede usar
Spring Security porque vive en `infrastructure`.

**Alternatives considered**: Argon2 — rechazado para v1 por dependencia extra, revisable después.

## Rate limiting de login (FR-010)

**Decision**: Contadores de intentos fallidos como parte del propio agregado `User`
(`failedLoginAttempts`, `lockedUntil`) — es una regla de negocio del dominio ("una cuenta se bloquea
tras N fallos"), no un detalle de infraestructura. `LoginUseCase` aplica la regla; la persistencia del
contador es responsabilidad del adaptador `JpaUserRepository`.

**Rationale**: Mantiene la regla de negocio en `domain`/`application` (testeable sin Spring ni base de
datos real) y evita infraestructura adicional (Redis/Bucket4j) no justificada por la escala actual.

**Alternatives considered**: Rate limiter distribuido (Redis) — rechazado como prematuro.

## Librería JWT

**Decision**: `io.jsonwebtoken:jjwt`, usada únicamente detrás de los puertos `TokenIssuer` (en
`auth/infrastructure`) y del validador en `shared/security` — el dominio nunca importa esta librería.

**Rationale**: API simple, ampliamente usada con Spring Security sin acoplarse a Spring OAuth2.

**Alternatives considered**: Nimbus JOSE+JWT — igualmente válida, jjwt tiene API más directa.

## Separación domain vs. entidades JPA

**Decision**: `User` y `RevokedToken` en `auth/domain` son clases Java planas (sin anotaciones JPA).
`auth/infrastructure/persistence` define `UserEntity`/`RevokedTokenEntity` (con `@Entity`) y mapea
entre ambas en `JpaUserRepository`/`JpaRevokedTokenRepository`.

**Rationale**: Es el requisito central de la arquitectura hexagonal (constitución, principio II): el
dominio no puede depender de JPA/Hibernate. El costo es un mapeo manual adicional, aceptado a cambio de
poder testear todas las reglas de negocio sin levantar Spring ni una base de datos.

**Alternatives considered**: Anotar `User` directamente con `@Entity` (un solo objeto) — rechazado,
es justo lo que la constitución prohíbe (domain acoplado a framework).

## Almacenamiento del token en Android

**Decision**: `androidx.security.crypto` (EncryptedSharedPreferences) para persistir el JWT en el
dispositivo entre sesiones de la app.

**Rationale**: Mecanismo estándar de Android para datos sensibles, sin depender de servicios externos.

**Alternatives considered**: DataStore sin cifrado — rechazado, el JWT es un dato sensible.
