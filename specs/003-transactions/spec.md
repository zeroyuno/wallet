# Feature Specification: Transacciones (ingresos y gastos)

**Feature Branch**: `003-transactions`

**Created**: 2026-07-18

**Status**: Draft

**Input**: User description: "Transacciones (ingresos y gastos). Los usuarios registran movimientos de dinero (ingreso o gasto) asociados a una cuenta y opcionalmente a una categoría, con monto, fecha, descripción opcional. Cada transacción de tipo gasto/ingreso afecta el saldo de la cuenta asociada. El usuario puede listar sus transacciones (con filtros básicos por cuenta, categoría y rango de fechas), editarlas y eliminarlas. Todo transaccional está aislado por usuario igual que cuentas y categorías. Para esta ronda solo se implementará el backend (API REST) — la UI de Android queda para una spec/implementación posterior."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar y ver mis movimientos (Priority: P1)

Un usuario autenticado registra un ingreso o un gasto concreto (monto, fecha, cuenta y,
opcionalmente, una categoría y una descripción), y puede volver a consultarlo junto con el resto de
sus movimientos.

**Why this priority**: Es el propósito central de toda la app — sin poder registrar movimientos, las
cuentas y categorías construidas en la feature anterior no tienen ningún uso real.

**Independent Test**: Con un usuario autenticado que ya tiene al menos una cuenta, puede probarse
registrando un ingreso y un gasto y verificando que ambos aparecen al listar sus movimientos, con el
saldo de la cuenta actualizado en consecuencia.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado con una cuenta propia, **When** registra un gasto con monto,
   fecha y esa cuenta, **Then** el movimiento aparece en su lista de transacciones y el saldo de la
   cuenta disminuye en ese monto.
2. **Given** un usuario autenticado con una cuenta propia, **When** registra un ingreso con monto,
   fecha y esa cuenta, **Then** el movimiento aparece en su lista de transacciones y el saldo de la
   cuenta aumenta en ese monto.
3. **Given** un usuario autenticado, **When** registra un movimiento sin indicar categoría, **Then**
   el sistema lo acepta igual, sin categoría asociada.
4. **Given** un usuario autenticado, **When** intenta registrar un movimiento sobre una cuenta que no
   es suya, o con monto no numérico, no positivo, o sin fecha, **Then** el sistema rechaza la
   operación indicando el campo inválido.
5. **Given** un usuario con varios movimientos propios, **When** consulta su lista de transacciones,
   **Then** ve únicamente los movimientos que él mismo registró.

---

### User Story 2 - Editar y eliminar mis movimientos (Priority: P2)

Un usuario corrige un movimiento cargado con datos equivocados (monto, fecha, categoría o
descripción) o lo elimina si lo registró por error, y en ambos casos el saldo de la cuenta afectada
queda correcto.

**Why this priority**: Es una corrección natural sobre la funcionalidad base (US1) — la app ya es
útil solo con registrar y listar, por eso es P2 y no P1.

**Independent Test**: Puede probarse editando el monto de un movimiento existente y verificando que
el saldo de la cuenta se ajusta al nuevo valor, y eliminando un movimiento y verificando que el saldo
vuelve a su estado previo a ese movimiento.

**Acceptance Scenarios**:

1. **Given** un movimiento propio existente, **When** el usuario edita su monto, fecha, categoría o
   descripción, **Then** los cambios se reflejan al volver a consultarlo y el saldo de la cuenta
   refleja únicamente el monto actualizado (no el original más el nuevo).
2. **Given** un movimiento propio existente, **When** el usuario lo elimina, **Then** deja de
   aparecer en su lista de transacciones y el saldo de la cuenta se ajusta como si ese movimiento
   nunca hubiera existido.
3. **Given** un movimiento de otro usuario, **When** intenta editarlo o eliminarlo, **Then** el
   sistema lo rechaza como si el movimiento no existiera.

---

### User Story 3 - Filtrar mis movimientos (Priority: P3)

Un usuario con muchos movimientos acumulados filtra su lista por cuenta, por categoría o por un rango
de fechas, para encontrar más rápido lo que busca.

**Why this priority**: Mejora la usabilidad sobre una lista que puede crecer mucho, pero el sistema
ya es funcional sin filtros (listando todo) — por eso es P3.

**Independent Test**: Con varios movimientos ya registrados sobre distintas cuentas, categorías y
fechas, puede probarse filtrando por cada criterio (por separado y combinados) y verificando que solo
se devuelven los movimientos que cumplen el filtro.

**Acceptance Scenarios**:

1. **Given** movimientos en más de una cuenta propia, **When** el usuario filtra por una cuenta
   específica, **Then** ve únicamente los movimientos de esa cuenta.
2. **Given** movimientos con distintas categorías, **When** el usuario filtra por una categoría,
   **Then** ve únicamente los movimientos con esa categoría.
3. **Given** movimientos en distintas fechas, **When** el usuario filtra por un rango de fechas,
   **Then** ve únicamente los movimientos cuya fecha cae dentro de ese rango.

---

### Edge Cases

