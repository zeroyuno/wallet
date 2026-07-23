package com.walletapp.backend.transaction.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionSyncResult(List<TransactionSyncItemView> upserts, List<UUID> deletedIds, Instant nextSince,
                                     boolean hasMore, long totalRemaining) {
}
