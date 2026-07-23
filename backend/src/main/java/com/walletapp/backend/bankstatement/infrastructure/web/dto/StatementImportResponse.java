package com.walletapp.backend.bankstatement.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StatementImportResponse(UUID id, UUID accountId, String status, int transactionsImported,
                                       List<StatementLineErrorResponse> errors,
                                       List<StatementImportedLineResponse> importedLines, String expenseColumnHeader,
                                       String incomeColumnHeader, String failureReason, Instant startedAt,
                                       Instant lastActivityAt) {
}
