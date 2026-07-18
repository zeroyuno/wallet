# Quickstart: Transacciones (ingresos y gastos)

Guía para validar la feature de extremo a extremo una vez implementada. Requiere las features 001
(autenticación) y 002 (cuentas y categorías) ya implementadas.

## Prerrequisitos

- `docker compose up -d` y backend corriendo
- Un `$TOKEN` de un usuario ya autenticado (login de la feature 001)
- Una cuenta propia (`$ACCOUNT_ID`) y, opcionalmente, una categoría propia de tipo `EXPENSE`
  (`$CATEGORY_ID`) — creadas vía la API de la feature 002

## Escenarios de validación

### 1. Registrar un gasto y ver el saldo actualizado (User Story 1, FR-002)

```bash
curl -s http://localhost:8080/api/accounts/$ACCOUNT_ID/balance -H "Authorization: Bearer $TOKEN"
# balance inicial = saldo inicial de la cuenta (sin transacciones todavía)

curl -i -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"type\":\"EXPENSE\",\"amount\":50,\"date\":\"2026-07-18\",\"accountId\":\"$ACCOUNT_ID\",\"categoryId\":\"$CATEGORY_ID\"}"
# guardar el id devuelto como $TX_ID

curl -s http://localhost:8080/api/accounts/$ACCOUNT_ID/balance -H "Authorization: Bearer $TOKEN"
```

Esperado: `201` al crear; el segundo `balance` es exactamente 50 menos que el primero.

### 2. Listar y filtrar (User Story 1 y 3)

```bash
curl -s http://localhost:8080/api/transactions -H "Authorization: Bearer $TOKEN"

curl -s "http://localhost:8080/api/transactions?accountId=$ACCOUNT_ID" -H "Authorization: Bearer $TOKEN"

curl -s "http://localhost:8080/api/transactions?dateFrom=2026-07-01&dateTo=2026-07-31" \
  -H "Authorization: Bearer $TOKEN"
```

Esperado: la transacción creada aparece en la lista sin filtros y en ambos filtros (coincide con la
cuenta y con el rango de fechas).

### 3. Editar y verificar que el saldo se ajusta (User Story 2, FR-005)

```bash
curl -i -X PUT http://localhost:8080/api/transactions/$TX_ID \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"amount\":80,\"date\":\"2026-07-18\",\"categoryId\":\"$CATEGORY_ID\"}"

curl -s http://localhost:8080/api/accounts/$ACCOUNT_ID/balance -H "Authorization: Bearer $TOKEN"
```

Esperado: `200` al editar; el saldo refleja únicamente el nuevo monto (80), no la suma de 50 + 80.

### 4. Eliminar y verificar que el saldo vuelve a su estado previo (User Story 2, FR-006)

```bash
curl -i -X DELETE http://localhost:8080/api/transactions/$TX_ID -H "Authorization: Bearer $TOKEN"

curl -s http://localhost:8080/api/accounts/$ACCOUNT_ID/balance -H "Authorization: Bearer $TOKEN"
```

Esperado: `204` al eliminar; el saldo vuelve a coincidir con el saldo inicial de la cuenta (el mismo
valor del primer paso del escenario 1).

### 5. Aislamiento entre usuarios (FR-003, FR-007 — el requisito más crítico)

Con un segundo usuario (`$TOKEN_2`) y su propia cuenta:

```bash
curl -i http://localhost:8080/api/transactions/$TX_ID -H "Authorization: Bearer $TOKEN_2"
```

Esperado: `404` (no `403`).

### 6. No se puede eliminar una cuenta o categoría con transacciones (FR-010)

Con una transacción todavía activa sobre `$ACCOUNT_ID`:

```bash
curl -i -X DELETE http://localhost:8080/api/accounts/$ACCOUNT_ID -H "Authorization: Bearer $TOKEN"
```

Esperado: `409` (no `401` — este fue exactamente el bug corregido en la feature 002 para
subcategorías; acá se verifica que el mismo patrón, ahora vía restricción de base de datos, también
responde el código correcto).

### 7. Categoría de tipo distinto rechazada (FR-008, edge case)

```bash
# Con $INCOME_CATEGORY_ID de tipo INCOME
curl -i -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"type\":\"EXPENSE\",\"amount\":10,\"date\":\"2026-07-18\",\"accountId\":\"$ACCOUNT_ID\",\"categoryId\":\"$INCOME_CATEGORY_ID\"}"
```

Esperado: `400`.
