package com.walletapp.backend.bankstatement.application;

import com.walletapp.backend.bankstatement.application.dto.ExtractedTransactionDto;
import com.walletapp.backend.bankstatement.application.dto.PdfExtractionResult;
import com.walletapp.backend.bankstatement.application.dto.UnparsedLineDto;
import com.walletapp.backend.bankstatement.domain.StatementImport;
import com.walletapp.backend.bankstatement.domain.StatementImportId;
import com.walletapp.backend.bankstatement.domain.StatementImportRepository;
import com.walletapp.backend.bankstatement.domain.StatementLineHashRepository;
import com.walletapp.backend.bankstatement.domain.exception.PdfExtractionException;
import com.walletapp.backend.transaction.application.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta la extracción e importación en segundo plano (research.md #4). Bean aparte de
 * {@link StatementImportService} por la misma razón que ImportProcessor de la feature 005: así el
 * {@code @Async} se dispara a través del proxy de Spring cuando lo invoca un colaborador inyectado,
 * no una auto-invocación dentro de la misma clase.
 */
@Component
class StatementImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(StatementImportProcessor.class);

    private final StatementImportRepository statementImportRepository;
    private final StatementLineHashRepository statementLineHashRepository;
    private final PdfExtractionGateway pdfExtractionGateway;
    private final TransactionService transactionService;

    StatementImportProcessor(StatementImportRepository statementImportRepository,
                              StatementLineHashRepository statementLineHashRepository,
                              PdfExtractionGateway pdfExtractionGateway, TransactionService transactionService) {
        this.statementImportRepository = statementImportRepository;
        this.statementLineHashRepository = statementLineHashRepository;
        this.pdfExtractionGateway = pdfExtractionGateway;
        this.transactionService = transactionService;
    }

    @Async
    void run(UUID statementImportId, byte[] pdfBytes) {
        StatementImport statementImport = statementImportRepository.findById(StatementImportId.of(statementImportId))
                .orElseThrow(() -> new IllegalStateException("Statement import not found: " + statementImportId));
        try {
            PdfExtractionResult result = pdfExtractionGateway.extract(pdfBytes);
            statementImport.recordColumnHeaders(result.expenseColumnHeader(), result.incomeColumnHeader());
            for (ExtractedTransactionDto transaction : result.transactions()) {
                importOneTransaction(statementImport, transaction);
            }
            for (UnparsedLineDto unparsedLine : result.unparsedLines()) {
                statementImport.recordLineError(unparsedLine.rawText(), unparsedLine.reason());
            }
            statementImport.markCompleted();
        } catch (PdfExtractionException e) {
            log.warn("Statement import {} failed: {}", statementImportId, e.getMessage());
            statementImport.markFailed(e.getMessage());
        }
        statementImportRepository.save(statementImport);
    }

    private void importOneTransaction(StatementImport statementImport, ExtractedTransactionDto transaction) {
        String hash = hash(statementImport.accountId(), transaction);
        Optional<UUID> existing = statementLineHashRepository
                .findInternalTransactionId(statementImport.userId(), hash);
        if (existing.isPresent()) {
            return;
        }
        try {
            UUID internalId = transactionService.createFromImportedTransaction(statementImport.userId(),
                    transaction.type(), transaction.amount(), transaction.date(), transaction.description(),
                    statementImport.accountId(), null);
            statementLineHashRepository.save(statementImport.userId(), statementImport.accountId(), hash,
                    internalId);
            statementImport.recordTransactionImported(transaction.date(), transaction.amount(), transaction.type(),
                    transaction.description(), transaction.columnHeader());
        } catch (RuntimeException e) {
            statementImport.recordLineError(transaction.description(), e.getMessage());
        }
    }

    // Deduplicación sin id externo (research.md #3): hash de cuenta+fecha+monto+descripción
    // normalizada — limitación aceptada y documentada en spec.md (dos movimientos legítimos
    // idénticos se tratan como uno solo).
    private static String hash(UUID accountId, ExtractedTransactionDto transaction) {
        String raw = accountId + "|" + transaction.date() + "|" + transaction.amount().toPlainString() + "|"
                + transaction.description().trim().toLowerCase();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
