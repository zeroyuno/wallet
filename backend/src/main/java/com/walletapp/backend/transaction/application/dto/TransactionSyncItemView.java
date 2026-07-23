package com.walletapp.backend.transaction.application.dto;

import com.walletapp.backend.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

// Igual shape que TransactionView, más updatedAt (solo relevante para el feed de sincronización,
// feature 007) — se mantiene aparte para no tocar el contrato de create/get/list/update ya existentes.
public record TransactionSyncItemView(UUID id, TransactionType type, BigDecimal amount, LocalDate date,
                                       String description, UUID accountId, UUID categoryId, String counterParty,
                                       String paymentType, String recordState, String walletTransferId,
                                       Set<String> labels, Instant updatedAt) {
}
