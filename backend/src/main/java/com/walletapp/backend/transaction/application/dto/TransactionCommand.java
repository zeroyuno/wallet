package com.walletapp.backend.transaction.application.dto;

import com.walletapp.backend.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// id es opcional (FR-011): si viene, el cliente lo generó (creación offline); si no, lo genera el servidor.
public record TransactionCommand(UUID id, TransactionType type, BigDecimal amount, LocalDate date,
                                  String description, UUID accountId, UUID categoryId) {
}
