package com.walletapp.backend.transaction.infrastructure.web.dto;

import com.walletapp.backend.transaction.domain.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// id es opcional (FR-011): si se omite, el servidor genera uno; si se indica, debe ser único para el
// usuario (409 si ya existe) — pensado para creación offline, ver research.md.
public record TransactionRequest(
        UUID id,
        @NotNull TransactionType type,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate date,
        String description,
        @NotNull UUID accountId,
        UUID categoryId) {
}
