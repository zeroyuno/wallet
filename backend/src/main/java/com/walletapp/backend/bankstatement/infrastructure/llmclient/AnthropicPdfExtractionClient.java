package com.walletapp.backend.bankstatement.infrastructure.llmclient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walletapp.backend.bankstatement.application.PdfExtractionGateway;
import com.walletapp.backend.bankstatement.application.dto.ExtractedTransactionDto;
import com.walletapp.backend.bankstatement.application.dto.PdfExtractionResult;
import com.walletapp.backend.bankstatement.application.dto.UnparsedLineDto;
import com.walletapp.backend.bankstatement.domain.exception.PdfExtractionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Adaptador hacia la API de Mensajes de Anthropic (research.md #1, #1b). Manda el PDF completo como
 * documento y fuerza tool-use (`tool_choice`) para garantizar una respuesta JSON contra un schema
 * fijo, en vez de parsear texto libre. Modelo: Claude Sonnet 5 — se probó primero con Haiku 4.5 por
 * costo, pero Haiku confundía la asociación fila-columna cuando dos movimientos compartían fecha y
 * monto (research.md #8); Sonnet lee la tabla con más precisión a cambio de ~2x el costo por PDF.
 */
@Component
class AnthropicPdfExtractionClient implements PdfExtractionGateway {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String MODEL = "claude-sonnet-5";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 8192;

    private static final String TOOL_NAME = "record_transactions";
    private static final String TOOL_DESCRIPTION = "Registra los movimientos identificados en el "
            + "estado de cuenta y las líneas que no se pudieron interpretar con confianza.";

    // language=json
    private static final String TOOL_INPUT_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "expense_column_header": {"type": "string", "description": "El encabezado EXACTO y COMPLETO (tal cual aparece en el documento, sin recortar ni traducir) de la columna donde aparecen los montos que SALEN de la cuenta (gastos, cargos, débitos, retiros — sin importar cómo lo llame este banco en particular). String vacío si el documento no separa los movimientos en columnas de este tipo."},
                "income_column_header": {"type": "string", "description": "El encabezado EXACTO y COMPLETO (tal cual aparece en el documento, sin recortar ni traducir) de la columna donde aparecen los montos que ENTRAN a la cuenta (ingresos, abonos, créditos, depósitos — sin importar cómo lo llame este banco en particular). String vacío si el documento no separa los movimientos en columnas de este tipo."},
                "transactions": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "date": {"type": "string", "description": "Fecha del movimiento, formato ISO 8601 YYYY-MM-DD"},
                      "amount": {"type": "number", "description": "Magnitud del monto, siempre positiva"},
                      "column_header": {"type": "string", "description": "Para este movimiento puntual: copiá acá EXACTAMENTE (carácter por carácter) el valor de expense_column_header o income_column_header, el que corresponda según en qué columna esté el monto de esta fila. String vacío si expense_column_header e income_column_header están vacíos."},
                      "type": {"type": "string", "enum": ["INCOME", "EXPENSE"], "description": "Si column_header no está vacío, DEBE ser el tipo que corresponde a esa columna (income_column_header -> INCOME, expense_column_header -> EXPENSE). Si column_header está vacío, inferilo de la descripción."},
                      "description": {"type": "string", "description": "Descripción o concepto del movimiento"}
                    },
                    "required": ["date", "amount", "column_header", "type", "description"]
                  }
                },
                "unparsed_lines": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "raw_text": {"type": "string"},
                      "reason": {"type": "string"}
                    },
                    "required": ["raw_text", "reason"]
                  }
                }
              },
              "required": ["expense_column_header", "income_column_header", "transactions", "unparsed_lines"]
            }
            """;

    private static final String PROMPT = "Este PDF es un estado de cuenta bancario o de tarjeta de "
            + "crédito. Antes de listar los movimientos, mirá los encabezados de columna del "
            + "documento y determiná: ¿cuál es la columna donde aparecen los montos que SALEN de la "
            + "cuenta (gastos), y cuál la de los montos que ENTRAN (ingresos)? Copiá esos dos "
            + "encabezados literalmente (tal cual están escritos, sin traducir ni resumir) en "
            + "expense_column_header e income_column_header — esto puede variar mucho de un banco a "
            + "otro (cargo/abono, débito/crédito, retiro/depósito, debe/haber, u otra terminología), "
            + "así que fijate en el documento real, no asumas nombres. Si el documento no separa los "
            + "movimientos en columnas de este tipo, dejá ambos vacíos. "
            + "Después, identificá cada movimiento (fecha, monto, en cuál de esas dos columnas está, "
            + "si es ingreso o gasto, y una descripción breve) y llamá a la herramienta " + TOOL_NAME
            + " con todo. Para cada movimiento, copiá en column_header el mismo texto EXACTO que ya "
            + "pusiste en expense_column_header o income_column_header (el que corresponda) — y el "
            + "tipo tiene que ser consistente con esa columna, sin importar lo que sugiera el texto de "
            + "la descripción (ej. una fila que dice \"Pago YAPE de X\" en la columna de ingresos es "
            + "INCOME, no EXPENSE, aunque la palabra \"Pago\" sugiera lo contrario). Prestá atención "
            + "fila por fila: no asumas el tipo de una fila por otras filas con descripción parecida — "
            + "cada una puede estar en una columna distinta. "
            + "Si alguna línea no se puede interpretar con confianza suficiente (monto ambiguo, fecha "
            + "ilegible, etc.), incluila en unparsed_lines con el texto tal cual aparece y el motivo, "
            + "en vez de adivinar.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final JsonNode toolInputSchema;

    AnthropicPdfExtractionClient(@Value("${app.anthropic.api-key}") String apiKey, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
        this.toolInputSchema = objectMapper.readTree(TOOL_INPUT_SCHEMA_JSON);
    }

    @Override
    public PdfExtractionResult extract(byte[] pdfBytes) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new PdfExtractionException("ANTHROPIC_API_KEY no está configurada");
        }

        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);
        AnthropicRequest request = new AnthropicRequest(MODEL, MAX_TOKENS,
                List.of(new AnthropicMessage("user", List.of(
                        new AnthropicDocumentBlock("document",
                                new AnthropicDocumentSource("base64", "application/pdf", base64Pdf)),
                        new AnthropicTextBlock("text", PROMPT)))),
                List.of(new AnthropicTool(TOOL_NAME, TOOL_DESCRIPTION, toolInputSchema)),
                new AnthropicToolChoice("tool", TOOL_NAME));

        AnthropicResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AnthropicResponse.class);
        } catch (RuntimeException e) {
            throw new PdfExtractionException("Fallo al llamar a la API de Anthropic: " + e.getMessage(), e);
        }

        JsonNode toolInput = findToolUseInput(response);
        return toResult(toolInput);
    }

    private JsonNode findToolUseInput(AnthropicResponse response) {
        if (response == null || response.content() == null) {
            throw new PdfExtractionException("Respuesta vacía de la API de Anthropic");
        }
        return response.content().stream()
                .filter(block -> "tool_use".equals(block.type()) && TOOL_NAME.equals(block.name()))
                .map(AnthropicResponseContentBlock::input)
                .findFirst()
                .orElseThrow(() -> new PdfExtractionException(
                        "La API de Anthropic no devolvió el resultado esperado (sin tool_use)"));
    }

    private PdfExtractionResult toResult(JsonNode toolInput) {
        String expenseHeader = normalizeHeader(toolInput.path("expense_column_header").asString(""));
        String incomeHeader = normalizeHeader(toolInput.path("income_column_header").asString(""));

        List<ExtractedTransactionDto> transactions = new ArrayList<>();
        for (JsonNode node : toolInput.path("transactions")) {
            String modelType = node.path("type").asString();
            String columnHeader = normalizeHeader(node.path("column_header").asString(""));
            transactions.add(new ExtractedTransactionDto(
                    LocalDate.parse(node.path("date").asString()),
                    new BigDecimal(node.path("amount").asString()),
                    resolveType(modelType, columnHeader, expenseHeader, incomeHeader),
                    node.path("description").asString(),
                    columnHeader));
        }
        List<UnparsedLineDto> unparsedLines = new ArrayList<>();
        for (JsonNode node : toolInput.path("unparsed_lines")) {
            unparsedLines.add(new UnparsedLineDto(node.path("raw_text").asString(), node.path("reason").asString()));
        }
        return new PdfExtractionResult(transactions, unparsedLines, expenseHeader, incomeHeader);
    }

    // Corrección determinística sobre el juicio del modelo, sin diccionario propio (research.md #8):
    // a veces el modelo identifica bien la columna de cada fila (column_header) pero igual asigna un
    // type inconsistente (sobre todo cuando la descripción "suena" al tipo contrario, ej. "Pago YAPE
    // de X" es un ingreso pese a la palabra "Pago"). En vez de mantener una lista de palabras clave
    // bancarias (no escala: cada banco/país usa una terminología distinta), se compara el
    // column_header de cada fila contra expense_column_header/income_column_header que el propio
    // modelo identificó una sola vez al principio del documento — es el modelo el que entiende el
    // idioma/terminología de este banco en particular, el código solo hace un match de texto exacto.
    private static String resolveType(String modelType, String columnHeader, String expenseHeader,
                                       String incomeHeader) {
        if (columnHeader.isEmpty()) {
            return modelType;
        }
        if (!expenseHeader.isEmpty() && columnHeader.equals(expenseHeader)) {
            return "EXPENSE";
        }
        if (!incomeHeader.isEmpty() && columnHeader.equals(incomeHeader)) {
            return "INCOME";
        }
        return modelType;
    }

    private static String normalizeHeader(String header) {
        return header == null ? "" : header.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AnthropicRequest(String model, @JsonProperty("max_tokens") int maxTokens,
                                     List<AnthropicMessage> messages, List<AnthropicTool> tools,
                                     @JsonProperty("tool_choice") AnthropicToolChoice toolChoice) {
    }

    // content es List<Object> a propósito: cada bloque (documento, texto) tiene sus propios campos
    // y ninguno más — la API de Anthropic rechaza con 400 "Extra inputs are not permitted" si un
    // bloque de tipo "document" incluye una clave "text" aunque sea null (un solo record con todos
    // los campos posibles serializaría esa clave igual).
    private record AnthropicMessage(String role, List<Object> content) {
    }

    private record AnthropicDocumentBlock(String type, AnthropicDocumentSource source) {
    }

    private record AnthropicTextBlock(String type, String text) {
    }

    private record AnthropicDocumentSource(String type, @JsonProperty("media_type") String mediaType, String data) {
    }

    private record AnthropicTool(String name, String description,
                                  @JsonProperty("input_schema") JsonNode inputSchema) {
    }

    private record AnthropicToolChoice(String type, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AnthropicResponse(List<AnthropicResponseContentBlock> content,
                                      @JsonProperty("stop_reason") String stopReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AnthropicResponseContentBlock(String type, String name, JsonNode input) {
    }
}
