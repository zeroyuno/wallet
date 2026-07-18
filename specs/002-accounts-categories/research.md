# Research: Gestión de cuentas y categorías

## Generalizar las reglas ArchUnit a múltiples bounded contexts

**Decision**: Reemplazar las dos reglas hardcodeadas para `auth` en `ArchitectureTest.java` por
versiones parametrizadas (`@ArchTest static void ...(JavaClasses classes)`) que iteran sobre una lista
de contextos conocidos (`auth`, `account`), aplicando la misma regla de privacidad de
`infrastructure`/`domain` a cada uno.

**Rationale**: El comentario dejado en la feature 001 ya anticipaba este momento ("cuando exista un
segundo bounded context, generalizar"). Evita duplicar código de test por cada contexto nuevo.

**Alternatives considered**: Usar `SlicesRuleDefinition.slices()...notDependOnEachOther()` — rechazado
porque esa API trata las dependencias como simétricas (nadie depende de nadie), mientras que acá
`shared` necesita ser dependible por todos sin que eso cuente como violación, y esa asimetría no se
expresa fácilmente con `slices()`.

## Modelo de subcategorías (FR-010, FR-011, User Story 4)

**Decision**: Auto-referencia simple en `categories` (`parent_category_id`, nullable, FK a
`categories.id`), sin límite de profundidad impuesto por el modelo de datos. La validación de
"no ciclos / no auto-referencia" se hace en `CategoryService` antes de persistir.

**Rationale**: Es el modelo más simple que cumple la spec (una categoría puede tener un padre
opcional); evita introducir una tabla de jerarquía o un campo de "nivel" que la spec no pide
(constitución, principio V).

**Alternatives considered**: Path/materialized path para jerarquías profundas — rechazado como
sobre-ingeniería para el alcance actual (la spec no pide más de un nivel de agrupación).

## Aislamiento por usuario en consultas (FR-002, FR-005, FR-008)

**Decision**: Toda query de `AccountRepository`/`CategoryRepository` que lista o busca por id recibe
explícitamente el `userId` del usuario autenticado como parámetro (ej.
`findByIdAndUserId(id, userId)`), en vez de filtrar después en memoria. Si el registro no pertenece al
usuario, el resultado es "no encontrado" (404), igual que si no existiera.

**Rationale**: Aplicar el filtro en la propia query de base de datos hace estructuralmente imposible
una fuga de datos entre usuarios, cumpliendo el principio IV de la constitución de la forma más
directa — mismo patrón ya probado en la feature 001.

**Alternatives considered**: Filtrar en memoria después de traer todos los registros — rechazado por
ser menos eficiente y más fácil de olvidar accidentalmente en un endpoint futuro.

## Identidad del usuario dentro del backend

**Decision**: Reutilizar `AuthenticatedUser`/`JwtAuthFilter`/`SecurityConfig` de `shared.security`
(ya implementados en la feature 001); los controllers de este contexto obtienen el `userId`
autenticado vía `@AuthenticationPrincipal AuthenticatedUser`, igual que `AuthController`.

**Rationale**: Evita duplicar lógica de autenticación; mantiene un único punto de verdad para "quién
es el usuario actual" en todo el backend — exactamente el propósito de `shared.security`.

**Alternatives considered**: N/A — ya resuelto por diseño en la feature 001.
