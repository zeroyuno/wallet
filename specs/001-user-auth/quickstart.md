# Quickstart: Autenticación de usuario

Guía para validar la feature de extremo a extremo una vez implementada.

## Prerrequisitos

- `docker compose up -d` (levanta Postgres en `localhost:5432`, ver [docker-compose.yml](../../docker-compose.yml))
- Backend corriendo: `cd backend && ./mvnw spring-boot:run`

## Escenarios de validación

### 1. Registro (User Story 1)

```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"password123","displayName":"Ana"}'
```

Esperado: `201 Created` con el usuario creado (sin `passwordHash` en la respuesta).

Repetir la misma petición: esperado `409 Conflict` (email ya en uso, FR-002).

### 2. Login (User Story 2)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"password123"}' | jq -r .accessToken)
```

Esperado: `200 OK` con `accessToken`. Con contraseña incorrecta: `401 Unauthorized` genérico (FR-005).

### 3. Endpoint protegido (User Story 2 y 3)

```bash
curl -i http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
```

Esperado: `200 OK` con los datos del usuario autenticado. Sin header `Authorization`: `401`.

### 4. Logout (User Story 3)

```bash
curl -i -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer $TOKEN"
```

Esperado: `204 No Content`. Repetir `GET /api/auth/me` con el mismo `$TOKEN`: ahora `401`
(FR-007/FR-008).

### 5. Rate limiting (Edge case)

Enviar 6 logins seguidos con contraseña incorrecta para el mismo email: la 6ª respuesta (y las
siguientes dentro de la ventana de bloqueo) debe ser `429 Too Many Requests` (FR-010).
