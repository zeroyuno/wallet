# Feature Specification: Modernizar la interfaz con el sistema de diseño Midnight FinTech

**Feature Branch**: `008-midnight-ui-theme`

**Created**: 2026-07-24

**Status**: Draft

**Input**: User description: "Modernizar la interfaz de Android con el sistema de diseño \"Midnight FinTech\" generado en Stitch. Aplica un dark theme completo (paleta, tipografía Inter, radios de esquina, espaciado) a las pantallas ya existentes de cuentas, movimientos, categorías, formulario de movimiento y login/registro, reemplazando los estilos default de Material3 sin personalizar que usa la app hoy. Incluye agregar una barra de navegación inferior fija (Cuentas / Movimientos / Categorías) como reemplazo del mecanismo de navegación manual actual (MainActivity con mapa de pantallas y BackHandler propio) — es un cambio estructural de navegación, no solo visual. La tipografía Inter se bundlea localmente (archivos .ttf en res/font, licencia OFL) para funcionar offline. Los mockups de Stitch muestran elementos de funcionalidades que la app no tiene (transferencias entre cuentas con QR de recibir, vencimiento de tarjeta de crédito, login social con Google/Apple) — esos elementos NO se implementan, se omiten de las pantallas reales; tampoco hay foto de perfil de usuario (se reemplaza por un ícono o iniciales). Alcance: solo re-diseño visual y navegación de las pantallas ya existentes (accounts, categories, transactions list/form, auth) — no se agregan pantallas ni funcionalidades nuevas de negocio en esta ronda."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Navegar la app con una barra inferior fija (Priority: P1) 🎯 MVP

Un usuario autenticado ve, en todas las pantallas principales, una barra de navegación inferior fija
con tres accesos directos (Cuentas, Movimientos, Categorías) y puede moverse entre ellas tocando cada
ícono, sin depender del botón de retroceso del sistema para volver a una sección anterior.

**Why this priority**: Es el cambio estructural del que depende visualmente el resto — sin la barra de
navegación, las demás pantallas rediseñadas quedan navegablemente desconectadas entre sí igual que hoy.

**Independent Test**: Con sesión iniciada, tocar cada uno de los tres accesos de la barra inferior
desde cualquier pantalla y verificar que la app cambia a la sección correspondiente, con el ícono
activo resaltado visualmente.

**Acceptance Scenarios**:

1. **Given** el usuario está en la pantalla de Movimientos, **When** toca el acceso "Cuentas" en la
   barra inferior, **Then** la app muestra la pantalla de cuentas y el acceso "Cuentas" queda marcado
   como activo.
2. **Given** el usuario está en cualquiera de las tres secciones principales, **When** navega a una
   pantalla secundaria (ej. formulario de alta de movimiento), **Then** la barra de navegación inferior
   deja de mostrarse (no es una acción de las tres secciones principales) y vuelve a aparecer al
   regresar.

---

### User Story 2 - Ver las pantallas existentes con el nuevo estilo visual (Priority: P1)

Un usuario ve las pantallas de cuentas, movimientos, categorías, el formulario de movimiento y el login
con la nueva paleta oscura, tipografía y estilo de tarjetas/botones/chips, en vez del estilo por
defecto de Material3 sin personalizar que tiene la app hoy.

**Why this priority**: Es el objetivo central del pedido ("se ve como un prototipo") — sin esto, la
barra de navegación de la User Story 1 quedaría aplicada sobre pantallas que siguen viéndose igual que
antes.

**Independent Test**: Abrir cada una de las 5 pantallas rediseñadas y verificar visualmente que usan la
paleta oscura, la tipografía Inter, y el estilo de tarjetas/chips definido en el sistema de diseño, sin
que ningún texto quede ilegible por bajo contraste.

**Acceptance Scenarios**:

1. **Given** el usuario abre la pantalla de movimientos, **When** la lista se renderiza, **Then** cada
   fila usa el estilo de tarjeta oscura con el ícono de categoría/tipo, tipografía Inter, y colores de
   éxito/error para ingreso/gasto definidos en el sistema de diseño.
2. **Given** el usuario abre la pantalla de login, **When** la ve, **Then** el formulario, los campos de
   texto y el botón de acceso usan el nuevo estilo visual — sin los botones de "Google"/"Apple ID" ni
   ningún otro elemento de funcionalidad que la app no tiene.
3. **Given** el usuario abre la pantalla de cuentas, **When** la ve, **Then** cada cuenta se muestra
   como una tarjeta con ícono según su tipo y el nuevo estilo visual, sin los botones de
   "Transferir"/"Recibir" ni indicadores de vencimiento de tarjeta que no corresponden a datos reales
   de la app.

---

### Edge Cases

- ¿Qué pasa si el dispositivo usa una fuente del sistema muy grande (accesibilidad)? El texto debe
  seguir siendo legible y las tarjetas/listas deben poder crecer en alto sin romper el layout (mismo
  criterio de robustez ya aplicado en los fixes recientes de la pantalla de movimientos).
