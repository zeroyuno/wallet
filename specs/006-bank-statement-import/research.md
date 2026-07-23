# Research: Importar movimientos desde estados de cuenta bancarios en PDF

## 1. Cómo se interpreta el PDF

**Decision**: el PDF se envía completo (como documento, codificado en base64) directamente a la API
de Mensajes de Anthropic (`POST https://api.anthropic.com/v1/messages`), pidiendo que el modelo
identifique los movimientos y los devuelva vía **tool-use forzado** (`tool_choice` apuntando a una
única tool `record_transactions` con un JSON Schema fijo: lista de `{date, amount, type, description}`
más `{unparsed_lines: [{rawText, reason}]}` para las líneas que no pudo interpretar con confianza).
No se extrae texto del PDF con una librería propia (ej. Apache PDFBox) de forma previa.

**Rationale**: la razón original para elegir un LLM en vez de un parser por banco es justamente que el
formato varía — dejar que el modelo procese el documento completo (con su layout visual, tablas, etc.)
es más robusto que primero aplanarlo a texto plano (donde se pierde la estructura de columnas/tablas
que ayuda a distinguir fecha/monto/descripción). El tool-use forzado garantiza una respuesta JSON
válida contra un schema conocido, evitando parsear texto libre con regex (frágil e impredecible con un
LLM).

## 1b. Qué modelo usar

**Decision**: Claude Haiku 4.5 (`claude-haiku-4-5-20251001`), no Sonnet ni Opus.

**Rationale**: es extracción estructurada de una tabla (leer filas de un documento y devolver JSON),
no una tarea que requiera razonamiento profundo — Haiku alcanza de sobra y es varias veces más barato.
Precios reales verificados (claude.com/pricing, julio 2026): Haiku 4.5 $1/MTok input, $5/MTok output
vs. Sonnet 5 $2/MTok input, $10/MTok output. Estimado por PDF (un estado de cuenta de 2-6 páginas,
~7.000-13.000 tokens de entrada por el documento + overhead de tool-use, ~1.500-2.500 tokens de
salida por el JSON de movimientos): **~$0.02 por PDF con Haiku** (~$0.04 con Sonnet). Para un volumen
de uso personal (decenas a un par de cientos de PDFs históricos, luego 1-2 por mes) el costo total es
de unos pocos dólares, no un factor limitante — igual se documenta acá para que quede explícito y
sea fácil de re-evaluar si el volumen creciera.

**Alternatives considered**: Sonnet 5 — descartado por ahora por ser 2-2.5x más caro sin necesidad
para esta tarea puntual; se puede reconsiderar si la calidad de extracción con Haiku resulta
insuficiente en la práctica (T026, validación manual).

**Alternatives considered (extracción de texto)**: extraer texto con Apache PDFBox y mandar el texto
plano al LLM —
distinguir qué número es el monto y cuál es el saldo corriente, por ejemplo; agregaría una dependencia
nueva sin necesidad, dado que Claude soporta documentos PDF de forma nativa.

## 2. Autenticación con la API de Anthropic

**Decision**: `ANTHROPIC_API_KEY` como variable de entorno del backend (mismo patrón que
`JWT_SECRET`/`DB_PASSWORD` en `application.properties`), sin valor por defecto para producción — si
falta, el endpoint de subida responde con un error claro en vez de fallar silenciosamente en el
`@Async`.

**Rationale**: es la propia API key del backend (server-side), no una credencial del usuario final —
a diferencia del token de Wallet, esta no la provee el usuario en cada request, es infraestructura del
servicio. Mismo criterio de "secreto por variable de entorno, nunca committeado" ya usado en el resto
del proyecto.

## 3. Deduplicación sin id externo (FR-006)

**Decision**: hash SHA-256 de `accountId + "|" + date.toString() + "|" + amount.toPlainString() +
"|" + description.trim().toLowerCase()`, guardado en una tabla `statement_import_line_hashes`
(`user_id`, `account_id`, `hash`, `internal_transaction_id`, único por `user_id`+`hash`). Antes de
crear cada movimiento extraído, se calcula su hash y se verifica si ya existe — si existe, se omite
(ya importado); si no, se crea la transacción y se registra el hash.

