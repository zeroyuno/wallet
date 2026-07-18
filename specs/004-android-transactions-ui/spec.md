# Feature Specification: Interfaz Android para transacciones

**Feature Branch**: `004-android-transactions-ui`

**Created**: 2026-07-18

**Status**: Draft

**Input**: User description: "Interfaz Android para transacciones (ingresos y gastos). Consume la API REST ya implementada en la feature 003 (specs/003-transactions). Los usuarios autenticados pueden, desde la app Android: registrar un movimiento (ingreso o gasto) eligiendo cuenta, tipo, monto, fecha, categoría opcional y descripción opcional; ver la lista de sus movimientos; ver el saldo actual de cada cuenta (reflejando sus transacciones) en la pantalla de cuentas; editar y eliminar un movimiento propio; filtrar la lista de movimientos por cuenta, categoría y rango de fechas. No incluye soporte offline en esta ronda ni gráficos/reportes — solo el CRUD de movimientos y ver el saldo actualizado."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un movimiento y ver mi lista (Priority: P1)

Un usuario autenticado, desde la app Android, registra un ingreso o un gasto (cuenta, tipo, monto,
fecha y, opcionalmente, categoría y descripción) y lo ve reflejado inmediatamente en su lista de
movimientos.

**Why this priority**: Es el propósito central de la app — sin poder registrar movimientos desde el
celular, la API de transacciones (feature 003) no tiene ningún uso real para el usuario final.

**Independent Test**: Con la app instalada y una sesión iniciada con al menos una cuenta propia, puede
probarse registrando un gasto y un ingreso desde la pantalla de nuevo movimiento y verificando que
ambos aparecen en la lista de movimientos.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado con al menos una cuenta propia, **When** abre la pantalla de nuevo
   movimiento, completa monto, fecha, tipo y cuenta, y confirma, **Then** el movimiento aparece en la
   lista de movimientos y la app vuelve a la lista.
2. **Given** un usuario que está completando un nuevo movimiento, **When** dejar la categoría o la
   descripción vacías, **Then** la app permite confirmar igual, sin exigir esos campos.
3. **Given** un usuario que está completando un nuevo movimiento, **When** intenta confirmar con monto
   vacío, no numérico, cero o negativo, sin fecha, o sin cuenta seleccionada, **Then** la app se lo
   impide y muestra qué campo corregir, sin llegar a llamar al servidor.
4. **Given** un usuario con varios movimientos ya registrados, **When** abre la lista de movimientos,
   **Then** ve cada uno con al menos su tipo, monto, fecha y cuenta, distinguiendo visualmente ingresos
   de gastos.
5. **Given** un error de red o del servidor al intentar registrar un movimiento, **When** ocurre ese
   error, **Then** la app muestra un mensaje de error y el usuario permanece en la pantalla de nuevo
   movimiento sin perder lo que ya había completado.

---

### User Story 2 - Ver el saldo actualizado de mis cuentas (Priority: P1)

Un usuario autenticado ve, en la pantalla de cuentas, el saldo actual de cada cuenta — reflejando el
saldo inicial más el efecto de todos sus movimientos — en vez de únicamente el saldo inicial con el
que fue creada.

**Why this priority**: Sin este cambio, la pantalla de cuentas (feature 002) queda desactualizada en
cuanto el usuario registra su primer movimiento, mostrando un dato incorrecto — es tan crítico para
que la app sea útil como poder registrar el movimiento en sí, por eso comparte prioridad P1 con la
historia 1.

**Independent Test**: Con una cuenta que tiene un saldo inicial conocido, puede probarse registrando un
ingreso y un gasto de montos distintos y verificando que el saldo mostrado en la pantalla de cuentas
pasa a reflejar la suma correcta, no el saldo inicial original.

**Acceptance Scenarios**:

1. **Given** una cuenta sin movimientos todavía, **When** el usuario abre la pantalla de cuentas,
   **Then** el saldo mostrado coincide con el saldo inicial de esa cuenta.
2. **Given** una cuenta con uno o más movimientos, **When** el usuario abre la pantalla de cuentas,
   **Then** el saldo mostrado refleja el saldo inicial más el efecto acumulado de esos movimientos, no
   únicamente el saldo inicial.
3. **Given** el usuario acaba de registrar, editar o eliminar un movimiento de una cuenta, **When**
   vuelve a la pantalla de cuentas, **Then** el saldo mostrado para esa cuenta ya refleja ese cambio.

