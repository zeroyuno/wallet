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
                      "type": {"type": "string", "enum": ["INCOME", "EXPENSE"]},
                      "description": {"type": "string", "description": "Descripción o concepto del movimiento"}
                    },
                    "required": ["date", "amount", "type", "description"]
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
            + "crédito. Identificá cada movimiento (fecha, monto, si es ingreso o gasto, y una "
            + "descripción breve) y llamá a la herramienta " + TOOL_NAME + " con la lista completa. "
            + "Para decidir si un movimiento es ingreso o gasto: si el documento tiene columnas "
            + "separadas para cargos/débitos y abonos/créditos (o nombres equivalentes como "
            + "'Retiro'/'Depósito', 'Débito'/'Crédito', 'Cargo'/'Abono'), usá SIEMPRE en qué columna "
            + "aparece el monto — un monto en la columna de cargos/débitos es EXPENSE, uno en la "
            + "columna de abonos/créditos es INCOME — sin importar lo que sugiera el texto de la "
            + "descripción. Solo si el documento no separa los movimientos en columnas de este tipo, "
            + "inferí el tipo a partir de la descripción. "
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
            transactions.add(new ExtractedTransactionDto(
                    LocalDate.parse(node.path("date").asString()),
                    new BigDecimal(node.path("amount").asString()),
                    node.path("type").asString(),
                    node.path("description").asString()));
        }
        List<UnparsedLineDto> unparsedLines = new ArrayList<>();
        for (JsonNode node : toolInput.path("unparsed_lines")) {
            unparsedLines.add(new UnparsedLineDto(node.path("raw_text").asString(), node.path("reason").asString()));
        }
        return new PdfExtractionResult(transactions, unparsedLines);
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