**Rationale**: decisión ya tomada explícitamente con el usuario en `/speckit-specify` (Pregunta 1) —
es el único mecanismo posible sin un id de movimiento real como el que sí provee la API de Wallet. La
limitación aceptada (dos movimientos legítimos idénticos en fecha+monto+descripción se tratan como uno
solo) queda documentada en spec.md.

**Alternatives considered**: hash del archivo PDF completo — rechazado porque no protege contra
estados de cuenta distintos con rango de fechas solapado (Edge Case explícito de spec.md, US3).

## 4. Manejo de errores y reintentos hacia la API de Anthropic

**Decision**: una sola llamada por PDF (sin paginación ni reintentos automáticos con backoff). Si la
llamada falla (error de red, `429`, error del modelo, o el modelo no logra procesar el documento), el
`StatementImport` completo pasa a `FAILED` con un motivo legible; el usuario puede volver a subir el
mismo PDF más tarde (la deduplicación ya construida evita que eso duplique lo que sí se haya llegado a
importar en un intento previo parcialmente exitoso — aunque en este diseño el fallo es de todo el
request, no parcial, dado que es una sola llamada).

**Rationale**: a diferencia de la importación de Wallet (500 requests/hora, corridas de miles de
movimientos que sí necesitan resumibilidad), acá es **una llamada por PDF** — el volumen es
inherentemente bajo (un estado de cuenta a la vez). No se justifica el mecanismo de cursor/pausa de la
feature 005 para este caso (YAGNI, Complexity Tracking de plan.md).

## 5. Persistencia del PDF subido

**Decision**: el archivo no se guarda en ningún lado más allá de la memoria del request — se procesa
y se descarta. No hay un endpoint para volver a descargar el PDF original ni un blob storage nuevo.

