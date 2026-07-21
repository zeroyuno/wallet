# Feature Specification: Importar datos desde BudgetBakers Wallet

**Feature Branch**: `005-budgetbakers-import`

**Created**: 2026-07-20

**Status**: Draft

**Input**: User description: "Importar datos desde BudgetBakers Wallet (API REST externa). Alcance mínimo viable: el usuario autenticado puede iniciar una importación de su historial desde Wallet hacia esta app, trayendo cuentas, categorías y transacciones (no presupuestos, no órdenes recurrentes, no metas de ahorro — eso queda para una fase posterior). Requiere que el usuario provea su API key/token de Wallet. Debe mapear cuentas, categorías (con jerarquía) y transacciones (monto con su propia moneda, fecha, tipo, cuenta, categoría opcional, nota, payee). Debe evitar duplicar datos si se corre la importación más de una vez. El usuario debe poder ver el progreso/resultado."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Importar mis cuentas y categorías (Priority: P1)

Un usuario que ya usaba BudgetBakers Wallet para trackear sus gastos ingresa su API key de Wallet y
trae sus cuentas y categorías existentes a esta app, para no tener que recrearlas a mano antes de
importar sus movimientos.

**Why this priority**: Es el prerrequisito de todo lo demás — las transacciones necesitan una cuenta
(y opcionalmente una categoría) propia ya existente para poder asociarse correctamente.

**Independent Test**: Con una API key válida de Wallet que tiene al menos una cuenta y una categoría
personalizada, puede probarse iniciando la importación y verificando que ambas aparecen en las
pantallas de "Mis cuentas" y "Mis categorías" de esta app.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado con una API key válida de Wallet, **When** inicia la importación,
   **Then** cada cuenta de Wallet aparece como una cuenta propia en esta app, con el mismo nombre y
   moneda.
2. **Given** categorías de Wallet con jerarquía (una categoría "hija" de otra), **When** se importan,
   **Then** esa misma relación padre/hijo se refleja en las categorías de esta app.
3. **Given** una cuenta o categoría de Wallet cuyo tipo no tiene equivalente exacto en esta app (por
   ejemplo, una cuenta de inversión o préstamo), **When** se importa, **Then** el sistema la trae
   igual usando el tipo más parecido disponible, sin rechazar la importación completa por eso.
4. **Given** una API key inválida o expirada, **When** el usuario intenta iniciar la importación,
   **Then** el sistema lo informa claramente sin iniciar ninguna importación parcial.

---

### User Story 2 - Importar mi historial de movimientos (Priority: P1)

El mismo usuario trae también su historial de transacciones (ingresos y gastos) desde Wallet, ya
asociadas a las cuentas y categorías recién importadas.

**Why this priority**: Es el valor central de la funcionalidad — sin el historial de movimientos, la
importación de cuentas y categorías por sí sola no resuelve el problema real del usuario (no repetir
a mano meses o años de carga de datos).

**Independent Test**: Con una cuenta ya importada (User Story 1) que tiene movimientos en Wallet, puede
probarse importando el historial y verificando que esos movimientos aparecen en "Mis movimientos" con
el monto, fecha, tipo y cuenta correctos.

**Acceptance Scenarios**:

1. **Given** movimientos existentes en una cuenta de Wallet ya importada, **When** se importa el
   historial, **Then** cada uno aparece como un movimiento propio en esta app, con el mismo monto
   (en la moneda original del movimiento), fecha y tipo (ingreso/gasto).
2. **Given** un movimiento de Wallet con categoría asignada, **When** se importa, **Then** queda
   asociado a la categoría correspondiente ya importada en esta app.
3. **Given** un movimiento de Wallet con "counterParty" (a quién se le pagó o quién pagó) y/o una nota,
   **When** se importa, **Then** esa información queda visible en la descripción del movimiento en
   esta app.
4. **Given** un movimiento de Wallet que es una transferencia entre dos cuentas propias del usuario,
   **When** se importa, **Then** se trae como un movimiento normal en cada cuenta involucrada (esta
   app no modela transferencias como un concepto separado todavía).
