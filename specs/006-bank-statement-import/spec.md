# Feature Specification: Importar movimientos desde estados de cuenta bancarios en PDF

**Feature Branch**: `006-bank-statement-import`

**Created**: 2026-07-22

**Status**: Draft

**Input**: User description: "Importar movimientos desde estados de cuenta bancarios en PDF. El usuario autenticado puede subir uno o varios archivos PDF de estados de cuenta (de cualquiera de sus cuentas bancarias o de tarjeta de crédito ya existentes en la app) y el sistema extrae los movimientos (fecha, monto, tipo ingreso/gasto, descripción/concepto) usando un modelo de lenguaje (LLM) para interpretar el texto del PDF, dado que el formato varía entre bancos y no se puede depender de un parser rígido por banco. El usuario indica a qué cuenta propia pertenece cada PDF antes de subirlo (no se auto-detecta la cuenta). Los movimientos extraídos se crean como transacciones reales en esa cuenta, reutilizando el mecanismo de creación ya existente, incluyendo protección de idempotencia para no duplicar movimientos si el mismo PDF (o un período de fechas solapado con otro estado de cuenta) se sube más de una vez. El procesamiento puede tardar así que debe correr en segundo plano, permitiendo consultar el progreso y ver qué movimientos no se pudieron extraer o fallaron. Backend-only en esta ronda, sin UI de Android todavía."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Importar un estado de cuenta en PDF (Priority: P1) 🎯 MVP

Un usuario autenticado sube el PDF de un estado de cuenta de una de sus cuentas ya existentes en la
app (bancaria o de tarjeta de crédito), indicando a qué cuenta pertenece. El sistema interpreta el
contenido del PDF y crea como transacciones reales los movimientos que logra identificar (fecha,
monto, tipo, descripción), asociados a esa cuenta.

**Why this priority**: Es el flujo mínimo que entrega valor — sin esto no hay feature. Todo lo demás
(múltiples archivos, reanudación, visibilidad de errores) es soporte alrededor de este flujo base.

**Independent Test**: Con un PDF de estado de cuenta real de una cuenta ya creada en la app, subirlo
indicando la cuenta correspondiente, y verificar que los movimientos aparecen en "Mis movimientos"
asociados a esa cuenta, con monto, fecha y tipo correctos.

**Acceptance Scenarios**:

1. **Given** una cuenta propia ya existente y un PDF de su estado de cuenta con movimientos legibles,
   **When** el usuario lo sube indicando esa cuenta, **Then** cada movimiento identificable en el PDF
   aparece como una transacción propia de esa cuenta con monto, fecha, tipo (ingreso/gasto) y
   descripción/concepto.
2. **Given** un PDF que referencia una cuenta que no es del usuario o no existe, **When** intenta
   subirlo indicando esa cuenta, **Then** el sistema rechaza la operación sin crear ningún movimiento.

---

### User Story 2 - Ver el progreso y el resultado de la importación (Priority: P1)

El usuario puede consultar el estado de una importación de PDF en curso o finalizada, incluyendo
cuántos movimientos se importaron y el detalle de los que no se pudieron extraer o fallaron al
crearse (por ejemplo, una línea del PDF donde el monto no se pudo interpretar con confianza).

**Why this priority**: La extracción por LLM no es 100% confiable como una API estructurada — el
usuario necesita saber qué se importó y qué no, para poder revisarlo manualmente si hace falta. Sin
esto, un PDF mal interpretado fallaría en silencio.

**Independent Test**: Iniciando una importación con un PDF de prueba conocido (con algunas líneas
deliberadamente ambiguas o ilegibles), el resumen final debe distinguir claramente los movimientos
importados de los que no se pudieron procesar, con un motivo legible para cada uno.

**Acceptance Scenarios**:

1. **Given** una importación de PDF en curso, **When** el usuario consulta su estado, **Then** ve si
   sigue procesándose o ya terminó, junto con la cantidad de movimientos importados hasta el momento.
2. **Given** una importación donde alguna línea del PDF no se pudo interpretar con confianza
   suficiente, **When** el usuario consulta el resultado final, **Then** ve esa línea listada como no
   importada, con un motivo legible, sin que eso impida que el resto de los movimientos sí se hayan
   importado.

---

### User Story 3 - Subir varios PDFs sin duplicar movimientos (Priority: P2)

El usuario puede subir varios estados de cuenta de la misma cuenta (por ejemplo, uno por mes) a lo
largo del tiempo, incluso si dos PDFs cubren un rango de fechas parcialmente solapado, sin que los
movimientos que ya aparecían en un PDF anterior se dupliquen.

**Why this priority**: Es razonable esperar que el usuario suba estados de cuenta de forma recurrente
(mes a mes) y que a veces el rango de fechas se solape con el estado de cuenta anterior. Sin
protección contra duplicados, cada subida repetida ensuciaría el historial de movimientos.

**Independent Test**: Subir el mismo PDF dos veces (o dos PDFs con movimientos que se repiten en un
rango de fechas solapado) y verificar que la cantidad de movimientos de la cuenta no cambia entre la
primera y la segunda subida para los que ya existían.

**Acceptance Scenarios**:

1. **Given** un PDF ya importado exitosamente, **When** el usuario lo vuelve a subir para la misma
   cuenta, **Then** ningún movimiento se duplica.

