package com.walletapp.backend.transaction.application.dto;

import com.walletapp.backend.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionView(UUID id, TransactionType type, BigDecimal amount, LocalDate date, String description,
                               UUID accountId, UUID categoryId) {
}
