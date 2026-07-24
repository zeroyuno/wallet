# Quickstart: Modernizar la interfaz con el sistema de diseño Midnight FinTech

Guía para validar la feature una vez implementada. Es una feature visual/de navegación — la validación
real es manual en dispositivo (no hay `adb` en el entorno del agente, ver Notas de la feature 007).

## Prerrequisitos

- App instalada en un dispositivo/emulador Android con sesión ya iniciada y datos de prueba (al menos
  2 cuentas, algunas categorías, y varios movimientos con montos largos y descripciones largas —
  para volver a estresar el caso que rompió el layout en la feature 007).

## Validación automatizada

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

Esperado: todos los tests en verde, incluyendo el nuevo `BottomNavTabTest` (mapeo `Screen` → pestaña).

## Escenarios de validación manual (en dispositivo)

### 1. Barra de navegación inferior (User Story 1)

Con sesión iniciada, tocar cada uno de los 3 accesos (Cuentas, Movimientos, Categorías) desde
cualquiera de los otros dos. Esperado: la app cambia de sección y el ícono/label activo queda resaltado
visualmente. Abrir el formulario de alta de un movimiento: la barra de navegación debe desaparecer;
volver atrás: la barra reaparece.

### 2. Estilo visual — lista de movimientos (User Story 2)

Abrir "Movimientos" con datos de prueba que incluyan descripciones largas y montos con decimales.
Esperado: tarjetas oscuras, ícono circular verde/rojo según ingreso/gasto, tipografía Inter, ningún
texto cortado de forma rara ni monto partido en varias líneas (regresión de la feature 007).

### 3. Estilo visual — cuentas y categorías

Abrir "Cuentas": tarjetas con ícono según tipo de cuenta, saldo alineado a la derecha, sin botones de
"Transferir"/"Recibir" (no hay esa funcionalidad). Abrir "Categorías": agrupadas por tipo
(ingreso/gasto) como ya lo hace hoy, con el nuevo estilo de tarjeta.

### 4. Estilo visual — formulario de movimiento

Abrir el formulario de alta: selector de tipo (ingreso/gasto), campos con el nuevo estilo, botón de
guardar con el color primario del sistema de diseño. Editar un movimiento existente: debe verse el
botón de eliminar, con el mismo estilo ya validado en la feature 007 (sin romper layout).

### 5. Estilo visual — login

Abrir la pantalla de login: nuevo estilo visual, sin botones de "Google"/"Apple ID" (no hay login
social implementado). Verificar que el flujo de login real (email + contraseña) sigue funcionando
igual que antes.

### 6. Nada se ve roto con letra grande (accesibilidad, edge case de spec.md)

Con la configuración de accesibilidad del dispositivo en tamaño de fuente grande, repetir el escenario
2 (lista de movimientos) — ninguna tarjeta debe recortar texto de forma abrupta ni romper el layout.
