# Data Model: Gestión de cuentas y categorías

Modelado como `domain` del bounded context `account` (clases planas, sin JPA — mismo patrón que
`auth`, ver research.md de la feature 001).

## Account (aggregate root)

| Campo | Tipo (dominio) | Reglas |
|---|---|---|
| `id` | `AccountId` (value object, envuelve UUID) | generado por el sistema |
| `userId` | `UUID` (ver Relaciones — por qué es un UUID plano y no un value object compartido) | dueño de la cuenta |
| `name` | `String` | no vacío (FR-012) |
| `type` | `AccountType` (enum: `CASH`, `BANK`, `CREDIT_CARD`, `OTHER`) | `OTHER` por defecto si no se especifica |
| `currency` | `CurrencyCode` (value object) | código ISO 4217 de 3 letras, ej. `USD` |
| `initialBalance` | `BigDecimal` | puede ser negativo (tarjetas de crédito con deuda) |
| `createdAt` | `Instant` | fecha de creación |

**Comportamiento del agregado**: `Account.create(userId, name, type, currency, initialBalance)`
(factory estático, valida invariantes de creación); `account.rename(name, type, currency)` (edición).

## Category (aggregate root)

| Campo | Tipo (dominio) | Reglas |
|---|---|---|
| `id` | `CategoryId` (value object) | generado por el sistema |
| `userId` | `UUID` | dueño de la categoría |
| `name` | `String` | no vacío; único junto con `userId` + `type` (FR-007) |
| `type` | `CategoryType` (enum: `INCOME`, `EXPENSE`) | |
| `parentCategoryId` | `CategoryId` (nullable) | debe ser una categoría del mismo `userId` y mismo
  `type` (FR-010); no puede apuntar a sí misma ni formar un ciclo (FR-011) |
| `createdAt` | `Instant` | fecha de creación |

**Comportamiento del agregado**: `Category.create(userId, name, type, parentCategoryId)`;
`category.rename(name, parentCategoryId)`. La validación de ciclo/auto-referencia vive en
`CategoryService` (necesita consultar el repositorio para recorrer la cadena de padres, algo que un
agregado aislado no puede hacer por sí solo).

## Puertos de salida (interfaces en `account.domain`)

- `AccountRepository`: `save(Account)`, `findAllByUserId(UUID)`, `findByIdAndUserId(AccountId, UUID)`,
  `deleteByIdAndUserId(AccountId, UUID)`.
- `CategoryRepository`: `save(Category)`, `findAllByUserId(UUID)`, `findByIdAndUserId(CategoryId,
  UUID)`, `deleteByIdAndUserId(CategoryId, UUID)`, `existsByUserIdAndTypeAndName(UUID, CategoryType,
  String)`.

## Persistencia (account.infrastructure.persistence)

- `AccountEntity`/`CategoryEntity` (`@Entity`, tablas `accounts`/`categories`): mismos campos que el
  dominio pero con anotaciones JPA; `user_id` como columna simple (FK lógica a `users.id`, sin mapear
  la entidad `UserEntity` de `auth` — los contextos no comparten entidades JPA, solo el `UUID`).
- `JpaAccountRepository`/`JpaCategoryRepository`: implementan los puertos del dominio, mapean
  `*Entity ↔ *` (dominio).

## Relaciones

- `Account.userId` y `Category.userId` son un `UUID` plano, **no** el value object `UserId` de
  `auth.domain`. Cada bounded context modela su propia noción de "a quién pertenece esto" — importar
  el `UserId` de `auth.domain` acoplaría `account` a los internos de otro contexto (violaría el
  principio II) solo para ahorrarse un `UUID`. Si en el futuro se necesita compartir un tipo de
  identidad entre contextos, ese tipo se define en `shared`, no en el `domain` de un contexto
  concreto — por ahora un `UUID` es suficiente (YAGNI).
- `Category.parentCategoryId` → `Category.id` (auto-referencia opcional, mismo usuario y tipo).
- Ninguna de estas entidades se relaciona todavía con movimientos/transacciones — esa relación se
  definirá en una feature futura (fuera de alcance aquí, ver Assumptions en spec.md).
