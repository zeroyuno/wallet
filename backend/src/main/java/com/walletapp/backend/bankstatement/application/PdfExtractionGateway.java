package com.walletapp.backend.bankstatement.application;

import com.walletapp.backend.bankstatement.application.dto.PdfExtractionResult;

/**
 * Puerto hacia el LLM que interpreta el PDF (research.md #1). La implementación real llama a la API
 * de Anthropic (infrastructure.llmclient); los tests usan un fake en memoria.
 */
public interface PdfExtractionGateway {

    PdfExtractionResult extract(byte[] pdfBytes);
}