---

### User Story 3 - Editar y eliminar mis movimientos (Priority: P2)

Un usuario corrige un movimiento cargado con datos equivocados o lo elimina si lo registró por error,
desde la propia lista de movimientos.

**Why this priority**: Es una corrección natural sobre la funcionalidad base (US1/US2) — la app ya es
útil solo con registrar y ver el saldo actualizado, por eso es P2.

**Independent Test**: Con un movimiento ya registrado, puede probarse editando su monto y verificando
que la lista y el saldo de la cuenta se actualizan, y eliminándolo y verificando que desaparece de la
lista y el saldo vuelve a su estado previo.

**Acceptance Scenarios**:

1. **Given** un movimiento propio existente, **When** el usuario lo abre para editar y cambia su monto,
   fecha, categoría o descripción, y confirma, **Then** los cambios se reflejan en la lista y el saldo
   de la cuenta asociada se actualiza en consecuencia.
2. **Given** un movimiento propio existente, **When** el usuario elige eliminarlo y confirma, **Then**
   deja de aparecer en la lista y el saldo de la cuenta asociada vuelve a su estado previo a ese
   movimiento.
3. **Given** un usuario a punto de eliminar un movimiento, **When** toca eliminar, **Then** la app pide
   confirmación antes de eliminarlo definitivamente.
4. **Given** un error de red o del servidor al editar o eliminar, **When** ocurre ese error, **Then** la
   app muestra un mensaje de error y el movimiento permanece sin cambios tanto en la lista como en el
   servidor.

---

### User Story 4 - Filtrar mi lista de movimientos (Priority: P3)

Un usuario con muchos movimientos acumulados filtra su lista por cuenta, por categoría o por un rango
de fechas, para encontrar más rápido lo que busca.

**Why this priority**: Mejora la usabilidad sobre una lista que puede crecer mucho, pero la app ya es
funcional sin filtros (mostrando todo) — por eso es P3, igual que en la feature 003 del backend.

**Independent Test**: Con varios movimientos ya registrados sobre distintas cuentas, categorías y
fechas, puede probarse aplicando cada filtro (por separado y combinados) desde la lista de movimientos
y verificando que solo se muestran los que cumplen el filtro.

**Acceptance Scenarios**:

1. **Given** movimientos en más de una cuenta propia, **When** el usuario filtra la lista por una
   cuenta específica, **Then** ve únicamente los movimientos de esa cuenta.
2. **Given** movimientos con distintas categorías, **When** el usuario filtra por una categoría,
   **Then** ve únicamente los movimientos con esa categoría.
3. **Given** movimientos en distintas fechas, **When** el usuario filtra por un rango de fechas,
   **Then** ve únicamente los movimientos cuya fecha cae dentro de ese rango.
4. **Given** el usuario tiene uno o más filtros activos, **When** los quita, **Then** vuelve a ver la
   lista completa de sus movimientos.

---

### Edge Cases

- ¿Qué pasa si el usuario intenta registrar un movimiento sin tener ninguna cuenta creada todavía? La
  app se lo indica y lo dirige a crear una cuenta primero, en vez de mostrar un formulario sin ninguna
  cuenta para elegir.
- ¿Qué pasa si, al abrir el formulario de nuevo movimiento, el usuario todavía no tiene ninguna
  categoría del tipo elegido (ingreso/gasto)? La app permite continuar igual sin categoría, ya que es
  opcional.
- ¿Qué pasa si el usuario intenta editar o eliminar un movimiento que ya fue eliminado o modificado
  desde otra sesión mientras tanto? La app muestra un mensaje de error y refresca la lista para reflejar
  el estado real.
- ¿Qué pasa si la app pierde la conexión mientras se está cargando la lista de movimientos o el saldo
  de una cuenta? La app muestra un estado de error con opción de reintentar, sin dejar la pantalla en
  blanco ni en un estado de carga indefinido.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La app DEBE permitir a un usuario autenticado registrar un movimiento nuevo indicando
  tipo (ingreso o gasto), monto, fecha y una cuenta propia, con categoría y descripción opcionales.
- **FR-002**: La app DEBE validar en el propio formulario (antes de llamar al servidor) que el monto
  sea numérico y mayor que cero, que haya una fecha y que haya una cuenta seleccionada, mostrando al
  usuario qué corregir.