**Rationale**: mismo criterio ya usado con el token de Wallet ("no se persiste, solo vive en memoria
durante la corrida") — minimiza qué datos sensibles retiene el sistema. Agregar almacenamiento de
archivos (Azure Blob Storage u otro) sería infraestructura nueva sin un requisito que la justifique
todavía (YAGNI).

## 6. Tamaño máximo de archivo

**Decision**: `spring.servlet.multipart.max-file-size=20MB` /
`spring.servlet.multipart.max-request-size=20MB` (el default de Spring Boot es 1MB, insuficiente para
un PDF de varias páginas).

**Rationale**: 20MB cubre con margen amplio un estado de cuenta típico de varias páginas (que
normalmente pesan unos pocos MB), sin abrir la puerta a archivos desproporcionadamente grandes.

## 7. Correcciones tras probar con un PDF real (T026)

Tres bugs reales encontrados recién al validar contra un estado de cuenta real del usuario (mismo
patrón que la feature 005: los defectos de integración con una API externa no aparecen hasta probar
con datos reales, no con el fake usado en los tests automatizados):

1. **Request mal serializado hacia Anthropic**: el bloque de contenido `document` y el bloque `text`
   compartían un mismo record Java con los campos de ambos — Jackson serializaba el campo no usado
   como `null` en vez de omitirlo, y la API de Anthropic rechaza con `400
   "Extra inputs are not permitted"` cualquier clave no esperada en un bloque, aunque sea `null`. Se
   corrige separando `document`/`text` en dos records distintos sin campos de más, tipando
   `AnthropicMessage.content` como `List<Object>` (cada bloque serializa solo lo suyo).
2. **Tipo ingreso/gasto inferido solo de la descripción**: el primer prompt le pedía al modelo
   determinar `INCOME`/`EXPENSE` únicamente a partir del texto de la descripción. Muchos estados de
   cuenta reales (el de prueba incluido) separan los movimientos en columnas explícitas (cargo/abono,
   débito/crédito, retiro/depósito) — esa es la fuente de verdad, no el texto. Se corrige el prompt
   para que priorice SIEMPRE la columna cuando el documento la tiene, y solo caiga a inferir por
   descripción si no existe esa separación.
3. **Deduplicación huérfana al borrar transacciones manualmente**: `statement_import_line_hashes` no
   tenía una foreign key hacia `transactions` — al borrar una transacción importada (vía
   `DELETE /api/transactions/{id}`, uso normal de la app), su hash de deduplicación quedaba huérfano
   y bloqueaba para siempre una futura re-importación de ese mismo movimiento (la corrida terminaba en
   `COMPLETED` con `transactionsImported: 0`, sin ningún error visible). Se agrega la FK con
   `ON DELETE CASCADE` (migración V8) — a diferencia de `import_external_refs` de la feature 005, acá
   sí es posible una FK simple porque `internal_transaction_id` siempre referencia `transactions`
   (nunca cuentas ni categorías, que la feature 006 no crea).

## 8. Precisión del tipo ingreso/gasto: "mostrar el trabajo" + corrección determinística

**Decision**: además de la instrucción de priorizar la columna (research.md #7.2), se agrega un campo
obligatorio `source_column` al schema del tool-use — el modelo debe declarar, para cada movimiento, el
texto literal del encabezado de columna donde vio el monto, ANTES de asignar `type`. Del lado del
código, `AnthropicPdfExtractionClient` no confía ciegamente en el `type` que devuelve el modelo: si
`source_column` matchea un término bancario conocido (abono/crédito/depósito → `INCOME`,
cargo/débito/retiro → `EXPENSE`), esa columna decide el tipo final, no el juicio del modelo. Si
`source_column` viene vacío (documento sin columnas separadas), se respeta el `type` que ya infirió el
modelo por descripción.

**Rationale**: validado con un PDF real (T026), la sola instrucción de "priorizá la columna" en el
prompt no bastó — el modelo declaraba bien la columna en algunos casos y aun así asignaba el `type`
contrario, sobre todo cuando la descripción "sonaba" al tipo opuesto (ej. "Pago YAPE de X" es un
ingreso pese a que la palabra "Pago" sugiere gasto). Forzar un campo intermedio obligatorio
(`source_column`) es una técnica de prompting conocida para mejorar precisión en extracción tabular
(obliga al modelo a "mostrar el trabajo" fila por fila en vez de solo dar la conclusión); agregar
además una corrección determinística en el código sobre ese campo es una capa de seguridad barata que
no depende de que el modelo sea perfectamente consistente entre `source_column` y `type` en cada fila.

**Alternatives considered**: quedarse solo con la instrucción de prompt sin el campo intermedio ni la
corrección en código — insuficiente, es justo lo que falló en la validación manual. Confiar
únicamente en la corrección por palabra clave sin pedirle al modelo que declare la columna — más
frágil, porque dependería de que el código adivinara el nombre de columna sin que el modelo lo haya
leído explícitamente del documento.

**Segunda ronda de validación**: con `source_column` agregado, 1 de 2 casos previamente mal
clasificados se corrigió solo; el otro ("Pago YAPE de X") seguía fallando. Causa: el estado de cuenta
real del usuario tiene headers combinados en terminología contable —
`"CARGOS / DEBE"` / `"ABONOS / HABER"` (común en Perú/LatAm) — y el modelo aparentemente declaraba
solo la segunda mitad del header (`"DEBE"`/`"HABER"` sueltos) en `source_column`, que no estaba en la
lista de palabras clave (solo tenía cargo/abono/débito/crédito/depósito/retiro).

**Rediseño (sin diccionario propio)**: agregar `"debe"`/`"haber"` a la lista resolvía este caso
puntual, pero el problema de fondo seguía ahí — cualquier banco con OTRA terminología (o en otro
idioma) iba a volver a fallar, y mantener una lista de palabras bancarias por país/banco no escala.
Se reemplaza el enfoque: en vez de que el código intente reconocer nombres de columna, el propio
modelo identifica UNA SOLA VEZ por documento (no fila por fila) cuáles son los dos encabezados reales
del PDF — `expense_column_header` e `income_column_header`, con su texto literal tal cual aparece,
sea cual sea el idioma o la terminología del banco — y por cada movimiento repite (copia textual) cuál
de esos dos le corresponde en `column_header`. El código ya no necesita ningún diccionario: solo
compara si `column_header` de una fila es exactamente igual a `expense_column_header` o
`income_column_header` que el modelo ya identificó. Esto es genérico por diseño — la "lista de
sinónimos bancarios" la resuelve el modelo con su propio entendimiento del idioma, no un `Set<String>`
en Java que hay que seguir ampliando cada vez que aparece un banco nuevo.
