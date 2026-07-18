# Data Model: Transacciones (ingresos y gastos)

Modelado como `domain` del bounded context `transaction` (clases planas, sin JPA — mismo patrón que
`auth`/`account`, ver research.md de features anteriores).

## Transaction (aggregate root)

| Campo | Tipo (dominio) | Reglas |
|---|---|---|
| `id` | `TransactionId` (value object, envuelve UUID **v7**) | provisto por el cliente si lo envía (FR-011), o generado por el sistema si no; nunca UUID v4 (ver research.md) |
| `userId` | `UUID` (ver Relaciones) | dueño de la transacción |
| `type` | `TransactionType` (enum: `INCOME`, `EXPENSE`) | propio de este contexto (ver Relaciones); inmutable tras la creación |
| `amount` | `BigDecimal` | mayor que cero (FR-009); el signo de su efecto sobre el saldo lo determina `type`, no el monto |
| `date` | `LocalDate` | requerida (FR-009) |
| `description` | `String` (nullable) | opcional, sin restricción de contenido |
| `accountId` | `UUID` (ver Relaciones) | cuenta propia del usuario (FR-008); inmutable tras la creación |
| `categoryId` | `UUID` (nullable, ver Relaciones) | si se indica, categoría propia del usuario y del mismo `type` (FR-008) |
| `createdAt` | `Instant` | fecha de creación del registro |

**Comportamiento del agregado**: `Transaction.create(id, userId, type, amount, date, description,
accountId, categoryId)` (factory estático; `id` es `Optional<TransactionId>` — si está vacío, la
factory genera uno con UUID v7; valida invariantes de creación — monto positivo);
`transaction.update(amount, date, description, categoryId)` (edición — `type` y `accountId` no
cambian, ver research.md).

## Puerto de salida (interfaz en `transaction.domain`)

- `TransactionRepository`: `save(Transaction)`, `findByIdAndUserId(TransactionId, UUID)`,
  `existsByIdAndUserId(TransactionId, UUID)` (para detectar un `id` duplicado provisto por el
  cliente antes de guardar, FR-011), `deleteByIdAndUserId(TransactionId, UUID)`,
  `findAllByUserId(UUID, TransactionFilter)` (filtro
  opcional por `accountId`, `categoryId`, `dateFrom`, `dateTo` — FR-004), `sumNetAmountForAccount(UUID
  userId, UUID accountId): BigDecimal` (suma de ingresos menos gastos de esa cuenta; `ZERO` si no hay
  transacciones — usado para calcular el saldo, ver research.md).

## Persistencia (transaction.infrastructure.persistence)

- `TransactionEntity` (`@Entity`, tabla `transactions`): mismos campos que el dominio con anotaciones
  JPA; `user_id`, `account_id`, `category_id` como columnas UUID simples (sin mapear las entidades JPA
  de `account` — los contextos no comparten entidades JPA, solo IDs).
- `JpaTransactionRepository`: implementa el puerto del dominio, incluye la query de agregación
  (`SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END)`) para `sumNetAmountForAccount`. El
  filtro de `findAllByUserId` (accountId/categoryId/dateFrom/dateTo) se aplica en Java sobre el
  resultado de una query simple (`findAllByUserId(userId)`), no con parámetros opcionales en SQL —
  ver research.md (problema de tipado de parámetros null con Postgres/Hibernate).

## Esquema (V3\_\_create_transactions.sql)

```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    category_id UUID REFERENCES categories(id) ON DELETE RESTRICT,
    type VARCHAR(10) NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    date DATE NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_category_id ON transactions(category_id);
CREATE INDEX idx_transactions_date ON transactions(date);
```

Los `ON DELETE RESTRICT` son el mecanismo que cumple FR-010 (no se puede eliminar una cuenta o
categoría con transacciones asociadas) sin que `account` dependa de `transaction` — ver research.md.

## Relaciones

- `Transaction.userId` es un `UUID` plano, igual criterio que `Account.userId`/`Category.userId` en
  la feature 002 (no se importa el `UserId` de `auth.domain`).
- `Transaction.accountId`/`categoryId` son también `UUID` planos — **no** los value objects
  `AccountId`/`CategoryId` de `account.domain`. Mismo motivo que `userId`: `transaction` no importa
  tipos de `account.domain` (violaría el principio II / la regla ArchUnit de privacidad de `domain`
  por contexto). La existencia y pertenencia de esos IDs se valida en `TransactionService` llamando a
  métodos de `account.application.{AccountService,CategoryService}` que devuelven únicamente tipos
  primitivos (ver research.md) — nunca se resuelven a un `Account`/`Category` de dominio dentro de
  `transaction`.
- `TransactionType` (`INCOME`/`EXPENSE`) es un enum propio de `transaction.domain`, deliberadamente
  duplicado en valores respecto de `account.domain.CategoryType` — cada contexto modela su propio
  lenguaje ubicuo (ver research.md). La validación "la categoría debe ser del mismo tipo que la
  transacción" compara el `name()` de ambos enums como `String`, en la frontera entre contextos.
- Ninguna entidad de `account` se modifica por esta feature: `Account`/`Category` no ganan ningún
  campo ni referencia hacia `Transaction`.
