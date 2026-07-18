# Wallet

App personal de gestión de ingresos y gastos. Monorepo con dos aplicaciones independientes,
acopladas solo por el contrato de API REST.

## Estructura

```
wallet/
  backend/   # API REST — Java 25 + Spring Boot 4.1.0
  android/   # App Android nativa — Kotlin + Jetpack Compose
  specs/     # Especificaciones de features (Spec-Driven Development)
```

## Metodología

Este proyecto se desarrolla con **Spec-Driven Development** usando
[Spec Kit](https://github.com/github/spec-kit). Cada feature nueva sigue el flujo:

1. `/speckit-specify` — define el qué y el porqué
2. `/speckit-plan` — define el cómo (modelo de datos, contrato de API, decisiones técnicas)
3. `/speckit-tasks` — desglosa en tareas ejecutables
4. `/speckit-implement` — implementa

Los principios del proyecto (stack, arquitectura, testing) están documentados en
[`.specify/memory/constitution.md`](.specify/memory/constitution.md).

## Arquitectura del backend

Monolito modular: un único desplegable, dividido en módulos por *bounded context*
(`com.walletapp.backend.<contexto>`), cada uno con arquitectura hexagonal:

```
<contexto>/
  domain/           # entidades, value objects, reglas de negocio — sin dependencias de Spring/JPA
  application/       # casos de uso que orquestan el dominio
  infrastructure/    # adaptadores: controllers REST, repositorios JPA
```

Ningún módulo importa `domain`/`infrastructure` de otro directamente (verificado con ArchUnit). Este
diseño permite extraer un contexto a un microservicio real más adelante si el proyecto lo justifica,
sin reescribir el dominio. `domain` y `application` requieren >80% de cobertura (JaCoCo, verificado en
`mvn verify`).

## API

- `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`
- `GET/POST /api/accounts`, `PUT/DELETE /api/accounts/{id}`
- `GET/POST /api/categories`, `PUT/DELETE /api/categories/{id}` (soporta `parentCategoryId` opcional
  para subcategorías; no se puede eliminar una categoría que todavía tiene subcategorías — `409`)

Contratos completos (OpenAPI) en `specs/<feature>/contracts/`.

## Stack

**Backend**: Java 25, Spring Boot 4.1.0, Spring Data JPA, Spring Security (JWT), PostgreSQL, Flyway,
ArchUnit, JaCoCo.

**Android**: Kotlin, Jetpack Compose, MVVM, Hilt, Retrofit.

## Desarrollo local

```bash
docker compose up -d        # levanta Postgres
cd backend && ./mvnw spring-boot:run
```

Luego abrir `android/` en Android Studio y correr en un emulador (apunta a `http://10.0.2.2:8080`
por defecto para el emulador de Android).

### Configuración de JWT

El backend firma los tokens con `app.security.jwt.secret` (ver
[application.properties](backend/src/main/resources/application.properties)). El valor por defecto
es solo para desarrollo local — en cualquier otro entorno, sobreescribirlo con la variable de entorno
`JWT_SECRET` (mínimo 32 caracteres). La expiración se controla con `app.security.jwt.expiration`
(por defecto `7d`).

### Tests

```bash
cd backend && ./mvnw verify   # unitarios + ArchUnit + integración (Testcontainers) + cobertura 80%
```

Requiere Docker corriendo (Testcontainers levanta su propio Postgres, independiente del de
`docker-compose.yml`).

### Mejoras futuras (no bloqueantes)

- Purga periódica de filas expiradas en `revoked_tokens` (`specs/001-user-auth/tasks.md`, T044) — hoy
  esas filas simplemente dejan de ser relevantes (el JWT ya habría expirado de todas formas), pero
  limpiarlas evitaría que la tabla crezca indefinidamente.
- La app Android no verifica si ya hay un token válido guardado al iniciar — siempre muestra Login,
  aunque `TokenStore` persista la sesión entre instalaciones/reinicios de la app.
- `AccountListScreen`/`CategoryListScreen` no tienen forma de volver a Home dentro de la app (el botón
  atrás del sistema cierra la Activity en vez de navegar); falta un `NavHost` real con back stack.
