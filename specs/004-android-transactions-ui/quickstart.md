# Quickstart: Interfaz Android para transacciones

Guía para validar la feature de extremo a extremo una vez implementada, en un dispositivo físico
Android sobre la misma red que el backend.

## Prerrequisitos

- `docker compose up -d` y backend corriendo (`./mvnw spring-boot:run`), features 001/002/003 ya
  desplegadas.
- App Android instalada en un dispositivo físico en la misma red Wi-Fi que el backend (mismo `BASE_URL`
  de `NetworkModule.kt`).
- Un usuario con sesión iniciada, con al menos una cuenta propia y, opcionalmente, alguna categoría de
  cada tipo (ingreso/gasto).

## Escenarios de validación

### 1. Registrar un movimiento y verlo en la lista (User Story 1)

1. Desde Home, abrir "Mis movimientos" → botón "+".
2. Completar tipo (gasto), monto, fecha, cuenta, dejar categoría y descripción vacías. Confirmar.

Esperado: vuelve a la lista y el movimiento nuevo aparece con los datos cargados.

### 2. Ver el saldo actualizado en la pantalla de cuentas (User Story 2)

1. Anotar el saldo mostrado para la cuenta usada en el paso anterior antes de registrar el movimiento.
2. Volver a "Mis cuentas" después de registrarlo.

Esperado: el saldo mostrado ya no es el `initialBalance` original — refleja el descuento del gasto
recién registrado, coincidiendo con `GET /api/accounts/{id}/balance`.

### 3. Editar un movimiento y verificar el saldo (User Story 3)

1. Desde la lista de movimientos, tocar el movimiento creado y cambiar su monto.
2. Confirmar y volver a "Mis cuentas".

Esperado: la lista muestra el monto nuevo; el saldo de la cuenta refleja únicamente el monto
actualizado (no la suma de ambos).

### 4. Eliminar un movimiento (User Story 3)

1. Desde el formulario de edición del movimiento, tocar eliminar y confirmar en el diálogo.
2. Volver a "Mis cuentas".

Esperado: el movimiento ya no aparece en la lista; el saldo de la cuenta vuelve a su valor previo al
primer escenario.

### 5. Filtrar la lista de movimientos (User Story 4)

Con al menos dos movimientos en cuentas o fechas distintas:

1. Aplicar el filtro de cuenta (chip) y verificar que solo aparecen los de esa cuenta.
2. Quitar ese filtro y aplicar un rango de fechas que excluya alguno de los movimientos.

Esperado: en cada paso, la lista muestra únicamente los movimientos que cumplen el filtro activo.

### 6. Manejo de error de red (edge case, FR-009)

1. Desactivar temporalmente el Wi-Fi del dispositivo (o detener el backend).
2. Intentar registrar un movimiento.

Esperado: la app muestra un mensaje de error y permanece en el formulario con los datos ya cargados,
sin navegar como si hubiera funcionado (mismo patrón `Result<Unit>` ya usado en cuentas/categorías).

### 7. Sin cuentas propias (edge case, FR-010)

Con un usuario recién registrado sin ninguna cuenta:

1. Intentar abrir el formulario de nuevo movimiento.

Esperado: la app indica que primero hay que crear una cuenta, sin mostrar un formulario con el selector
de cuenta vacío.