---

### Edge Cases

- ¿Qué pasa si el PDF no es un estado de cuenta reconocible (está vacío, corrupto, o es un documento
  de otro tipo)? El sistema debe rechazarlo o marcarlo como fallido sin crear movimientos parciales
  incoherentes.
- ¿Qué pasa si el PDF tiene páginas escaneadas como imagen en vez de texto seleccionable? Se marca
  como no procesable si no se puede extraer contenido interpretable, en vez de fallar en silencio.
- ¿Qué pasa si dos movimientos legítimos y distintos tienen exactamente la misma fecha, monto y
  descripción (ej. dos compras idénticas el mismo día)? Ver FR-006 para el criterio de deduplicación.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a un usuario autenticado subir uno o más archivos PDF de
  estados de cuenta, indicando para cada uno a qué cuenta propia (ya existente) pertenece.
- **FR-002**: El sistema DEBE rechazar la subida de un PDF asociado a una cuenta que no exista o no
  pertenezca al usuario autenticado, sin crear ningún movimiento.
- **FR-003**: El sistema DEBE interpretar el contenido del PDF para identificar movimientos
  individuales, extrayendo como mínimo: fecha, monto, tipo (ingreso/gasto) y una descripción o
  concepto.
- **FR-004**: El sistema DEBE crear cada movimiento identificado como una transacción real asociada a
  la cuenta indicada por el usuario, usando el mismo mecanismo de creación de movimientos ya existente
  en la app.
- **FR-005**: El procesamiento de un PDF DEBE ejecutarse en segundo plano, permitiendo al usuario
  consultar su progreso sin bloquear la subida.
- **FR-006**: El sistema DEBE evitar crear movimientos duplicados cuando el mismo PDF (u otro con
  movimientos superpuestos en fecha) se sube más de una vez para la misma cuenta. El criterio de "es
  el mismo movimiento" es la combinación exacta de cuenta + fecha + monto + descripción/concepto — si
  ya existe un movimiento con esos cuatro datos en esa cuenta, se omite. (Limitación aceptada: dos
  movimientos legítimos y distintos con esos cuatro datos idénticos —ej. dos compras iguales el mismo
  día— se tratan como duplicado y solo se importa uno.)
- **FR-007**: El sistema DEBE permitir consultar el estado de una importación de PDF (en curso o
  finalizada), incluyendo cuántos movimientos se importaron.
- **FR-008**: El sistema DEBE registrar y exponer, para cada línea del PDF que no se pudo interpretar
  o crear como movimiento, un motivo legible, sin que eso interrumpa el procesamiento del resto del
  PDF.
- **FR-009**: El sistema DEBE rechazar o marcar como fallida la importación de un PDF sin contenido
  interpretable (vacío, corrupto, o sin texto extraíble), sin crear movimientos parciales.
- **FR-010**: Los movimientos importados DEBEN quedar visibles junto con el resto de los movimientos
  de la cuenta, indistinguibles en su tratamiento salvo por su origen (para trazabilidad).
- **FR-011**: Los movimientos extraídos DEBEN crearse directamente como transacciones reales de la
  cuenta (no quedan en un estado de "propuesta" pendiente de aprobación) — el mismo criterio ya usado
  en la importación de Wallet.
- **FR-012**: Los movimientos importados DEBEN crearse sin categoría asignada; el usuario los
  categoriza manualmente después, igual que cualquier movimiento nuevo.

### Key Entities *(include if feature involves data)*

- **StatementImport**: Una corrida de importación de un PDF — a qué cuenta pertenece, quién la inició,
  su estado (en curso, completada, fallida), cuántos movimientos se importaron, y la lista de líneas
  no procesadas con su motivo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario puede importar un estado de cuenta típico (hasta unas pocas decenas de
  movimientos) y ver los movimientos reflejados en su cuenta en menos de 2 minutos desde que sube el
  PDF.
- **SC-002**: Al menos el 90% de los movimientos de un estado de cuenta con formato estándar (texto
  seleccionable, no escaneado) se importan correctamente sin intervención manual.
- **SC-003**: Subir el mismo estado de cuenta dos veces no produce ningún movimiento duplicado.
- **SC-004**: El usuario puede identificar, sin ayuda externa, cuáles líneas de un PDF no se
  importaron y por qué, a partir del resultado de la importación.

## Assumptions

- Los PDFs contienen texto seleccionable (no son escaneos de solo imagen) en el caso general; el
  soporte de PDFs escaneados/OCR queda fuera de alcance de esta ronda (se marcan como no procesables,
  ver Edge Cases).
- El usuario ya tiene creadas en la app las cuentas a las que corresponden los estados de cuenta que
  va a importar (esta feature no crea cuentas nuevas).
- No se valida que el PDF efectivamente pertenezca al banco/cuenta indicada más allá de lo que el
  usuario declara — se confía en la selección del usuario, igual que en la importación de Wallet.
- Backend-only en esta ronda: no incluye UI en la app Android, igual que el criterio ya usado en la
  feature 005 en su momento.
- No hay límite explícito de tamaño de archivo o cantidad de páginas más allá de límites técnicos
  razonables (se resuelve en el plan técnico, no es una restricción de negocio).