- ¿Qué pasa con una categoría o cuenta cuyo tipo/ícono no está contemplado explícitamente en el sistema
  de diseño (ej. un tipo de cuenta agregado a futuro)? Debe existir un ícono/color por defecto
  razonable, no un ícono roto o ausente.
- ¿Qué pasa si el usuario navega directo a una pantalla secundaria (ej. editar un movimiento) y
  presiona atrás? Debe volver a la sección principal correspondiente con la barra de navegación inferior
  visible de nuevo, no salir de la app.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La app MUST mostrar una barra de navegación inferior fija con tres accesos (Cuentas,
  Movimientos, Categorías) visible en las tres pantallas principales.
- **FR-002**: La barra de navegación MUST indicar visualmente cuál de las tres secciones está activa.
- **FR-003**: Las pantallas de cuentas, movimientos, categorías, formulario de movimiento y login/registro
  MUST aplicar la paleta de colores, tipografía y estilo de componentes (tarjetas, chips, botones)
  definidos en el sistema de diseño Midnight FinTech.
- **FR-004**: La tipografía Inter MUST estar embebida en la app (funciona sin conexión a internet), no
  depender de una descarga en el primer uso.
- **FR-005**: Las pantallas rediseñadas MUST seguir mostrando exactamente los mismos datos y acciones
  reales que hoy (crear/editar/eliminar movimientos, filtros, saldo, categorías/subcategorías) — el
  cambio es de presentación y navegación, no de funcionalidad.
- **FR-006**: Las pantallas MUST omitir cualquier elemento visual de los mockups que no corresponda a
  una funcionalidad real de la app (transferencias entre cuentas, código QR para recibir, vencimiento de
  tarjeta de crédito, login con Google/Apple, foto de perfil de usuario).
- **FR-007**: Donde el mockup usa una foto de perfil de usuario, la app MUST mostrar en su lugar un
  ícono genérico o las iniciales del usuario — no hay funcionalidad de foto de perfil.
- **FR-008**: El color de ingreso (verde) y de gasto (rojo) definidos en el sistema de diseño MUST
  aplicarse de forma consistente en todas las pantallas que distinguen ingreso/gasto (ya sea texto,
  ícono, o ambos).

### Key Entities

- **Sistema de diseño (tokens)**: paleta de colores (roles Material3: primary, secondary, error,
  superficies, etc.), escala tipográfica (Inter, varios tamaños/pesos), escala de espaciado, radios de
  esquina, y especificación de componentes reutilizables (tarjetas, chips, botones) — ya definidos en el
  export de Stitch, se traducen a un tema de la app.
- **Sección de navegación principal**: cada uno de los tres accesos de la barra inferior (Cuentas,
  Movimientos, Categorías), con su ícono activo/inactivo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario puede moverse entre las tres secciones principales tocando la barra de
  navegación inferior, sin usar el botón de retroceso del sistema, en el 100% de los casos.
- **SC-002**: Las 5 pantallas rediseñadas (cuentas, movimientos, categorías, formulario de movimiento,
  login/registro) usan la paleta y tipografía del nuevo sistema de diseño de forma consistente entre
  sí — ningún texto queda con contraste insuficiente para leerse.
- **SC-003**: Ninguna pantalla rediseñada muestra un botón o elemento visual que, al tocarlo, no tenga
  ninguna acción real detrás (todo lo que se ve es funcional o fue removido).
- **SC-004**: El comportamiento funcional de cada pantalla (qué datos muestra, qué se puede crear/editar/
  eliminar/filtrar) es idéntico al de antes del rediseño, verificado contra los mismos escenarios ya
  cubiertos por los tests existentes de cada ViewModel.

## Assumptions

- El sistema de diseño "Midnight FinTech" (paleta, tipografía, espaciado, radios, especificación de
  componentes) ya está definido y aprobado — viene del export de Stitch en
  `~/Downloads/stitch_wallet_personal_finance_ui/`, no se re-discute en esta ronda.
- Alcance de pantallas: las 5 ya mockeadas (cuentas, movimientos, categorías, formulario de movimiento,
  login/registro). La pantalla de registro (separada del login en la app actual) sigue el mismo estilo
  aunque no tenga un mockup dedicado.
- No se agrega soporte de tema claro en esta ronda — el sistema de diseño es dark-mode-first y la app
  pasa a ser oscura únicamente (documentado como decisión, no limitación técnica: se puede reconsiderar
  a futuro con su propia spec).
- La barra de navegación inferior reemplaza la navegación manual actual solo para las 3 secciones
  principales — pantallas secundarias (formularios de alta/edición, login/registro) siguen navegándose
  como hasta ahora (push/back), sin la barra visible.
- Los íconos de categoría/cuenta por tipo son un mapeo razonable elegido durante la implementación
  (ej. por `AccountType`/nombre de categoría), no hay una lista cerrada de íconos definida en esta spec.
