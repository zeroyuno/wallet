# Implementation Plan: Modernizar la interfaz con el sistema de diseño Midnight FinTech

**Branch**: `008-midnight-ui-theme` | **Date**: 2026-07-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-midnight-ui-theme/spec.md`

## Summary

Se reemplaza el `MaterialTheme {}` sin personalizar de `MainActivity.kt` por un tema oscuro completo
("Midnight FinTech", export de Stitch en `~/Downloads/stitch_wallet_personal_finance_ui/`): paleta de
colores (ya expresada en roles de Material3), tipografía Inter (bundleada localmente), radios de
esquina y espaciado. Se agrega una barra de navegación inferior fija (Cuentas/Movimientos/Categorías)
que reemplaza la pantalla `Home` actual (un menú manual), reutilizando el mecanismo de navegación por
estado ya existente en `MainActivity.kt` en vez de introducir Navigation-Compose. Se actualiza el
estilo visual de las 5 pantallas ya mockeadas, sin agregar botones/elementos de funcionalidades que la
app no tiene (transferencias, QR, vencimiento de tarjeta, login social, foto de perfil).

## Technical Context

**Language/Version**: Kotlin 2.2.21 sobre el módulo `android/` ya existente (Jetpack Compose,
compileSdk 37, minSdk 26). Sin cambios de versión de plataforma.

**Primary Dependencies**: Reutiliza Compose Material3 (`NavigationBar`/`NavigationBarItem` ya
disponibles en la versión actual, sin dependencia nueva). Se agrega la fuente Inter como recurso local
(`res/font/`, formato variable `.ttf`, licencia OFL) — no se agrega Navigation-Compose ni ninguna
librería de theming externa.

**Storage**: N/A — sin cambios de persistencia; feature puramente de presentación/navegación.

**Testing**: JUnit4 para la función pura que mapea `Screen` → pestaña activa de la barra de navegación
(ver research.md #3 — no hace falta un ViewModel para esto). Verificación visual manual en dispositivo
físico (no hay `adb` en este entorno de ejecución del agente, ver Notas de la feature 007) para las 5
pantallas rediseñadas.

**Target Platform**: Android, dispositivo físico contra el backend de producción (sin cambios de
plataforma ni de backend — esta feature no toca `backend/`).

**Project Type**: mobile-app — cambios contenidos en `android/app`, ningún cambio en `backend/`.

**Performance Goals**: Sin metas de throughput; expectativa estándar de fluidez de una app Compose
(sin regresión de performance por el cambio de tema/fuente).

**Constraints**: La fuente Inter debe funcionar 100% offline (embebida, no descargable en runtime). El
resto del comportamiento funcional de cada pantalla debe quedar idéntico (FR-005) — este es un
rediseño de presentación, no una reescritura de lógica.

**Scale/Scope**: 5 pantallas rediseñadas (cuentas, movimientos, categorías, formulario de movimiento,
login/registro) + 1 componente nuevo (barra de navegación inferior) + fundamentos de tema
(`Color.kt`/`Type.kt`/`Shape.kt`/`Theme.kt`) que se vuelven la base para toda la app de acá en más.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. API Contract-First**: N/A — esta feature no toca la API REST del backend ni agrega/cambia
  ningún endpoint; consume exactamente los mismos datos que ya expone el backend.
- **II. Arquitectura hexagonal y DDD**: N/A para esta feature — aplica solo al backend según la propia
  constitución. Android sigue MVVM sin excepción: el ViewModel nuevo de navegación (qué pestaña está
  activa) es puro estado de UI, no lógica de dominio.
- **III. Tests y cobertura >80%**: PASS — no se agrega ningún ViewModel nuevo en esta feature (es
  presentación/navegación pura), así que no aplica el requisito de "todo ViewModel con lógica lleva
  tests". La única lógica no trivial (qué pestaña de la barra de navegación corresponde a cada
  `Screen`) es una función pura y sí lleva test unitario. Los demás cambios son Composables de
  presentación (Color/Type/Shape/Theme, estilos de Card/Chip/Button) sin lógica propia que testear — se
  validan visualmente, mismo criterio que ya se usó para los fixes de UI de la feature 007.
- **IV. Aislamiento de datos por usuario**: N/A — no se toca ninguna consulta ni lógica de acceso a
  datos; las pantallas siguen mostrando exactamente lo que ya mostraban, con otro estilo visual.
- **V. Simplicidad y alcance por spec (YAGNI)**: PASS con una decisión explícita — se reutiliza el
  mecanismo de navegación por estado ya existente en `MainActivity.kt` en vez de migrar a
  Navigation-Compose (evita una migración de navegación mucho más grande de la que pide esta spec); no
  se implementa ningún elemento visual de los mockups sin funcionalidad real detrás (FR-006), evitando
  UI "de mentira".

No hay violaciones que requieran justificación adicional más allá de lo documentado en Complexity
Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/008-midnight-ui-theme/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md
```

