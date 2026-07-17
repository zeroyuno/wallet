# Feature Specification: Autenticación de usuario

**Feature Branch**: `001-user-auth`

**Created**: 2026-07-17

**Status**: Draft

**Input**: User description: "Registro e inicio de sesión de usuarios (JWT) como prerrequisito de toda la app multi-usuario de ingresos y gastos."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registro de nueva cuenta (Priority: P1)

Una persona nueva descarga la app y crea su cuenta con email y contraseña para poder empezar a
registrar sus cuentas y categorías de ingresos/gastos.

**Why this priority**: Sin registro no existe usuario, y sin usuario no hay datos que aislar — es la
base absoluta de la que depende todo lo demás (constitución, principio IV: aislamiento por usuario).

**Independent Test**: Puede probarse de forma aislada enviando un email y contraseña válidos al
endpoint de registro y verificando que se crea la cuenta y se puede iniciar sesión con esas
credenciales inmediatamente después.

**Acceptance Scenarios**:

1. **Given** un email no registrado previamente, **When** el usuario se registra con email y
   contraseña válidos, **Then** la cuenta se crea y el usuario puede iniciar sesión con esas mismas
   credenciales.
2. **Given** un email ya registrado, **When** alguien intenta registrarse de nuevo con ese email,
   **Then** el sistema rechaza el registro indicando que el email ya está en uso.
3. **Given** una contraseña que no cumple la política mínima de seguridad, **When** el usuario intenta
   registrarse, **Then** el sistema rechaza el registro explicando el requisito no cumplido.

---

### User Story 2 - Inicio de sesión (Priority: P1)

Un usuario ya registrado abre la app e inicia sesión con su email y contraseña para acceder a sus
datos financieros personales.

**Why this priority**: Es la puerta de entrada a cualquier uso recurrente de la app; sin login no hay
forma de volver a acceder a los datos ya creados.

**Independent Test**: Puede probarse de forma aislada usando credenciales de una cuenta ya existente
y verificando que se recibe un token válido que permite acceder a endpoints protegidos.

**Acceptance Scenarios**:

1. **Given** una cuenta existente y credenciales correctas, **When** el usuario inicia sesión,
   **Then** recibe un token de acceso válido y puede consultar endpoints protegidos con él.
2. **Given** una cuenta existente y una contraseña incorrecta, **When** el usuario intenta iniciar
   sesión, **Then** el sistema rechaza el intento sin revelar si el email existe o no.
3. **Given** un email que no corresponde a ninguna cuenta, **When** se intenta iniciar sesión,
   **Then** el sistema responde con el mismo mensaje genérico de credenciales inválidas (sin filtrar
   qué campo era incorrecto).

---

### User Story 3 - Sesión persistente y cierre de sesión (Priority: P2)

Un usuario que ya inició sesión permanece autenticado entre usos de la app sin tener que volver a
escribir su contraseña cada vez, y puede cerrar sesión explícitamente cuando lo desee (por ejemplo,
antes de prestar el teléfono).

**Why this priority**: Mejora sustancialmente la experiencia de uso diario, pero la app es utilizable
sin esto (con login repetido) — por eso es P2 y no P1.

**Independent Test**: Puede probarse verificando que un token emitido sigue siendo válido en peticiones
posteriores dentro de su periodo de vigencia, y que tras cerrar sesión el token deja de ser aceptado.

**Acceptance Scenarios**:

1. **Given** un usuario con sesión iniciada, **When** vuelve a abrir la app antes de que el token
   expire, **Then** no se le pide iniciar sesión de nuevo.
2. **Given** un usuario con sesión iniciada, **When** cierra sesión explícitamente, **Then** el token
   usado deja de ser válido para acceder a endpoints protegidos.
3. **Given** un token expirado, **When** se usa para acceder a un endpoint protegido, **Then** el
   sistema lo rechaza y el usuario debe iniciar sesión de nuevo.

---

### Edge Cases

- ¿Qué pasa si el usuario intenta registrarse con un email mal formado? El sistema debe rechazarlo con
  un mensaje de validación claro, sin llegar a crear la cuenta.
- ¿Qué pasa si alguien intenta fuerza bruta contra el login? El sistema debe limitar/ralentizar
  intentos repetidos fallidos sobre el mismo email para mitigar ataques de fuerza bruta.
- ¿Qué pasa si el token se usa después de que el usuario cerró sesión desde otro dispositivo? Debe ser
  rechazado igual que cualquier token invalidado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a una persona registrarse con email y contraseña.
- **FR-002**: El sistema DEBE rechazar registros con un email ya utilizado por otra cuenta.
- **FR-003**: El sistema DEBE validar formato de email y una política mínima de contraseña (longitud
  mínima) antes de crear la cuenta.
- **FR-004**: El sistema DEBE permitir iniciar sesión con email y contraseña correctos, devolviendo un
  token de acceso.
- **FR-005**: El sistema DEBE rechazar inicios de sesión con credenciales incorrectas usando un mensaje
  genérico que no revele si el email existe.
- **FR-006**: El sistema DEBE aceptar el token de acceso en peticiones subsecuentes para identificar al
  usuario autenticado, sin requerir reingresar credenciales durante la vigencia del token.
- **FR-007**: El sistema DEBE permitir cerrar sesión de forma explícita, invalidando el token usado.
- **FR-008**: El sistema DEBE rechazar el uso de tokens expirados o invalidados.
- **FR-009**: El sistema DEBE almacenar las contraseñas de forma irreversible (hash), nunca en texto
  plano.
- **FR-010**: El sistema DEBE limitar/ralentizar intentos repetidos de login fallido sobre el mismo
  email en una ventana de tiempo corta.

### Key Entities

- **Usuario**: representa a una persona con acceso a la app. Atributos clave: identificador único,
  email (único), contraseña almacenada como hash, nombre para mostrar, fecha de creación.
- **Sesión / Token**: representa una autenticación activa de un usuario. Atributos clave: usuario
  asociado, fecha de expiración, estado (activo/invalidado).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona nueva puede registrarse e iniciar sesión por primera vez en menos de 1
  minuto.
- **SC-002**: Un usuario ya registrado puede iniciar sesión en menos de 10 segundos en condiciones
  normales de red.
- **SC-003**: El 100% de los endpoints que exponen datos financieros del usuario rechazan peticiones
  sin un token válido.
- **SC-004**: Ningún intento de login fallido expone si el email existe o no en el sistema.

## Assumptions

- El único método de autenticación en esta primera versión es email + contraseña (sin login social ni
  SSO); queda fuera de alcance para este feature.
- No hay recuperación de contraseña ("olvidé mi contraseña") en el alcance de este feature — se
  documentará como feature separado si se necesita.
- Cada usuario ve y gestiona únicamente sus propios datos (cuentas, categorías, y lo que venga
  después); esto lo garantiza este feature emitiendo la identidad que las features siguientes usan
  para aislar datos (constitución, principio IV).
- La duración de vigencia del token de acceso se define en el plan técnico, no en esta spec (es un
  detalle de implementación).
