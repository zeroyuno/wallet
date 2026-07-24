# Data Model: Modernizar la interfaz con el sistema de diseño Midnight FinTech

No hay entidades de dominio ni persistencia nuevas (feature puramente de presentación/navegación). Lo
que sigue son las "formas de datos" que introduce la UI.

## Tokens del sistema de diseño (`ui/theme/`)

Valores literales tomados de `~/Downloads/stitch_wallet_personal_finance_ui/midnight_fintech/DESIGN.md`
— no se recalculan ni se derivan, se transcriben.

### Color (roles de `androidx.compose.material3.ColorScheme`)

| Rol | Valor |
|---|---|
| `background` / `surface` | `#10131a` |
| `surfaceContainerLowest` | `#0b0e14` |
| `surfaceContainer` | `#1d2026` |
| `onSurface` | `#e1e2eb` |
| `onSurfaceVariant` | `#c0c7d4` |
| `primary` | `#a3c9ff` |
| `onPrimary` | `#00315c` |
| `primaryContainer` | `#4da1ff` |
| `secondary` (éxito/ingreso) | `#40e56c` |
| `onSecondary` | `#003912` |
| `secondaryContainer` | `#02c953` |
| `error` (gasto) | `#ffb4ab` |
| `onError` | `#690005` |
| `errorContainer` | `#93000a` |
| `outline` | `#8a919d` |
| `outlineVariant` | `#404752` |

(lista no exhaustiva — el resto de los roles del `DESIGN.md` se transcriben igual, 1:1, sin reinterpretar).

### Typography

| Rol | Tamaño | Peso | Line height |
|---|---|---|---|
| `headlineXl` | 32sp | 700 | 40sp |
| `headlineLg` | 24sp | 600 | 32sp |
| `bodyLg` | 16sp | 400 | 24sp |
| `bodyMd` | 14sp | 400 | 20sp |
| `labelLg` | 14sp | 600 | 20sp |
| `labelMd` | 12sp | 500 | 16sp |
| `numericDisplay` (montos/saldos) | 28sp | 700 | 36sp |

Todos con `FontFamily` Inter (variable font, peso vía `variationSettings` — ver research.md #2).

### Shape / Spacing

| Token | Valor |
|---|---|
| `shape.sm` | 4dp |
| `shape.default` | 8dp |
| `shape.md` | 12dp |
| `shape.lg` | 16dp |
| `shape.xl` | 24dp |
| `shape.full` | pill (999dp) |
| `spacing.xs` … `spacing.xl` | 8dp / 12dp / 16dp / 24dp / 32dp |

## `BottomNavTab` (navegación)

Enum puro de UI, sin persistencia:

| Valor | Pantallas `Screen` asociadas |
|---|---|
| `Accounts` | `AccountsList`, `AccountForm` |
| `Transactions` | `TransactionsList`, `TransactionForm` |
| `Categories` | `CategoriesList`, `CategoryForm` |
| *(barra oculta)* | `Login`, `Register` — `Screen.toBottomNavTab()` devuelve `null` |

`Screen.Home` se elimina (research.md #5) — su función (menú manual) queda cubierta por la barra de
navegación inferior.
