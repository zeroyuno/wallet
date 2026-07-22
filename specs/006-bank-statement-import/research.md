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
