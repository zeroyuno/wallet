package com.walletapp.backend.transaction.infrastructure.web.dto;

import com.walletapp.backend.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        BigDecimal amount,
        LocalDate date,
        String description,
        UUID accountId,
        UUID categoryId,
        String counterParty,
        String paymentType,
        String recordState,
        String walletTransferId,
        Set<String> labels) {
}
