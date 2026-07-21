# Quickstart: Importar datos desde BudgetBakers Wallet

Guía para validar la feature de extremo a extremo una vez implementada. Requiere las features 001
(autenticación), 002 (cuentas y categorías) y 003 (transacciones) ya implementadas, y un token real de
la API de BudgetBakers Wallet (`WALLET_TOKEN`) con datos de prueba conocidos en esa cuenta.

## Prerrequisitos

- `docker compose up -d` y backend corriendo
- Un `$TOKEN` de un usuario ya autenticado (login de la feature 001)
- Un `$WALLET_TOKEN` real de BudgetBakers Wallet, con al menos: 1 cuenta, 1 categoría con una
  subcategoría, y algunos movimientos (ingresos y gastos) en esa cuenta

## Escenarios de validación

### 1. Importar cuentas y categorías (User Story 1)

```bash
curl -i -X POST http://localhost:8080/api/imports \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"walletApiToken\":\"$WALLET_TOKEN\"}"
# guardar el id devuelto como $IMPORT_ID

curl -s http://localhost:8080/api/imports/$IMPORT_ID -H "Authorization: Bearer $TOKEN"
```

Esperado: `202` al iniciar. Consultando el estado, eventualmente `status` pasa a `COMPLETED` (o queda
`IN_PROGRESS` mientras corre) y `accountsImported`/`categoriesImported` coinciden con lo que había en
la cuenta de Wallet usada. Verificar en `GET /api/accounts` y `GET /api/categories` que aparecen con el
mismo nombre, y que la subcategoría de prueba mantiene su categoría padre.

### 2. Importar el historial de movimientos (User Story 2)

```bash
curl -s http://localhost:8080/api/transactions -H "Authorization: Bearer $TOKEN"
```

Esperado: cada movimiento de la cuenta de Wallet de prueba aparece con el mismo monto, fecha y tipo
(ingreso/gasto); la categoría queda asociada si tenía una, `description` refleja la `note` de Wallet, y
`counterParty`/`paymentType`/`recordState`/`walletTransferId`/`labels` aparecen en la respuesta tal
cual venían en Wallet (todos los campos de `Record` excepto `photos` y `place`, ver data-model.md).

### 3. Correr la importación dos veces no duplica nada (FR-006, SC-003)

```bash
curl -i -X POST http://localhost:8080/api/imports \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"walletApiToken\":\"$WALLET_TOKEN\"}"

curl -s http://localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN" | jq 'length'
curl -s http://localhost:8080/api/transactions -H "Authorization: Bearer $TOKEN" | jq 'length'
```

Esperado: la cantidad de cuentas y movimientos es exactamente la misma que después del escenario 1 —
ninguno se duplicó.

### 4. Token de Wallet inválido (FR-009, edge case)

```bash
curl -i -X POST http://localhost:8080/api/imports \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"walletApiToken\":\"token-invalido-a-proposito\"}"
```

Esperado: `400`, sin que se haya creado ninguna cuenta/categoría/movimiento nuevo.

### 5. Ver errores de importación (User Story 3, FR-007)

Con un movimiento de Wallet cuya cuenta no se pudo importar (simular borrando esa cuenta en Wallet
entre el paso 1 y este, o usando datos de prueba con una cuenta ya eliminada):

```bash
curl -s http://localhost:8080/api/imports/$IMPORT_ID -H "Authorization: Bearer $TOKEN" | jq '.errors'
```

Esperado: el array `errors` incluye una entrada con `entityType: "TRANSACTION"` y un `reason` legible,
y `status` sigue siendo `COMPLETED` (el error puntual no interrumpe el resto de la importación).

### 6. Aislamiento entre usuarios (FR-010)

Con un segundo usuario (`$TOKEN_2`):

```bash
curl -i http://localhost:8080/api/imports/$IMPORT_ID -H "Authorization: Bearer $TOKEN_2"
```

Esperado: `404` (no `403`) — mismo patrón ya usado en cuentas, categorías y transacciones.
