---

description: "Task list for feature 008: Modernizar la interfaz con el sistema de diseño Midnight FinTech"
---

# Tasks: Modernizar la interfaz con el sistema de diseño Midnight FinTech

**Input**: Design documents from `/specs/008-midnight-ui-theme/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Se incluye una tarea de test para la única lógica no trivial que agrega esta feature
(`Screen.toBottomNavTab()`) — el resto son Composables de presentación sin lógica propia, validados
visualmente (mismo criterio ya usado para los fixes de UI de la feature 007).

**Organization**: Tareas agrupadas por user story (spec.md): US1 y US2 son ambas P1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)
- **[Story]**: A qué user story pertenece (US1, US2)

## Path Conventions

Proyecto Android existente: `android/app/src/main/java/com/walletapp/android/...` (producción),
`android/app/src/test/java/com/walletapp/android/...` (tests), `android/app/src/main/res/font/`
(recursos de fuente).

---

## Phase 1: Setup

**Purpose**: Recurso de fuente disponible antes de armar la tipografía del tema.

- [X] T001 [P] Descargar `Inter[opsz,wght].ttf` (variable font, licencia OFL) desde el repositorio
      oficial de Google Fonts y guardarlo como `android/app/src/main/res/font/inter.ttf`; guardar
      también `OFL.txt` junto a los recursos de fuente para cumplir la licencia (research.md #2)

**Checkpoint**: el archivo de fuente existe en el proyecto, listo para referenciarse desde `Type.kt`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Sistema de tema (`WalletTheme`) — ninguna user story puede avanzar sin esto, ya que tanto
la barra de navegación (US1) como el restyling de pantallas (US2) dependen de los mismos tokens.

**⚠️ CRITICAL**: Bloquea ambas user stories.

- [X] T002 [P] Crear `android/app/src/main/java/com/walletapp/android/ui/theme/Color.kt`: un
      `darkColorScheme(...)` de Material3 con los valores exactos del sistema "Midnight FinTech"
      (data-model.md) — depende de nada
- [X] T003 Crear `android/app/src/main/java/com/walletapp/android/ui/theme/Type.kt`: `Typography` de
      Material3 con `FontFamily` Inter (una entrada `Font(R.font.inter, weight=..., variationSettings=
      FontVariation.Settings(FontVariation.weight(...)))` por cada peso 400/500/600/700 — depende de
      T001
- [X] T004 [P] Crear `android/app/src/main/java/com/walletapp/android/ui/theme/Shape.kt`: `Shapes` de
      Material3 con los radios sm/default/md/lg/xl (data-model.md) — depende de nada
- [X] T005 Crear `android/app/src/main/java/com/walletapp/android/ui/theme/Theme.kt`: Composable
      `WalletTheme(content: @Composable () -> Unit)` que envuelve `MaterialTheme` con el
      `colorScheme`/`typography`/`shapes` de T002-T004 — depende de T002, T003, T004
- [X] T006 Modificar `MainActivity.kt`: reemplazar `MaterialTheme { }` (default, sin personalizar) por
      `WalletTheme { }` — depende de T005

**Checkpoint**: la app compila y arranca con el nuevo tema aplicado por defecto a todos los componentes
Material3 existentes (aunque las pantallas puntuales todavía no estén restilizadas, ya heredan colores/
tipografía nuevos automáticamente).

---

## Phase 3: User Story 1 - Navegar la app con una barra inferior fija (Priority: P1) 🎯 MVP

**Goal**: Reemplazar la navegación manual (pantalla `Home` con botones) por una barra de navegación
inferior fija con 3 accesos.

**Independent Test**: Con sesión iniciada, tocar cada uno de los 3 accesos desde cualquiera de los
otros dos y verificar que la app cambia de sección con el ítem activo resaltado.

### Implementation for User Story 1

- [X] T007 [US1] Crear `android/app/src/main/java/com/walletapp/android/navigation/BottomNavTab.kt`:
      enum `BottomNavTab { Accounts, Transactions, Categories }` + función pura
      `fun Screen.toBottomNavTab(): BottomNavTab?` (null para Login/Register/formularios — data-model.md).
      Requiere cambiar el modifier de `Screen` en `MainActivity.kt` de `private` a `internal` para que
      este archivo (mismo módulo, otro archivo) pueda referenciarlo — depende de T006
- [X] T008 [P] [US1] Test unitario de `Screen.toBottomNavTab()` (cada variante de `Screen` mapea a la
      pestaña esperada; Login/Register devuelven null) en
      `android/app/src/test/java/com/walletapp/android/navigation/BottomNavTabTest.kt` — depende de T007
- [X] T009 [US1] Modificar `MainActivity.kt`: agregar `NavigationBar`/`NavigationBarItem` (Cuentas/
      Movimientos/Categorías) dentro del `Scaffold`, visible solo cuando
      `screen.toBottomNavTab() != null`, con el ítem activo resaltado según la pestaña actual — depende
      de T007
- [X] T010 [US1] Modificar `MainActivity.kt`: eliminar `Screen.Home` y el Composable `HomeScreen()`; la
      pantalla inicial tras un login exitoso pasa a ser `Screen.AccountsList` (research.md #5);
      actualizar el `when`/`BackHandler` en consecuencia — depende de T009

**Checkpoint**: User Story 1 funcional de forma independiente — se navegan las 3 secciones principales
con la barra inferior, sin pasar por un menú `Home`.

---

## Phase 4: User Story 2 - Ver las pantallas existentes con el nuevo estilo visual (Priority: P1)

**Goal**: Las 5 pantallas ya mockeadas usan la paleta, tipografía y estilo de componentes del sistema
de diseño Midnight FinTech, sin agregar elementos de funcionalidades inexistentes.

**Independent Test**: Abrir cada una de las 5 pantallas y verificar visualmente contra las capturas de
Stitch que usan la paleta oscura, Inter, y el estilo de tarjetas/chips — sin botones que no tengan
acción real detrás.

### Implementation for User Story 2

- [X] T011 [P] [US2] Restilizar `auth/ui/LoginScreen.kt`: nuevo estilo de campos y botón de acceso, sin
      agregar los botones de "Google"/"Apple ID" del mockup (FR-006, sin login social implementado) —
      depende de T006
- [X] T012 [P] [US2] Restilizar `auth/ui/RegisterScreen.kt` con el mismo estilo que el login — depende
      de T006
- [X] T013 [P] [US2] Restilizar `accounts/ui/AccountListScreen.kt`: tarjetas con ícono según tipo de
      cuenta, saldo alineado a la derecha, sin los botones de "Transferir"/"Recibir" del mockup (FR-006,
      sin esa funcionalidad) — depende de T006
- [X] T014 [P] [US2] Restilizar `accounts/ui/AccountFormScreen.kt` — depende de T006
- [X] T015 [P] [US2] Restilizar `categories/ui/CategoryListScreen.kt`: mantiene el agrupado por tipo
      (ingreso/gasto) que ya tiene hoy, con el nuevo estilo de tarjeta — depende de T006
- [X] T016 [P] [US2] Restilizar `categories/ui/CategoryFormScreen.kt` — depende de T006
- [X] T017 [P] [US2] Restilizar `transactions/ui/TransactionListScreen.kt`: nuevo estilo de tarjeta/
      ícono/tipografía, preservando el fix de layout (`weight(1f)`, ellipsis de una línea) de la
      feature 007 — depende de T006
- [X] T018 [P] [US2] Restilizar `transactions/ui/TransactionFormScreen.kt` — depende de T006
- [X] T019 [P] [US2] Restilizar `transactions/ui/DatePickerField.kt`: nuevo estilo de botón/diálogo,
      preservando el fix de estado inicial y el botón "Hoy" de la feature 007 — depende de T006

**Checkpoint**: Las 2 user stories funcionan de forma independiente entre sí — navegación principal y
las 5 pantallas mockeadas usan el nuevo sistema de diseño.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T020 Ejecutar los 6 escenarios de `quickstart.md` en un dispositivo físico — **pendiente**, sin
      `adb`/emulador en este entorno (mismo motivo que en la feature 007); sí se validó
      `./gradlew :app:assembleDebug` completo (compila y empaqueta el recurso de fuente sin errores)
- [X] T021 [P] Confirmar `./gradlew :app:testDebugUnitTest` en verde
- [X] T022 Revisión manual contra las capturas de Stitch: ningún texto con bajo contraste, ningún
      elemento visual sin funcionalidad real detrás (SC-002/SC-003)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias
- **Foundational (Phase 2)**: depende de Setup (T003 necesita T001) — bloquea ambas user stories
- **US1 (Phase 3)** y **US2 (Phase 4)**: ambas P1, dependen solo de Foundational (T006);
  independientes entre sí — no comparten archivos de producción
- **Polish (Phase 5)**: depende de ambas user stories

### Parallel Opportunities

- T002 y T004 (Foundational) son paralelos entre sí; T001 (Setup) es paralelo a ambos
- Una vez completo T006, toda la Phase 3 (US1) puede avanzar en paralelo con toda la Phase 4 (US2) —
  no comparten archivos
- Dentro de US2, T011-T019 son todas paralelas entre sí (pantallas distintas, sin dependencias cruzadas)
- T008 (test) es paralelo a cualquier archivo de producción que no sea `BottomNavTab.kt`

---

## Parallel Example: tras completar Foundational (Phase 2)

```bash
# US1 y US2 pueden avanzar en paralelo (no comparten archivos):
Task: "Crear navigation/BottomNavTab.kt y agregar NavigationBar a MainActivity.kt"
Task: "Restilizar las 9 pantallas existentes (auth, accounts, categories, transactions) con WalletTheme"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Completar Phase 1 (Setup) y Phase 2 (Foundational) — el tema ya se ve aplicado por defecto
2. Completar Phase 3 (US1) y Phase 4 (US2) en paralelo — ambas P1, no hay un "MVP parcial" razonable
   más chico que ambas juntas (una barra de navegación sin pantallas restilizadas, o pantallas
   restilizadas sin navegación coherente, no cumplen el pedido original completo)
