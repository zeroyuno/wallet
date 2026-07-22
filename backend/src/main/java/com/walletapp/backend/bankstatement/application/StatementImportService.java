package com.walletapp.backend.bankstatement.application;

import com.walletapp.backend.account.application.AccountService;
import com.walletapp.backend.bankstatement.application.dto.StatementImportView;
import com.walletapp.backend.bankstatement.application.dto.StatementLineErrorView;
import com.walletapp.backend.bankstatement.domain.StatementImport;
import com.walletapp.backend.bankstatement.domain.StatementImportId;
import com.walletapp.backend.bankstatement.domain.StatementImportRepository;
import com.walletapp.backend.bankstatement.domain.StatementLineError;
import com.walletapp.backend.bankstatement.domain.exception.InvalidStatementAccountException;
import com.walletapp.backend.bankstatement.domain.exception.StatementImportNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * A propósito SIN {@code @Transactional} de clase — mismo motivo que ImportService de la feature
 * 005: el guardado inicial del {@code StatementImport} debe quedar confirmado (commit) ANTES de que
 * el {@code @Async} de {@link StatementImportProcessor} lo busque en otro hilo.
 */
@Component
public class StatementImportService {

    private final StatementImportRepository statementImportRepository;
    private final AccountService accountService;
    private final StatementImportProcessor statementImportProcessor;

    public StatementImportService(StatementImportRepository statementImportRepository,
                                   AccountService accountService, StatementImportProcessor statementImportProcessor) {
        this.statementImportRepository = statementImportRepository;
        this.accountService = accountService;
        this.statementImportProcessor = statementImportProcessor;
    }

    // FR-002: la cuenta se valida ANTES de crear el registro o llamar al LLM.
    public StatementImportView start(UUID userId, UUID accountId, byte[] pdfBytes) {
        if (!accountService.existsOwnedByUser(userId, accountId)) {
            throw new InvalidStatementAccountException("Account not found or not owned: " + accountId);
        }
        StatementImport statementImport = StatementImport.start(userId, accountId);
        statementImport = statementImportRepository.save(statementImport);
        statementImportProcessor.run(statementImport.id().value(), pdfBytes);
        return toView(statementImport);
    }

    public StatementImportView get(UUID userId, UUID statementImportId) {
        StatementImport statementImport = statementImportRepository
                .findByIdAndUserId(StatementImportId.of(statementImportId), userId)
                .orElseThrow(() -> new StatementImportNotFoundException(
                        "Statement import not found: " + statementImportId));
        return toView(statementImport);
    }

    private static StatementImportView toView(StatementImport statementImport) {
        return new StatementImportView(statementImport.id().value(), statementImport.accountId(),
                statementImport.status().name(), statementImport.transactionsImported(),
                statementImport.errors().stream().map(StatementImportService::toErrorView).toList(),
                statementImport.failureReason(), statementImport.startedAt(), statementImport.lastActivityAt());
    }

    private static StatementLineErrorView toErrorView(StatementLineError error) {
        return new StatementLineErrorView(error.rawText(), error.reason());
    }
}
