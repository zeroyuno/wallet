# Feature Specification: Caché local y sincronización de movimientos en Android

**Feature Branch**: `007-transactions-local-sync`

**Created**: 2026-07-23

**Status**: Draft

**Input**: User description: "Caché local y sincronización en segundo plano para movimientos (transacciones) en Android. Objetivo: resolver que la pantalla de movimientos hoy carga los ~11.000 registros del backend en una sola llamada y falla/se pone lenta. En vez de leer la API en vivo, la app Android guarda los movimientos en una base de datos local (Room) que es la fuente de datos para la UI (lectura paginada local, instantánea, sin depender de la red en cada apertura de pantalla). El backend sigue siendo la fuente de verdad de los datos. Sincronización: (1) hacia abajo, la app trae desde el backend solo los movimientos nuevos/modificados desde la última sincronización (no la lista completa cada vez) y los guarda en local; (2) hacia arriba, cuando el usuario crea, edita o elimina un movimiento, el cambio se refleja al instante en la base local (UI optimista, sin esperar al backend) y se empuja al backend en segundo plano; si falla el envío (sin conexión, error), se reintenta más adelante sin bloquear el uso de la app. Prioridad: rendimiento y experiencia de uso (que la app no se sienta lenta ni falle por volumen de datos), no soporte offline real — el caso de uso es un único usuario en un único teléfono, casi siempre con conexión, así que no hace falta resolver conflictos de edición concurrente entre dispositivos. Alcance: solo movimientos/transacciones en esta ronda (no cuentas ni categorías, que son pocas filas y no tienen este problema). Requiere que el backend exponga una forma de traer solo cambios incrementales (por ejemplo por fecha de última modificación + paginación), ya que hoy GET /api/transactions no pagina y devuelve todo. Fuera de alcance: multi-dispositivo con resolución de conflictos, uso offline completo (crear movimientos sin haber sincronizado nunca), sincronización de cuentas/categorías."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Abrir la pantalla de movimientos sin esperas ni fallos (Priority: P1) 🎯 MVP

Un usuario autenticado abre la pantalla de "mis movimientos" y la ve cargar de inmediato, sin importar
cuántos movimientos tenga acumulados en su cuenta (hoy ~11.000), y sin que la pantalla falle o se
congele.

**Why this priority**: Es el problema que dispara la feature — hoy la pantalla directamente falla con
el volumen actual de datos. Sin esto no hay valor entregado.

**Independent Test**: Con una cuenta que tiene miles de movimientos ya sincronizados una vez, abrir la
pantalla de movimientos repetidas veces (incluyendo con la red desconectada) y verificar que siempre
carga al instante, mostrando los movimientos más recientes primero, con scroll para ver más.

**Acceptance Scenarios**:

1. **Given** un usuario con miles de movimientos ya sincronizados previamente, **When** abre la
   pantalla de movimientos, **Then** la lista aparece de inmediato (sin esperar una respuesta de red) y
   permite hacer scroll para ver más movimientos de forma progresiva.
2. **Given** un usuario sin conexión a internet, **When** abre la pantalla de movimientos, **Then**
   igual ve la lista completa de lo que ya tenía sincronizado, sin mensajes de error.

---

### User Story 2 - Crear, editar o eliminar un movimiento sin esperar al servidor (Priority: P1)

Un usuario crea, edita o elimina un movimiento y ve el cambio reflejado en la lista de inmediato, sin
esperar a que el backend confirme la operación. El envío al backend ocurre en segundo plano.

**Why this priority**: Junto con la User Story 1 es lo que hace que la app "se sienta" rápida en el
uso diario — es el mismo mecanismo (lectura y escritura contra la base local) aplicado a las
operaciones de escritura.

**Independent Test**: Crear un movimiento nuevo y verificar que aparece en la lista al instante (antes
de que una llamada de red pudiera completarse); confirmar por separado (revisando el backend) que
también quedó guardado ahí una vez sincronizado.

**Acceptance Scenarios**:

1. **Given** el usuario está en la pantalla de movimientos, **When** crea un movimiento nuevo,
   **Then** aparece en la lista de inmediato y, en un momento posterior, queda reflejado también en el
   backend.
2. **Given** un movimiento ya sincronizado, **When** el usuario lo edita o lo elimina, **Then** el
   cambio se ve reflejado de inmediato en la lista local y se propaga al backend en segundo plano.
3. **Given** el usuario no tiene conexión a internet, **When** crea, edita o elimina un movimiento,
   **Then** el cambio se guarda localmente y queda pendiente de enviar, sin bloquear el uso de la app
   ni mostrar un error.

---

### User Story 3 - Reintento automático cuando falla el envío al backend (Priority: P2)

Cuando un cambio pendiente no logra enviarse al backend (por falta de conexión o un error temporal), el
sistema lo reintenta más adelante sin intervención del usuario, hasta lograr sincronizarlo.

**Why this priority**: Sin esto, un cambio hecho sin conexión podría perderse silenciosamente en vez de
sincronizarse apenas vuelva la conexión — es la garantía que sostiene el valor de las historias 1 y 2 a
lo largo del tiempo, pero no es necesaria para demostrar el flujo básico.

**Independent Test**: Crear un movimiento con la red desconectada, esperar sin reconectar (confirmar
que no se pierde ni bloquea nada), luego reconectar la red y verificar que el movimiento se envía al
backend sin que el usuario tenga que repetir la acción.

**Acceptance Scenarios**:

