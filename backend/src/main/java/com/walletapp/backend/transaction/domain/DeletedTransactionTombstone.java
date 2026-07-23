package com.walletapp.backend.transaction.domain;

import java.time.Instant;
import java.util.UUID;

// Registro de que una transacción fue eliminada, para que el feed de sincronización (feature 007) le
// avise al cliente sin necesidad de convertir `transactions` a soft-delete (ver research.md #2).
public record DeletedTransactionTombstone(UUID id, UUID userId, Instant deletedAt) {
}
