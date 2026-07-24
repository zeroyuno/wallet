# Research: Modernizar la interfaz con el sistema de diseño Midnight FinTech

## 1. Traducción de la paleta a `ColorScheme` de Compose

**Decision**: los colores del `DESIGN.md` de Stitch (`midnight_fintech/DESIGN.md`) ya usan exactamente
los mismos nombres de rol que `androidx.compose.material3.ColorScheme` (`primary`, `onPrimary`,
`primaryContainer`, `secondary`, `error`, `surface`, `surfaceContainer`, etc.) — no hace falta
reinterpretar ni mapear manualmente qué color va en qué rol. Se construye un único
`darkColorScheme(...)` en `Color.kt`/`Theme.kt` con esos valores literales.

**Rationale**: Stitch generó el export ya en convención Material3 (probablemente vía Material Theme
Builder internamente), así que la traducción es mecánica y de bajo riesgo de error — copiar cada hex a
su rol correspondiente.

**Alternatives considered**: interpretar los colores "a mano" desde las capturas (screen.png) sin usar
los valores exactos del `DESIGN.md` — descartado, más impreciso y más trabajo para el mismo resultado
dado que el `DESIGN.md` ya trae los valores exactos en formato directamente utilizable.

## 2. Tipografía Inter como variable font

**Decision**: se descarga `Inter[opsz,wght].ttf` (variable font, ejes `opsz` y `wght`, ~860KB) desde el
repositorio oficial de Google Fonts (licencia OFL) y se agrega a `res/font/`. La `Typography` de
Compose se arma con varias entradas `Font(R.font.inter, weight = ..., variationSettings =
FontVariation.Settings(FontVariation.weight(...)))` — una por cada peso que define el sistema de diseño
(400/500/600/700) — en vez de 4 archivos `.ttf` estáticos separados.

**Rationale**: el repositorio de Google Fonts para Inter solo publica el archivo variable (no hay
carpeta `static/` en la fuente actual) — usar variable font + `variationSettings` es además el enfoque
recomendado actualmente por Jetpack Compose (soportado desde Compose 1.2 en API 26+, que ya es el
`minSdk` de la app) y reduce a 1 archivo en vez de 4 lo que hay que bundlear/mantener.

**Alternatives considered**: convertir manualmente el variable font a 4 estáticos — descartado,
innecesario dado que Compose soporta variable fonts nativamente; usar la fuente del sistema — ya
descartada en la decisión previa con el usuario (el sistema de diseño pide Inter explícitamente).

**Nota de cumplimiento de licencia**: se incluye una copia de `OFL.txt` (Open Font License) del
repositorio de Google Fonts junto a los archivos de fuente, requisito de la licencia.

## 3. Estado de "qué pestaña de la barra de navegación está activa"

**Decision**: no se crea un ViewModel nuevo. Se agrega una función pura,
`fun Screen.toBottomNavTab(): BottomNavTab?`, que mapea el estado `Screen` ya existente en
`MainActivity.kt` a una de las 3 pestañas (o `null` si la pantalla actual no es una de las 3 secciones
principales, en cuyo caso la barra de navegación se oculta — FR de spec.md, edge case de pantallas
secundarias). El `NavigationBar` de Compose se muestra condicionalmente según si esa función devuelve
no-null.

**Rationale**: el estado de navegación (`screen`) ya existe y es la única fuente de verdad — derivar la
pestaña activa a partir de él evita duplicar estado (y el riesgo de que se desincronicen). Es
exactamente el mismo criterio de simplicidad (YAGNI) ya aplicado en el resto del proyecto.

**Alternatives considered**: un ViewModel dedicado a la navegación — descartado, agrega una capa
(inyección Hilt, ciclo de vida) para gobernar un dato que ya existe como estado local y no necesita
sobrevivir más allá de la propia composición de `MainActivity`.

## 4. Selección de íconos para la barra de navegación y las filas de movimiento/cuenta/categoría

**Decision**: usar únicamente íconos ya presentes en `androidx.compose.material:material-icons-core`
(la dependencia ya incluida, sin agregar `material-icons-extended`, que es pesada). La lista concreta de
qué ícono usa cada pestaña/tipo de cuenta/categoría se validó por prueba de compilación durante
`/speckit-implement` (ya se había dado este mismo problema en la feature 007 — `ArrowUpward`/
`ArrowDownward` no estaban en core y se resolvió con un carácter Unicode como fallback).

**Hallazgo durante la implementación**: `material-icons-core` 1.7.8 trae solo ~48 íconos "Filled"
(inspeccionado extrayendo el AAR) — bastante más acotado de lo esperado, sin ninguno de
banco/billetera/categoría/swap. Se resolvió así:
- Barra de navegación: `AccountBox` (Cuentas, sí disponible), `List` (Movimientos, sí disponible),
  símbolo de texto "▦" (Categorías, no hay ícono adecuado en core).
- Tipo de cuenta (`AccountListScreen`): emoji por tipo (💵/🏦/💳/📦) en vez de íconos vectoriales — no
  hay ninguno de banco/tarjeta/billetera en core.
- Acción de cerrar sesión: `Settings` (sí disponible, coincide además con el ícono de engranaje que
  usan los mockups de Stitch en la esquina superior derecha).
- Ingreso/gasto (`TransactionListScreen`, ya resuelto en la feature 007): flechas de texto "↓"/"↑".

**Rationale**: agregar `material-icons-extended` (miles de íconos, varios MB) para un puñado de íconos
puntuales no se justifica a esta escala; ya existe precedente de resolverlo con texto/Unicode cuando
hace falta.

**Alternatives considered**: agregar `material-icons-extended` — descartado por peso; usar imágenes
vectoriales custom (SVG/XML) por cada ícono — descartado, más trabajo de mantenimiento para un
resultado equivalente a los íconos ya disponibles en core para las categorías más comunes (cuenta,
movimientos, categorías, casa, auto, comida, etc., todos presentes en el set core).

## 5. Pantalla de aterrizaje tras el login

**Decision**: al iniciar sesión, la app aterriza en la pestaña "Cuentas" (reemplaza a `Screen.Home`,
que se elimina — research.md de plan.md, Complexity Tracking).

**Rationale**: es un dato no especificado en los mockups de Stitch (ninguno indica cuál pestaña es la
de aterrizaje) — se elige Cuentas por dar una vista general/resumen antes de entrar al detalle de
movimientos, patrón común en apps de finanzas personales. Decisión de bajo impacto y fácilmente
reversible si el usuario prefiere otra pestaña por defecto.

**Alternatives considered**: aterrizar en "Movimientos" — igual de válido; se documenta acá como
decisión tomada durante la implementación en vez de bloquear el plan con una pregunta de bajo impacto.
