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
import java.util.Set;

/**
 * Adaptador hacia la API de Mensajes de Anthropic (research.md #1, #1b). Manda el PDF completo como
 * documento y fuerza tool-use (`tool_choice`) para garantizar una respuesta JSON contra un schema
 * fijo, en vez de parsear texto libre. Modelo: Claude Haiku 4.5 — suficiente para extracción
 * estructurada de una tabla, mucho más barato que Sonnet/Opus para esta tarea puntual.
 */
@Component
class AnthropicPdfExtractionClient implements PdfExtractionGateway {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String MODEL = "claude-haiku-4-5-20251001";
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
                "transactions": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "date": {"type": "string", "description": "Fecha del movimiento, formato ISO 8601 YYYY-MM-DD"},
                      "amount": {"type": "number", "description": "Magnitud del monto, siempre positiva"},
                      "source_column": {"type": "string", "description": "Texto EXACTO del encabezado de la columna en la que aparece el monto de este movimiento en el documento (ej. 'Cargo', 'Abono', 'Débito', 'Crédito', 'Retiro', 'Depósito'). Dejar como string vacío si el documento no separa los movimientos en columnas de este tipo."},
                      "type": {"type": "string", "enum": ["INCOME", "EXPENSE"], "description": "Si source_column no está vacío, DEBE ser consistente con esa columna (cargo/débito/retiro -> EXPENSE, abono/crédito/depósito -> INCOME). Si source_column está vacío, inferilo de la descripción."},
                      "description": {"type": "string", "description": "Descripción o concepto del movimiento"}
                    },
                    "required": ["date", "amount", "source_column", "type", "description"]
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
              "required": ["transactions", "unparsed_lines"]
            }
            """;

    private static final String PROMPT = "Este PDF es un estado de cuenta bancario o de tarjeta de "
            + "crédito. Identificá cada movimiento (fecha, monto, columna de origen, si es ingreso o "
            + "gasto, y una descripción breve) y llamá a la herramienta " + TOOL_NAME + " con la lista "
            + "completa. "
            + "Para cada movimiento, ANTES de decidir el tipo, fijate en qué columna del documento "
            + "aparece el monto y anotalo literalmente en source_column (ej. 'Cargo', 'Abono'). Recién "
            + "después, con eso ya identificado, asigná el tipo: un monto en una columna de "
            + "cargo/débito/retiro es EXPENSE, uno en abono/crédito/depósito es INCOME — esto vale "
            + "SIEMPRE que el documento tenga esa separación en columnas, sin importar lo que sugiera "
            + "el texto de la descripción (ej. una fila que dice \"Pago YAPE de X\" en la columna de "
            + "abonos es INCOME, no EXPENSE, aunque la palabra \"Pago\" sugiera lo contrario). Prestá "
            + "atención fila por fila: no asumas el tipo de una fila por otras filas con descripción "
            + "parecida — cada una puede estar en una columna distinta. Solo si el documento no separa "
            + "los movimientos en columnas de este tipo, dejá source_column vacío e inferí el tipo a "
            + "partir de la descripción. "
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
        List<ExtractedTransactionDto> transactions = new ArrayList<>();
        for (JsonNode node : toolInput.path("transactions")) {
            String modelType = node.path("type").asString();
            String sourceColumn = node.path("source_column").asString("");
            transactions.add(new ExtractedTransactionDto(
                    LocalDate.parse(node.path("date").asString()),
                    new BigDecimal(node.path("amount").asString()),
                    resolveType(modelType, sourceColumn),
                    node.path("description").asString()));
        }
        List<UnparsedLineDto> unparsedLines = new ArrayList<>();
        for (JsonNode node : toolInput.path("unparsed_lines")) {
            unparsedLines.add(new UnparsedLineDto(node.path("raw_text").asString(), node.path("reason").asString()));
        }
        return new PdfExtractionResult(transactions, unparsedLines);
    }

    // Corrección determinística sobre el juicio del modelo (research.md #7, T026): a veces el
    // modelo declara correctamente la columna en source_column pero igual asigna el type que no le
    // corresponde (sobre todo cuando la descripción "suena" al tipo contrario, ej. "Pago YAPE de X"
    // es un ingreso pese a la palabra "Pago"). Si la columna reportada matchea un término bancario
    // conocido, esa columna manda sobre el type del modelo; si no matchea nada (documento sin
    // columnas separadas), se respeta el type que ya infirió el modelo por descripción.
    private static final Set<String> INCOME_COLUMN_KEYWORDS = Set.of("abono", "credito", "crédito", "deposito",
            "depósito", "ingreso");
    private static final Set<String> EXPENSE_COLUMN_KEYWORDS = Set.of("cargo", "debito", "débito", "retiro",
            "egreso");

    private static String resolveType(String modelType, String sourceColumn) {
        String normalized = sourceColumn == null ? "" : sourceColumn.toLowerCase().trim();
        if (INCOME_COLUMN_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return "INCOME";
        }
        if (EXPENSE_COLUMN_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return "EXPENSE";
        }
        return modelType;
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
