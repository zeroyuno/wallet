package com.walletapp.backend.transaction.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Sin type ni accountId: ambos son inmutables tras la creación (FR-005, ver research.md).
public record TransactionUpdateCommand(BigDecimal amount, LocalDate date, String description, UUID categoryId) {
}
