# Quickstart: Gestión de cuentas y categorías

Guía para validar la feature de extremo a extremo una vez implementada. Requiere la feature 001
(autenticación) ya implementada — ver [specs/001-user-auth/quickstart.md](../001-user-auth/quickstart.md)
para obtener un `$TOKEN` válido.

## Prerrequisitos

- `docker compose up -d` y backend corriendo (igual que en la feature 001)
- Un `$TOKEN` de un usuario ya autenticado (login de la feature 001)

## Escenarios de validación

### 1. Crear y listar cuentas (User Story 1)

```bash
curl -i -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Efectivo","type":"CASH","currency":"USD","initialBalance":100}'

curl -s http://localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN"
```

Esperado: `201` al crear, y la cuenta aparece en el `GET` posterior.

### 2. Editar y eliminar una cuenta (User Story 2)

```bash
curl -i -X PUT http://localhost:8080/api/accounts/$ACCOUNT_ID \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Efectivo (billetera)","type":"CASH","currency":"USD","initialBalance":100}'

curl -i -X DELETE http://localhost:8080/api/accounts/$ACCOUNT_ID -H "Authorization: Bearer $TOKEN"
```

Esperado: `200` al editar, `204` al eliminar; un `GET` posterior ya no la incluye.

### 3. Aislamiento entre usuarios (FR-002, FR-005 — el requisito más crítico)

Con un segundo usuario (`$TOKEN_2`, registrado y logueado por la feature 001):

```bash
curl -i http://localhost:8080/api/accounts/$ACCOUNT_ID -H "Authorization: Bearer $TOKEN_2"
```

Esperado: `404` (no `403`) — el usuario 2 no puede saber si la cuenta existe.

### 4. Crear categorías y subcategorías (User Story 3 y 4)

```bash
curl -s -X POST http://localhost:8080/api/categories \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Comida","type":"EXPENSE"}'
# guardar el id devuelto como $PARENT_ID

curl -i -X POST http://localhost:8080/api/categories \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"name\":\"Supermercado\",\"type\":\"EXPENSE\",\"parentCategoryId\":\"$PARENT_ID\"}"
```

Esperado: `201` en ambos casos; la segunda categoría muestra `parentCategoryId` al consultarla.

Repetir la creación de "Comida" tipo `EXPENSE`: esperado `409` (FR-007).
