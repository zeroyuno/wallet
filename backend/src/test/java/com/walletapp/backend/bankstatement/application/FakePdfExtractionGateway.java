package com.walletapp.backend.bankstatement.application;

import com.walletapp.backend.bankstatement.application.dto.ExtractedTransactionDto;
import com.walletapp.backend.bankstatement.application.dto.PdfExtractionResult;
import com.walletapp.backend.bankstatement.application.dto.UnparsedLineDto;
import com.walletapp.backend.bankstatement.domain.exception.PdfExtractionException;

import java.util.ArrayList;
import java.util.List;

/** Fake en memoria del extractor de PDF, usado en tests para no depender de la API de Anthropic. */
public class FakePdfExtractionGateway implements PdfExtractionGateway {

    private final List<ExtractedTransactionDto> transactions = new ArrayList<>();
    private final List<UnparsedLineDto> unparsedLines = new ArrayList<>();
    private boolean shouldFail;

    public FakePdfExtractionGateway withTransactions(ExtractedTransactionDto... transactions) {
        this.transactions.addAll(List.of(transactions));
        return this;
    }

    public FakePdfExtractionGateway withUnparsedLines(UnparsedLineDto... unparsedLines) {
        this.unparsedLines.addAll(List.of(unparsedLines));
        return this;
    }

    public FakePdfExtractionGateway failing() {
        this.shouldFail = true;
        return this;
    }

    @Override
    public PdfExtractionResult extract(byte[] pdfBytes) {
        if (shouldFail) {
            throw new PdfExtractionException("Fallo simulado de extracción");
        }
        return new PdfExtractionResult(List.copyOf(transactions), List.copyOf(unparsedLines));
    }
}
