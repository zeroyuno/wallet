package com.walletapp.backend.bankstatement.application;

import com.walletapp.backend.bankstatement.application.dto.ExtractedTransactionDto;
import com.walletapp.backend.bankstatement.application.dto.UnparsedLineDto;
import com.walletapp.backend.bankstatement.domain.StatementImport;
import com.walletapp.backend.bankstatement.domain.StatementImportId;
import com.walletapp.backend.bankstatement.domain.StatementImportRepository;
import com.walletapp.backend.bankstatement.domain.StatementImportStatus;
import com.walletapp.backend.bankstatement.domain.StatementLineHashRepository;
import com.walletapp.backend.transaction.application.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementImportProcessorTest {

    @Mock
    StatementImportRepository statementImportRepository;

    @Mock
    TransactionService transactionService;

    // Fake en memoria (no Mockito mock): así save() y findInternalTransactionId() reflejan el mismo
    // estado dentro de una corrida — necesario para probar la deduplicación.
    private final StatementLineHashRepository statementLineHashRepository = new InMemoryStatementLineHashRepository();

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @Test
    void extractsTransactionsAndMarksCompleted() {
        StatementImport statementImport = StatementImport.start(userId, accountId);
        when(statementImportRepository.findById(statementImport.id())).thenReturn(Optional.of(statementImport));
        when(transactionService.createFromImportedTransaction(eq(userId), anyString(), any(), any(), any(),
                eq(accountId), any())).thenReturn(UUID.randomUUID());

        FakePdfExtractionGateway gateway = new FakePdfExtractionGateway()
                .withTransactions(new ExtractedTransactionDto(LocalDate.of(2026, 1, 15), new BigDecimal("50"),
                        "EXPENSE", "Supermercado"));

        StatementImportProcessor processor = new StatementImportProcessor(statementImportRepository,
                statementLineHashRepository, gateway, transactionService);
        processor.run(statementImport.id().value(), new byte[]{1, 2, 3});

        assertThat(statementImport.status()).isEqualTo(StatementImportStatus.COMPLETED);
        assertThat(statementImport.transactionsImported()).isEqualTo(1);
        assertThat(statementImport.errors()).isEmpty();
    }

    @Test
    void recordsUnparsedLinesWithoutHaltingTheImport() {
        StatementImport statementImport = StatementImport.start(userId, accountId);
        when(statementImportRepository.findById(statementImport.id())).thenReturn(Optional.of(statementImport));
        when(transactionService.createFromImportedTransaction(eq(userId), anyString(), any(), any(), any(),
                eq(accountId), any())).thenReturn(UUID.randomUUID());

        FakePdfExtractionGateway gateway = new FakePdfExtractionGateway()
                .withTransactions(new ExtractedTransactionDto(LocalDate.of(2026, 1, 15), new BigDecimal("50"),
                        "EXPENSE", "Supermercado"))
                .withUnparsedLines(new UnparsedLineDto("linea rara $???", "monto ilegible"));

        StatementImportProcessor processor = new StatementImportProcessor(statementImportRepository,
                statementLineHashRepository, gateway, transactionService);
        processor.run(statementImport.id().value(), new byte[]{1, 2, 3});

        assertThat(statementImport.status()).isEqualTo(StatementImportStatus.COMPLETED);
        assertThat(statementImport.transactionsImported()).isEqualTo(1);
        assertThat(statementImport.errors()).hasSize(1);
        assertThat(statementImport.errors().get(0).reason()).isEqualTo("monto ilegible");
    }

    @Test
    void marksFailedWhenExtractionFailsEntirely() {
        StatementImport statementImport = StatementImport.start(userId, accountId);
        when(statementImportRepository.findById(statementImport.id())).thenReturn(Optional.of(statementImport));

        FakePdfExtractionGateway gateway = new FakePdfExtractionGateway().failing();

        StatementImportProcessor processor = new StatementImportProcessor(statementImportRepository,
                statementLineHashRepository, gateway, transactionService);
        processor.run(statementImport.id().value(), new byte[]{1, 2, 3});

        assertThat(statementImport.status()).isEqualTo(StatementImportStatus.FAILED);
        assertThat(statementImport.failureReason()).isNotNull();
        verify(transactionService, never()).createFromImportedTransaction(any(), any(), any(), any(), any(), any(),
                any());
    }

    // FR-006: un movimiento con la misma cuenta+fecha+monto+descripción ya importado se omite.
    @Test
    void skipsAlreadyImportedTransactionsOnRerun() {
        StatementImport statementImport = StatementImport.start(userId, accountId);
        when(statementImportRepository.findById(statementImport.id())).thenReturn(Optional.of(statementImport));
        statementLineHashRepository.save(userId, accountId,
                hashFor(accountId, LocalDate.of(2026, 1, 15), new BigDecimal("50"), "supermercado"),
                UUID.randomUUID());

        FakePdfExtractionGateway gateway = new FakePdfExtractionGateway()
                .withTransactions(new ExtractedTransactionDto(LocalDate.of(2026, 1, 15), new BigDecimal("50"),
                        "EXPENSE", "Supermercado"));

        StatementImportProcessor processor = new StatementImportProcessor(statementImportRepository,
                statementLineHashRepository, gateway, transactionService);
        processor.run(statementImport.id().value(), new byte[]{1, 2, 3});

        assertThat(statementImport.transactionsImported()).isZero();
        verify(transactionService, never()).createFromImportedTransaction(any(), any(), any(), any(), any(), any(),
                any());
    }

    private static String hashFor(UUID accountId, LocalDate date, BigDecimal amount, String description) {
        // Replica la misma normalización que StatementImportProcessor#hash (privado, ver esa clase).
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            String raw = accountId + "|" + date + "|" + amount.toPlainString() + "|" + description;
            return java.util.HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class InMemoryStatementLineHashRepository implements StatementLineHashRepository {
        private final Map<String, UUID> store = new HashMap<>();

        @Override
        public Optional<UUID> findInternalTransactionId(UUID userId, String hash) {
            return Optional.ofNullable(store.get(userId + "|" + hash));
        }

        @Override
        public void save(UUID userId, UUID accountId, String hash, UUID internalTransactionId) {
            store.put(userId + "|" + hash, internalTransactionId);
        }
    }
}