1. **Given** un cambio pendiente de sincronizar por falta de conexión, **When** la conexión se
   restablece, **Then** el sistema lo envía al backend automáticamente, sin acción del usuario.
2. **Given** un envío al backend falla por un error temporal (no de conexión), **When** el sistema
   reintenta más adelante, **Then** el cambio finalmente queda sincronizado sin duplicarse.

---

### Edge Cases

- ¿Qué pasa si el usuario cierra sesión con cambios todavía pendientes de sincronizar? (asunción: se
  descartan al cerrar sesión — ver sección de Asunciones)
- ¿Qué pasa si el usuario reinstala la app o cambia de teléfono? La base local se reconstruye desde
  cero, trayendo todo el historial del backend nuevamente vía sincronización incremental completa desde
  el principio.
- ¿Qué pasa si dos cambios pendientes sobre el mismo movimiento se acumulan antes de sincronizar (ej.
  editar dos veces seguidas sin conexión)? Solo se envía el estado final resultante, no cada paso
  intermedio.
- ¿Qué pasa si el usuario edita/elimina un movimiento que todavía no terminó de sincronizarse como
  "creado" (la creación original sigue pendiente de envío)? El sistema debe encadenar correctamente
  la operación pendiente para no perder ni duplicar el movimiento una vez que se sincronice.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La app Android MUST mostrar la lista de movimientos leyendo únicamente desde una base de
  datos local en el dispositivo, sin depender de una respuesta de red para renderizar la pantalla.
- **FR-002**: La lista de movimientos MUST cargar de forma paginada/incremental (scroll progresivo), no
  cargando todos los movimientos guardados localmente de una sola vez.
- **FR-003**: El sistema MUST sincronizar hacia el dispositivo solo los movimientos nuevos o modificados
  desde la última sincronización exitosa, no la lista completa en cada sincronización.
- **FR-004**: El backend MUST exponer una forma de consultar movimientos de forma incremental y
  paginada (p. ej. por fecha de última modificación), reemplazando la carga completa actual.
- **FR-005**: Al crear, editar o eliminar un movimiento, el sistema MUST reflejar el cambio en la base
  local y en la pantalla de inmediato, sin esperar la confirmación del backend.
- **FR-006**: El sistema MUST enviar al backend, en segundo plano, cada cambio (creación, edición,
  eliminación) hecho localmente, sin que el usuario tenga que esperar ni accionar nada adicional.
- **FR-007**: Si el envío de un cambio pendiente al backend falla (sin conexión o error), el sistema
  MUST reintentarlo automáticamente más adelante, sin bloquear el uso de la app ni perder el cambio.
- **FR-008**: El sistema MUST evitar duplicar un movimiento en el backend si el mismo cambio pendiente
  se reintenta más de una vez (p. ej. tras un reintento exitoso que no se confirmó a tiempo del lado del
  dispositivo).
- **FR-009**: La sincronización hacia el dispositivo MUST ejecutarse automáticamente en segundo plano
  (al abrir la app y periódicamente) además de permitir que el usuario la dispare manualmente (p. ej.
  con un gesto de "pull to refresh").
- **FR-010**: Esta feature MUST aplicar únicamente a movimientos/transacciones — cuentas y categorías
  siguen consultándose en vivo contra el backend como hasta ahora.

### Key Entities

- **Movimiento local (caché)**: representación en el dispositivo de una transacción, espejo de los
  mismos datos que ya existen en el backend (fecha, monto, tipo, descripción, cuenta, categoría), más
  metadatos propios de la sincronización: si tiene cambios pendientes de enviar y de qué tipo
  (creación/edición/eliminación).
- **Cursor de sincronización**: marca hasta qué punto ya se trajeron los cambios del backend, para que
  la siguiente sincronización solo pida lo nuevo desde ahí en adelante.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La pantalla de movimientos se muestra en menos de 1 segundo al abrirla, sin importar la
  cantidad de movimientos acumulados en la cuenta.
- **SC-002**: La pantalla de movimientos es utilizable (abre y muestra el historial ya sincronizado) sin
  conexión a internet.
- **SC-003**: Un movimiento creado, editado o eliminado se refleja en la pantalla en menos de 200ms,
  independientemente de la velocidad de la red en ese momento.
- **SC-004**: El 100% de los cambios hechos sin conexión se sincronizan correctamente al backend una vez
  que la conexión se restablece, sin intervención del usuario y sin duplicados.
- **SC-005**: Ningún movimiento se pierde ni se duplica entre lo que el usuario ve en la app y lo que
  existe en el backend, en el uso normal de un único usuario en un único dispositivo.

## Assumptions

- Un único usuario, un único dispositivo activo a la vez — no hace falta resolver conflictos de
  edición concurrente entre dos instancias de la app con el mismo usuario (fuera de alcance, ver
  descripción).
- Al cerrar sesión, cualquier cambio todavía pendiente de sincronizar se descarta junto con la base
  local (mismo tratamiento que el resto de los datos locales de la sesión) — no se contempla un aviso
  especial de "tenés cambios sin guardar" en esta ronda.
- Cuentas y categorías siguen funcionando como hoy (consulta en vivo contra el backend) — no se
  cachean localmente en esta ronda, dado que son pocas filas y no presentan el problema de volumen que
  motiva esta feature.
- El backend sigue siendo la única fuente de verdad de los datos; la base local en el dispositivo es
  una caché de lectura/escritura optimista, no un reemplazo del backend.
- No se requiere soporte para trabajar completamente offline desde una instalación nueva (primer uso):
  la primera sincronización sí necesita conexión para traer el historial inicial.
