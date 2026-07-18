package com.walletapp.backend.transaction.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Sin id, type ni accountId: todos inmutables tras la creación (ver research.md).
public record TransactionUpdateRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate date,
        String description,
        UUID categoryId) {
}