3. **Validar** con los 6 escenarios de `quickstart.md`

### Incremental Delivery

1. Setup + Foundational → tema base aplicado
2. US1 + US2 (en paralelo) → app modernizada completa
3. Polish → validación final en dispositivo

---

## Notes

- No se agrega ningún ViewModel nuevo en esta feature — `Screen.toBottomNavTab()` es una función pura
  (research.md #3).
- No se agrega `material-icons-extended` — los íconos se resuelven con lo ya disponible en
  `material-icons-core`, con fallback a texto/Unicode si hace falta (mismo criterio ya usado en la
  feature 007, research.md #4).
- Por la convención de git del proyecto, la implementación de estas tareas se hace en la rama
  `008-midnight-ui-theme`, con un solo PR al finalizar.
- **T011/T012/T014/T016/T018/T019 sin diff de código**: `LoginScreen`, `RegisterScreen`,
  `AccountFormScreen`, `CategoryFormScreen`, `TransactionFormScreen` y `DatePickerField` ya usaban
  roles de `MaterialTheme` (sin colores hardcodeados, verificado por búsqueda) y ninguno tenía botones
  de Google/Apple/transferencias/QR que sacar — heredan el sistema de diseño nuevo automáticamente al
  quedar todas envueltas en `WalletTheme` (T006), sin necesitar cambios propios. Solo `AccountListScreen`,
  `CategoryListScreen` y `TransactionListScreen` (T013/T015/T017) necesitaron cambios de código reales
  (íconos por fila, agrupado de categorías, topBar con acción de cerrar sesión).
