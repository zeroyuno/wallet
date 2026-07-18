package com.walletapp.backend.transaction.application.dto;

import java.time.LocalDate;
import java.util.UUID;

// Todos los campos son opcionales (nullable) — sin filtrar por ese criterio si es null (FR-004).
public record TransactionFilter(UUID accountId, UUID categoryId, LocalDate dateFrom, LocalDate dateTo) {

    public static TransactionFilter none() {
        return new TransactionFilter(null, null, null, null);
    }
}