5. **Given** un historial con más movimientos de los que la API de Wallet permite traer en una sola
   tanda por sus límites de uso, **When** la importación se interrumpe por eso, **Then** el usuario
   puede volver a iniciarla más tarde y continúa donde quedó, sin volver a traer lo ya importado.

---

### User Story 3 - Ver el progreso y el resultado de la importación (Priority: P2)

El usuario puede ver, mientras la importación corre y una vez terminada, cuántas cuentas, categorías y
movimientos se importaron, y si algo falló.

**Why this priority**: Una importación de historiales grandes puede tardar y no ser instantánea — sin
visibilidad del progreso o del resultado, el usuario no puede confiar en que terminó bien ni saber qué
revisar si algo no vino como esperaba. No es P1 porque la importación en sí (US1/US2) ya entrega valor
aunque el usuario tenga que consultar el resultado recién al final.

**Independent Test**: Iniciando una importación con datos de prueba conocidos, puede verificarse que el
resumen final muestra cantidades que coinciden exactamente con lo que había en la cuenta de Wallet
usada para la prueba.

**Acceptance Scenarios**:

1. **Given** una importación en curso, **When** el usuario consulta su estado, **Then** ve si sigue en
   progreso, se completó, o se detuvo por un error.
2. **Given** una importación finalizada (completa o parcial), **When** el usuario ve el resultado,
   **Then** ve la cantidad de cuentas, categorías y movimientos importados, y una lista de errores si
   los hubo (por ejemplo, movimientos que no se pudieron traer por faltarles datos obligatorios).
3. **Given** una importación detenida por haber alcanzado el límite de uso de la API de Wallet,
   **When** el usuario ve el resultado, **Then** el sistema lo indica explícitamente como motivo de la
   interrupción (no como un error genérico), distinguiéndolo de un problema real.

---

### Edge Cases

- ¿Qué pasa si el usuario corre la importación dos veces (por ejemplo, para traer movimientos nuevos
  cargados en Wallet después de la primera vez)? El sistema no duplica lo ya importado — cada cuenta,
  categoría y movimiento importado antes se reconoce y se omite, solo se trae lo nuevo.
- ¿Qué pasa si una cuenta de Wallet ya fue borrada del lado de Wallet entre que se lista y se importa?
  El sistema continúa con el resto de la importación y reporta esa cuenta como no importada.
- ¿Qué pasa si un movimiento de Wallet no tiene cuenta asociada válida (por ejemplo, pertenece a una
  cuenta que no se pudo importar)? El sistema lo omite y lo cuenta como error en el resultado, en vez
  de interrumpir toda la importación.
- ¿Qué pasa si el usuario no tiene ninguna cuenta o movimiento en Wallet? La importación se completa
  igual, informando que no había nada para traer.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a un usuario autenticado iniciar una importación proveyendo su
  API key de Wallet, sin almacenarla de forma persistente más allá de lo necesario para completar esa
  importación.
- **FR-002**: El sistema DEBE traer las cuentas del usuario desde Wallet y crear una cuenta propia
  equivalente por cada una, preservando nombre y moneda; si el tipo de cuenta de Wallet no tiene
  equivalente exacto, DEBE usar el tipo más parecido disponible en vez de rechazar la cuenta.
- **FR-003**: El sistema DEBE traer las categorías del usuario desde Wallet (incluyendo su jerarquía
  padre/hijo) y crear una categoría propia equivalente por cada una, preservando nombre, tipo
  (ingreso/gasto) y relación con su categoría padre si la tiene.
- **FR-004**: El sistema DEBE traer los movimientos del usuario desde Wallet y crear un movimiento
  propio equivalente por cada uno, preservando monto (en la moneda original del movimiento), fecha,
  tipo (ingreso/gasto), cuenta asociada, y categoría asociada si la tiene.