- **FR-003**: La app DEBE mostrar la lista de movimientos del usuario autenticado, con al menos tipo,
  monto, fecha y cuenta visibles por movimiento, distinguiendo visualmente ingresos de gastos.
- **FR-004**: La app DEBE permitir filtrar la lista de movimientos por cuenta, por categoría y por
  rango de fechas, de forma independiente o combinada, y permitir quitar los filtros aplicados.
- **FR-005**: La app DEBE permitir editar el monto, la fecha, la categoría y la descripción de un
  movimiento propio existente.
- **FR-006**: La app DEBE permitir eliminar un movimiento propio existente, pidiendo confirmación antes
  de eliminarlo.
- **FR-007**: La app DEBE mostrar, en la pantalla de cuentas, el saldo actual de cada cuenta (saldo
  inicial más el efecto de sus movimientos) en vez de únicamente el saldo inicial con el que fue
  creada.
- **FR-008**: La app DEBE reflejar en la lista de movimientos y en el saldo de cuentas, sin necesidad
  de reiniciar la app, los cambios hechos al registrar, editar o eliminar un movimiento.
- **FR-009**: La app DEBE mostrar un mensaje de error comprensible (sin datos técnicos) cuando el
  registro, edición, eliminación o carga de movimientos falla por un error de red o del servidor,
  dejando al usuario en condiciones de reintentar.
- **FR-010**: La app DEBE impedir que el usuario intente registrar un movimiento si no tiene ninguna
  cuenta propia creada, dirigiéndolo a crear una primero.

### Key Entities

- **Movimiento (Transaction)**: representa, desde la perspectiva de la app, un ingreso o gasto ya
  registrado o en edición. Atributos visibles: tipo, monto, fecha, descripción (opcional), cuenta,
  categoría (opcional). Corresponde uno a uno con la entidad `Transacción` ya definida en la feature
  003 (specs/003-transactions/spec.md).
- **Saldo de cuenta (Account balance)**: el saldo actual de una cuenta tal como se muestra en la
  pantalla de cuentas, calculado por el servidor (feature 003) a partir del saldo inicial de la cuenta
  y sus movimientos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario puede registrar un movimiento nuevo desde que abre la app en menos de 30
  segundos.
- **SC-002**: El saldo que ve un usuario en la pantalla de cuentas coincide exactamente, en el 100% de
  los casos, con el saldo que devuelve el servidor para esa cuenta en ese momento.
- **SC-003**: Un usuario puede encontrar un movimiento específico entre varias decenas filtrando por
  cuenta, categoría o fecha, sin tener que revisar la lista completa manualmente.
- **SC-004**: El 100% de los intentos fallidos de registrar, editar o eliminar un movimiento (por error
  de red o del servidor) le muestran al usuario un mensaje de error, nunca un fallo silencioso donde la
  app actúa como si hubiera funcionado.

## Assumptions

- Requiere la feature 003 (Transacciones, backend) ya implementada y desplegada, cuya API REST se
  consume tal cual (`specs/003-transactions/contracts/transactions-api.yaml`), incluyendo el endpoint
  de saldo de cuenta.
- Requiere las features 001 (Autenticación) y 002 (Cuentas y categorías) ya implementadas en Android,
  reutilizando la sesión y las pantallas de cuentas/categorías existentes.
- Sigue el mismo patrón arquitectónico (MVVM: pantalla → ViewModel → repositorio → cliente HTTP) y las
  mismas convenciones de UI ya establecidas en las pantallas de cuentas y categorías de la app.
- No incluye soporte para creación/edición de movimientos sin conexión en esta ronda. La feature 003 ya
  soporta que el cliente indique el identificador del movimiento al crearlo, pensando en esa capacidad
  offline futura, pero la lógica de guardado local y sincronización queda fuera de alcance aquí — cada
  operación requiere conexión al servidor en el momento de realizarse.
- No incluye gráficos, reportes ni resúmenes agregados (por ejemplo, gastos por categoría en un
  período) — solo el CRUD de movimientos y la visualización del saldo actualizado por cuenta.
- No hay paginación en la lista de movimientos en esta versión, igual que en el backend (feature 003);
  se documenta como posible mejora futura si el volumen de datos lo justifica.
- El formulario de nuevo movimiento/edición reutiliza las cuentas y categorías propias del usuario ya
  existentes (creadas vía la feature 002); no se ofrece crear una cuenta o categoría nueva desde ese
  mismo formulario.
