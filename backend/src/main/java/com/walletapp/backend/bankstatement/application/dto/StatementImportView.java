package com.walletapp.backend.bankstatement.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StatementImportView(UUID id, UUID accountId, String status, int transactionsImported,
                                   List<StatementLineErrorView> errors, List<StatementImportedLineView> importedLines,
                                   String expenseColumnHeader, String incomeColumnHeader, String failureReason,
                                   Instant startedAt, Instant lastActivityAt) {
}
