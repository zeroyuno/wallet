# Feature Specification: Gestión de cuentas y categorías

**Feature Branch**: `002-accounts-categories`

**Created**: 2026-07-18

**Status**: Draft

**Input**: User description: "Gestión de cuentas (efectivo, banco, tarjeta) y categorías personalizables de ingresos/gastos, para un usuario ya autenticado (feature 001)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crear y ver mis cuentas (Priority: P1)

Un usuario autenticado registra las cuentas donde guarda su dinero (efectivo, cuenta bancaria,
tarjeta de crédito, etc.) con un saldo inicial, para tener un punto de partida antes de registrar
movimientos.

**Why this priority**: Sin al menos una cuenta no hay dónde asociar ingresos/gastos en el futuro — es
la base de todo lo demás que se construya sobre esta feature.

**Independent Test**: Con un usuario autenticado, puede probarse creando una cuenta con nombre, tipo y
saldo inicial, y verificando que aparece al listar las cuentas de ese usuario.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado sin cuentas, **When** crea una cuenta con nombre, tipo, moneda y
   saldo inicial, **Then** la cuenta aparece en su lista de cuentas con esos datos.
2. **Given** un usuario con varias cuentas, **When** consulta su lista de cuentas, **Then** ve todas
   las que él mismo creó y ninguna de otro usuario.
3. **Given** un usuario autenticado, **When** intenta crear una cuenta sin nombre o con saldo inicial
   no numérico, **Then** el sistema rechaza la operación indicando el campo inválido.

---

### User Story 2 - Editar y eliminar mis cuentas (Priority: P2)

Un usuario corrige el nombre de una cuenta o la elimina cuando ya no la usa (por ejemplo, cerró una
tarjeta).

**Why this priority**: Es una mejora natural de la gestión (evita errores permanentes al crear), pero
la app ya es útil sin esto usando solo creación y listado — por eso P2.

**Independent Test**: Puede probarse editando el nombre de una cuenta existente y verificando el
cambio, y eliminando una cuenta y verificando que ya no aparece en el listado.

**Acceptance Scenarios**:

1. **Given** una cuenta existente del usuario, **When** edita su nombre o tipo, **Then** los cambios
   se reflejan al volver a consultarla.
2. **Given** una cuenta existente sin movimientos asociados, **When** el usuario la elimina, **Then**
   deja de aparecer en su lista de cuentas.
3. **Given** una cuenta de otro usuario, **When** intenta editarla o eliminarla, **Then** el sistema lo
   rechaza como si la cuenta no existiera.

---

### User Story 3 - Crear y gestionar categorías (Priority: P1)

Un usuario autenticado define categorías propias de ingreso o gasto (por ejemplo "Salario",
"Supermercado", "Transporte") para poder clasificar su dinero más adelante.

**Why this priority**: Igual que las cuentas, las categorías son un requisito base para cualquier
registro de movimientos futuro — sin ellas no hay forma de clasificar ingresos/gastos.

**Independent Test**: Con un usuario autenticado, puede probarse creando una categoría de tipo ingreso
o gasto y verificando que aparece en su lista de categorías.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado, **When** crea una categoría con nombre y tipo (ingreso o gasto),
   **Then** la categoría aparece en su lista de categorías de ese tipo.
2. **Given** un usuario con categorías propias, **When** las consulta, **Then** ve únicamente las que
   él creó.
3. **Given** un usuario autenticado, **When** intenta crear dos categorías con el mismo nombre y tipo,
   **Then** el sistema rechaza la segunda como duplicada.

---

### User Story 4 - Organizar categorías con subcategorías (Priority: P3)

Un usuario agrupa categorías relacionadas bajo una categoría padre (por ejemplo, "Comida" como padre
de "Supermercado" y "Restaurantes") para tener reportes más organizados en el futuro.

**Why this priority**: Aporta valor organizativo pero no es indispensable para que la gestión básica de
categorías funcione — por eso es P3 y puede llegar después.

**Independent Test**: Puede probarse asignando una categoría existente como padre de otra y verificando
que la relación se refleja al consultar la categoría hija.

**Acceptance Scenarios**:

1. **Given** dos categorías del mismo usuario y mismo tipo, **When** una se asigna como subcategoría de
   la otra, **Then** al consultar la subcategoría se ve su categoría padre.
2. **Given** una categoría, **When** el usuario intenta asignarla como su propia subcategoría (padre =
   sí misma) o crear un ciclo, **Then** el sistema lo rechaza.

---

### Edge Cases

- ¿Qué pasa si un usuario intenta eliminar una cuenta o categoría que ya tiene movimientos asociados en
  el futuro (fuera del alcance de esta feature)? Por ahora, sin movimientos implementados, la
  eliminación siempre es posible; el bloqueo por movimientos asociados se definirá en la feature de
  transacciones.
- ¿Qué pasa si el saldo inicial es negativo (por ejemplo, una tarjeta de crédito con deuda)? Debe
  permitirse — un saldo inicial negativo es un caso válido de negocio.
- ¿Qué pasa si dos usuarios distintos crean una cuenta o categoría con el mismo nombre? Debe permitirse
  — la unicidad de nombre de categoría (User Story 3) aplica solo dentro de las categorías del mismo
  usuario, no entre usuarios distintos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a un usuario autenticado crear una cuenta con nombre, tipo,
  moneda y saldo inicial.
- **FR-002**: El sistema DEBE listar únicamente las cuentas que pertenecen al usuario autenticado
  (constitución, principio IV).
- **FR-003**: El sistema DEBE permitir editar el nombre, tipo y moneda de una cuenta propia.
- **FR-004**: El sistema DEBE permitir eliminar una cuenta propia.
- **FR-005**: El sistema DEBE rechazar cualquier intento de ver, editar o eliminar una cuenta que no
  pertenece al usuario autenticado, sin distinguir si existe o no (404, no 403).
- **FR-006**: El sistema DEBE permitir a un usuario autenticado crear una categoría con nombre y tipo
  (ingreso o gasto).
- **FR-007**: El sistema DEBE rechazar la creación de una categoría con el mismo nombre y tipo que otra
  categoría ya existente del mismo usuario.
- **FR-008**: El sistema DEBE listar únicamente las categorías que pertenecen al usuario autenticado.
- **FR-009**: El sistema DEBE permitir editar y eliminar una categoría propia.
- **FR-010**: El sistema DEBE permitir asignar una categoría existente como subcategoría de otra
  categoría del mismo usuario y del mismo tipo (ingreso/gasto).
- **FR-011**: El sistema DEBE rechazar relaciones de subcategoría que formen un ciclo o que una
  categoría sea su propia subcategoría.
- **FR-012**: El sistema DEBE validar que el nombre de cuenta y de categoría no estén vacíos y que el
  saldo inicial de una cuenta sea un valor numérico.

### Key Entities

- **Cuenta (Account)**: representa un lugar donde el usuario guarda dinero. Atributos clave:
  identificador, nombre, tipo (efectivo, banco, tarjeta, otro), moneda, saldo inicial, usuario dueño.
- **Categoría (Category)**: representa una clasificación de ingreso o gasto. Atributos clave:
  identificador, nombre, tipo (ingreso/gasto), categoría padre (opcional), usuario dueño.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario puede crear su primera cuenta y su primera categoría en menos de 1 minuto
  combinado.
- **SC-002**: El 100% de las cuentas y categorías devueltas al listar pertenecen exclusivamente al
  usuario autenticado que hace la petición.
- **SC-003**: Un usuario puede editar o eliminar una cuenta o categoría propia en menos de 3 acciones
  (abrir, editar/eliminar, confirmar).

## Assumptions

- Esta feature NO incluye transacciones/movimientos (ingresos y gastos concretos) ni presupuestos ni
  reportes — quedan fuera de alcance para features futuras, según el alcance MVP elegido para el
  proyecto.
- No hay categorías predefinidas por el sistema en esta versión; cada usuario crea las suyas desde
  cero. Se documenta como posible mejora futura (no bloqueante).
- El saldo inicial de una cuenta es un valor fijo capturado al crearla; no se recalcula automáticamente
  hasta que exista la feature de transacciones.
- Requiere la feature 001 (Autenticación de usuario) ya implementada: toda operación de esta feature
  exige un usuario autenticado y usa su identidad para aislar los datos.