### Source Code (repository root)

```text
android/app/src/main/
├── res/font/
│   ├── inter.ttf                          # [NUEVO] variable font (wght 100-900, opsz), licencia OFL
│   └── inter_italic.ttf                   # [NUEVO] variante itálica (usada mínimamente, si se usa)
├── java/com/walletapp/android/
│   ├── ui/theme/
│   │   ├── Color.kt                       # [NUEVO] paleta Midnight FinTech (roles Material3)
│   │   ├── Type.kt                        # [NUEVO] Typography con FontFamily Inter (variationSettings por peso)
│   │   ├── Shape.kt                       # [NUEVO] Shapes (radios sm/default/md/lg/xl/full)
│   │   └── Theme.kt                       # [NUEVO] WalletTheme { } — envuelve MaterialTheme con lo anterior
│   ├── MainActivity.kt                    # [MODIFICADO] usa WalletTheme en vez de MaterialTheme{} default;
│   │                                          agrega NavigationBar; quita Screen.Home (redundante con la
│   │                                          barra); pantalla inicial tras login pasa a ser Cuentas
│   ├── navigation/
│   │   └── BottomNavTab.kt                # [NUEVO] enum de las 3 pestañas + función pura
│   │                                          Screen.toBottomNavTab(): BottomNavTab? (null = ocultar la
│   │                                          barra) — sin ViewModel, ver research.md #3
│   ├── accounts/ui/AccountListScreen.kt   # [MODIFICADO] estilo de tarjetas/tipografía/colores nuevos
│   ├── accounts/ui/AccountFormScreen.kt   # [MODIFICADO] estilo de campos/botones nuevos
│   ├── categories/ui/CategoryListScreen.kt # [MODIFICADO] estilo nuevo, agrupado por tipo (ya lo hace hoy)
│   ├── categories/ui/CategoryFormScreen.kt # [MODIFICADO] estilo nuevo
│   ├── transactions/ui/TransactionListScreen.kt # [MODIFICADO] estilo nuevo (ya tiene el fix de layout de
│   │                                          la feature 007 — se mantiene, solo cambian colores/tipografía)
│   ├── transactions/ui/TransactionFormScreen.kt # [MODIFICADO] estilo nuevo
│   ├── transactions/ui/DatePickerField.kt # [MODIFICADO] estilo nuevo (mantiene el fix de la feature 007)
│   ├── auth/ui/LoginScreen.kt              # [MODIFICADO] estilo nuevo, sin botones de Google/Apple ID
│   └── auth/ui/RegisterScreen.kt           # [MODIFICADO] estilo nuevo
└── test/java/com/walletapp/android/
    └── navigation/BottomNavTabTest.kt      # [NUEVO]
```

**Structure Decision**: Se crea un paquete `ui/theme/` nuevo (patrón estándar de un proyecto Compose,
separado de las pantallas por feature ya existentes) con los fundamentos del tema. La navegación
inferior se resuelve dentro de la misma `MainActivity.kt`/estado `Screen` ya existente — se agrega un
paquete `navigation/` chico con una función pura que deriva la pestaña activa desde `Screen`, sin
ViewModel (research.md #3). El resto de los
paquetes por feature (`accounts/`, `categories/`, `transactions/`, `auth/`) no cambian de estructura,
solo el contenido visual de sus Composables ya existentes.

## Complexity Tracking

| Decisión | Por qué es necesaria | Alternativa más simple descartada porque |
|-----------|------------|---------------------------------------|
| Fuente Inter como variable font embebida (~860KB) en vez de fuente del sistema | FR-004/SC-002: el sistema de diseño especifica Inter explícitamente y debe funcionar offline | Usar la fuente del sistema (Roboto) — descartada porque no cumple el pedido explícito del sistema de diseño; usar Google Fonts descargable — descartada en la decisión ya tomada con el usuario (depende de conexión/Play Services) |
| Se quita la pantalla `Home` (menú manual existente) | La barra de navegación inferior ya cubre las 3 secciones que `Home` enlazaba — mantenerla sería una pantalla redundante que ningún mockup contempla | Dejar `Home` como pantalla intermedia tras el login — descartada por ser justo la navegación manual que esta spec pide reemplazar (FR-001) |
