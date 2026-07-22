package com.walletapp.backend.bankstatement.application.dto;

import java.util.List;

public record PdfExtractionResult(List<ExtractedTransactionDto> transactions, List<UnparsedLineDto> unparsedLines) {
}
