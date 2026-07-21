package com.walletapp.backend.transaction.application.dto;

import com.walletapp.backend.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

// counterParty/paymentType/recordState/walletTransferId/labels: solo poblados cuando la transacción
// vino de una importación de BudgetBakers Wallet (feature 005) — null/vacío en el resto de los casos.
public record TransactionView(UUID id, TransactionType type, BigDecimal amount, LocalDate date, String description,
                               UUID accountId, UUID categoryId, String counterParty, String paymentType,
                               String recordState, String walletTransferId, Set<String> labels) {
}