- ¿Qué pasa si se intenta eliminar una cuenta o categoría que ya tiene transacciones asociadas? El
  sistema lo rechaza (igual que ya ocurre hoy al eliminar una categoría con subcategorías) — primero
  hay que eliminar o reasignar las transacciones asociadas.
- ¿Qué pasa si la categoría indicada en una transacción es de un tipo distinto al del movimiento (por
  ejemplo, un gasto con una categoría de ingreso)? El sistema lo rechaza.
- ¿Qué pasa si el monto es cero? El sistema lo rechaza — un movimiento debe representar un monto
  positivo; el signo (suma o resta del saldo) lo determina el tipo (ingreso/gasto), no el monto.
- ¿Qué pasa si la categoría indicada no existe o pertenece a otro usuario? El sistema lo rechaza igual
  que un intento de usar una cuenta ajena.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a un usuario autenticado registrar una transacción con tipo
  (ingreso o gasto), monto, fecha y una cuenta propia; la categoría y la descripción son opcionales.
- **FR-002**: El sistema DEBE aumentar el saldo de la cuenta asociada cuando se registra un ingreso, y
  disminuirlo cuando se registra un gasto, en el momento de la creación.
- **FR-003**: El sistema DEBE listar únicamente las transacciones que pertenecen al usuario
  autenticado (constitución, principio IV).
- **FR-004**: El sistema DEBE permitir filtrar la lista de transacciones del usuario por cuenta, por
  categoría y por rango de fechas, de forma independiente o combinada.
- **FR-005**: El sistema DEBE permitir editar el monto, la fecha, la categoría y la descripción de una
  transacción propia, ajustando el saldo de la cuenta para reflejar únicamente el valor actualizado.
- **FR-006**: El sistema DEBE permitir eliminar una transacción propia, revirtiendo su efecto sobre el
  saldo de la cuenta asociada.
- **FR-007**: El sistema DEBE rechazar cualquier intento de ver, editar o eliminar una transacción que
  no pertenece al usuario autenticado, sin distinguir si existe o no (404, no 403).
- **FR-008**: El sistema DEBE rechazar una transacción cuya cuenta no pertenezca al usuario
  autenticado, o cuya categoría (si se indica) no pertenezca al usuario autenticado o no coincida en
  tipo (ingreso/gasto) con la transacción.
- **FR-009**: El sistema DEBE validar que el monto de una transacción sea numérico y mayor que cero, y
  que la fecha esté presente.
- **FR-010**: El sistema DEBE rechazar la eliminación de una cuenta o de una categoría que todavía
  tiene transacciones asociadas.
- **FR-011**: El sistema DEBE permitir que el cliente indique el identificador de una transacción al
  crearla; si no lo indica, el sistema genera uno. El identificador DEBE ser no adivinable
  (no secuencial) y temporalmente ordenable. Si el identificador indicado ya existe, el sistema DEBE
  rechazar la creación como duplicada en vez de sobrescribir la transacción existente.

### Key Entities

- **Transacción (Transaction)**: representa un movimiento de dinero concreto. Atributos clave:
  identificador (puede originarse en el cliente, FR-011), tipo (ingreso/gasto), monto, fecha,
  descripción (opcional), cuenta asociada, categoría asociada (opcional), usuario dueño.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario puede registrar un movimiento nuevo en menos de 30 segundos.
- **SC-002**: El saldo de una cuenta calculado a partir de su saldo inicial y sus transacciones
  coincide exactamente con el saldo reflejado por el sistema en el 100% de los casos, incluso tras
  ediciones y eliminaciones.
- **SC-003**: El 100% de las transacciones devueltas al listar o filtrar pertenecen exclusivamente al
  usuario autenticado que hace la petición.
- **SC-004**: Un usuario puede encontrar un movimiento específico entre cientos filtrando por cuenta,
  categoría o fecha, sin tener que revisar la lista completa.

## Assumptions

- Esta ronda de implementación cubre únicamente el backend (API REST); la interfaz de Android para
  registrar/ver transacciones queda para una spec o fase de implementación posterior, igual que ya
  ocurrió con la API de cuentas y categorías respecto de su UI.
- Una transacción tiene un tipo propio (ingreso/gasto) independiente de su categoría, ya que la
  categoría es opcional — no se infiere el tipo a partir de la categoría.
- El monto de una transacción se expresa implícitamente en la moneda de la cuenta asociada; no se
  maneja conversión de moneda ni un campo de moneda propio en la transacción.
- No hay paginación en el listado de transacciones en esta versión (se listan todas las que cumplen el
  filtro); se documenta como posible mejora futura si el volumen de datos lo justifica.
- Requiere las features 001 (Autenticación) y 002 (Cuentas y categorías) ya implementadas: toda
  transacción exige un usuario autenticado y una cuenta propia existente, y usa las reglas de
  aislamiento por usuario ya establecidas.
- FR-011 (identificador originado en el cliente) anticipa una futura app Android con capacidad
  offline: un movimiento cargado sin conexión necesita un identificador único generado localmente
  para poder guardarse antes de sincronizar, sin depender de que el servidor se lo asigne primero ni
  arriesgar colisiones al sincronizar más tarde. No se implementa la sincronización offline en sí en
  esta ronda — solo el soporte del backend para aceptar ese identificador.
