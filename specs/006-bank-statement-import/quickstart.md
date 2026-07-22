# Quickstart: Importar movimientos desde estados de cuenta bancarios en PDF

Guía para validar la feature de extremo a extremo una vez implementada. Requiere las features 001
(autenticación), 002 (cuentas) y 003 (transacciones) ya implementadas, una cuenta propia ya creada, un
PDF real de un estado de cuenta de esa cuenta, y una `ANTHROPIC_API_KEY` válida configurada en el
backend.

## Prerrequisitos

- `docker compose up -d` y backend corriendo, con `ANTHROPIC_API_KEY` exportada en el entorno
- Un `$TOKEN` de un usuario ya autenticado (login de la feature 001)
- Una cuenta propia ya creada (`$ACCOUNT_ID`, feature 002)
- Un PDF real de estado de cuenta de esa cuenta (`$PDF_PATH`)

## Escenarios de validación

### 1. Importar un PDF de estado de cuenta (User Story 1)

```bash
curl -i -X POST http://localhost:8080/api/statement-imports \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$PDF_PATH;type=application/pdf" \
  -F "accountId=$ACCOUNT_ID"
# guardar el id devuelto como $IMPORT_ID

curl -s http://localhost:8080/api/statement-imports/$IMPORT_ID -H "Authorization: Bearer $TOKEN"
```

Esperado: `202` al subir. Consultando el estado, eventualmente `status` pasa a `COMPLETED` (o
`FAILED` con un `failureReason` legible si algo salió mal) y `transactionsImported` coincide
aproximadamente con la cantidad de movimientos visibles en el PDF. Verificar en
`GET /api/transactions?accountId=$ACCOUNT_ID` que aparecen con monto, fecha, tipo y descripción
razonables, y sin categoría asignada (FR-012).

### 2. Ver líneas no interpretadas (User Story 2, FR-008)

Con un PDF que tenga alguna línea ambigua o poco clara (o forzando el caso con un PDF de mala
calidad):

```bash
curl -s http://localhost:8080/api/statement-imports/$IMPORT_ID -H "Authorization: Bearer $TOKEN" | jq '.errors'
```

Esperado: el array `errors` incluye una entrada con `rawText` (el texto de esa línea) y un `reason`
legible, y `status` sigue siendo `COMPLETED` (una línea no interpretada no interrumpe el resto).

### 3. Subir el mismo PDF dos veces no duplica nada (FR-006, SC-003, User Story 3)

```bash
curl -i -X POST http://localhost:8080/api/statement-imports \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$PDF_PATH;type=application/pdf" \
  -F "accountId=$ACCOUNT_ID"

curl -s "http://localhost:8080/api/transactions?accountId=$ACCOUNT_ID" -H "Authorization: Bearer $TOKEN" | jq 'length'
```

Esperado: la cantidad de movimientos de la cuenta es la misma que después del escenario 1 — ninguno
se duplicó.

### 4. Cuenta ajena o inexistente (FR-002, edge case)

```bash
curl -i -X POST http://localhost:8080/api/statement-imports \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$PDF_PATH;type=application/pdf" \
  -F "accountId=00000000-0000-0000-0000-000000000000"
```

Esperado: `404`, sin que se haya creado ninguna importación ni movimiento.

### 5. Aislamiento entre usuarios (FR-007)

Con un segundo usuario (`$TOKEN_2`):

```bash
curl -i http://localhost:8080/api/statement-imports/$IMPORT_ID -H "Authorization: Bearer $TOKEN_2"
```

Esperado: `404` (no `403`) — mismo patrón ya usado en el resto de la API.
