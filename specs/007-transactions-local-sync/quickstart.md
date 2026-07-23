# Quickstart: Caché local y sincronización de movimientos en Android

Guía para validar la feature de extremo a extremo una vez implementada. Requiere las features 001
(autenticación), 002 (cuentas), 003 (transacciones) y 004 (UI Android de movimientos) ya implementadas,
con datos de prueba suficientes para notar la diferencia de rendimiento (idealmente una cuenta con
varios miles de movimientos, como la real).

## Prerrequisitos

- `docker compose up -d` y backend corriendo
- Un `$TOKEN` de un usuario ya autenticado, con una cuenta (`$ACCOUNT_ID`) que tenga muchos movimientos
- Un dispositivo/emulador Android con la app instalada apuntando a ese backend

## Escenarios de validación — backend (endpoint de sync)

### 1. Sincronización inicial completa (research.md #1)

```bash
curl -s "http://localhost:8080/api/transactions/sync?limit=200" -H "Authorization: Bearer $TOKEN" | jq '{count: (.upserts | length), hasMore, nextSince}'
```

Esperado: `upserts` trae hasta `limit` movimientos, `hasMore=true` si hay más de 200 en la cuenta,
`nextSince` es un timestamp usable para la siguiente llamada. Repitiendo la llamada con
`since=$nextSince` hasta que `hasMore=false`, la suma de todos los `upserts` recibidos coincide con el
total de movimientos del usuario.

### 2. Sincronización incremental trae solo lo nuevo (FR-003)

```bash
# Guardar el cursor tras sincronizar todo (paso 1)
LAST_CURSOR="<nextSince de la última página>"

curl -s -X POST http://localhost:8080/api/transactions -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"EXPENSE","amount":10,"date":"2026-07-23","accountId":"'"$ACCOUNT_ID"'"}'

curl -s "http://localhost:8080/api/transactions/sync?since=$LAST_CURSOR" -H "Authorization: Bearer $TOKEN" | jq '.upserts'
```

Esperado: `upserts` trae únicamente el movimiento recién creado, no la lista completa de nuevo.

### 3. Un borrado aparece en `deletedIds` (research.md #2)

```bash
curl -i -X DELETE http://localhost:8080/api/transactions/<id-del-paso-2> -H "Authorization: Bearer $TOKEN"

curl -s "http://localhost:8080/api/transactions/sync?since=$LAST_CURSOR" -H "Authorization: Bearer $TOKEN" | jq '.deletedIds'
```

Esperado: el id eliminado aparece en `deletedIds`.

## Escenarios de validación — Android (manual, en dispositivo)

### 4. La pantalla de movimientos abre al instante (User Story 1, SC-001)

Con la app ya usada antes (caché local ya poblada), abrir "mis movimientos" y medir a ojo el tiempo
hasta ver la lista. Esperado: aparece de inmediato (<1s), con scroll progresivo, sin spinner de carga
prolongado sin importar el volumen de movimientos de la cuenta.

### 5. Uso sin conexión (User Story 1/2, SC-002)

Activar modo avión, abrir la app y la pantalla de movimientos. Esperado: la lista ya sincronizada se ve
igual. Crear un movimiento nuevo: aparece en la lista de inmediato, sin error ni bloqueo.

### 6. Reconexión sincroniza lo pendiente (User Story 3, FR-007, SC-004)

Con el movimiento creado sin conexión del paso 5, desactivar el modo avión y esperar (o forzar sync).
Esperado: en poco tiempo, consultando `GET /api/transactions?accountId=$ACCOUNT_ID` desde curl, el
movimiento aparece también en el backend — sin duplicados si se repite la consulta.

### 7. Aislamiento entre usuarios (heredado de la feature 003)

Con un segundo usuario (`$TOKEN_2`):

```bash
curl -s "http://localhost:8080/api/transactions/sync" -H "Authorization: Bearer $TOKEN_2" | jq '.upserts | length'
```

Esperado: no incluye ningún movimiento del primer usuario.