- **FR-005**: El sistema DEBE incluir en la descripción del movimiento importado la información de
  "counterParty" (a quién se pagó o quién pagó) y la nota original de Wallet, si alguna de las dos
  está presente.
- **FR-006**: El sistema DEBE evitar crear cuentas, categorías o movimientos duplicados si la
  importación se corre más de una vez para el mismo usuario — cada entidad ya importada anteriormente
  se reconoce y se omite en corridas posteriores.
- **FR-007**: El sistema DEBE permitir al usuario consultar el estado de una importación (en progreso,
  completada, detenida) y, al finalizar, un resumen con la cantidad de cuentas, categorías y
  movimientos importados y una lista de los que no se pudieron importar con el motivo.
- **FR-008**: El sistema DEBE detener la importación de forma controlada (sin perder lo ya importado)
  si se alcanza el límite de uso de la API de Wallet, y DEBE permitir al usuario continuarla más tarde
  sin volver a traer lo ya importado.
- **FR-009**: El sistema DEBE rechazar el inicio de una importación si la API key de Wallet provista no
  es válida, informándolo antes de importar cualquier dato.
- **FR-010**: El sistema DEBE aislar los datos importados por el usuario dueño de la API key — una
  importación solo puede crear cuentas, categorías y movimientos para el usuario autenticado que la
  inició (constitución, principio IV).

### Key Entities

- **Importación (Import)**: representa una corrida (o continuación de una corrida anterior) del proceso
  de traer datos desde Wallet para un usuario. Atributos clave: usuario dueño, estado (en progreso,
  completada, detenida por límite de uso, con errores), cantidad de cuentas/categorías/movimientos
  importados, lista de errores, momento de inicio y de última actividad.
- **Referencia externa (identificador de Wallet)**: asociada a cada cuenta, categoría y movimiento
  importado, guarda el identificador que esa entidad tenía en Wallet — es lo que permite reconocer en
  una corrida posterior que ya fue importada (FR-006) y no se relaciona con ningún concepto visible
  para el usuario.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario con historial en Wallet puede tener sus cuentas, categorías y movimientos
  disponibles en esta app sin cargar manualmente ninguno de ellos.
- **SC-002**: El 100% de los movimientos importados conservan el mismo monto y fecha que tenían en
  Wallet.
- **SC-003**: Correr la importación dos veces seguidas sobre los mismos datos de origen no produce
  ninguna cuenta, categoría o movimiento duplicado.
- **SC-004**: Un usuario puede saber, sin ambigüedad, cuántos elementos se importaron y cuántos
  fallaron (y por qué) al finalizar una importación.

## Assumptions

- La API key de Wallet la obtiene el usuario por su cuenta desde su propia cuenta de BudgetBakers
  Wallet; esta spec no cubre cómo generarla, solo cómo usarla para importar.
- No se persiste la API key de Wallet más allá de la importación en curso — cada corrida requiere que
  el usuario la vuelva a proveer. Se prioriza no guardar credenciales de un tercero en reposo sobre la
  comodidad de no tener que reingresarla; se puede reconsiderar en una fase posterior si se vuelve una
  fricción real.
- Alcance mínimo viable: solo cuentas, categorías y movimientos. Presupuestos, órdenes recurrentes,
  metas de ahorro, labels/etiquetas, fotos adjuntas y ubicación de Wallet quedan fuera de esta spec —
  son features nuevas en sí mismas, no solo migración de datos existente.
- Las transferencias entre cuentas propias en Wallet se importan como dos movimientos independientes
  (uno por cuenta), ya que esta app todavía no modela transferencias como un concepto propio separado
  de un ingreso/gasto común.
- La importación es un proceso que puede tardar y necesitar más de una corrida (por los límites de uso
  de la API de Wallet) — no se espera que el usuario reciba el resultado de forma instantánea para
  historiales grandes.
- Requiere la feature 001 (autenticación), 002 (cuentas y categorías) y 003 (transacciones) ya
  implementadas, ya que esta importación crea datos usando esas mismas capacidades existentes.
