package com.walletapp.backend.transaction.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionSyncResponse(List<TransactionSyncItemResponse> upserts, List<UUID> deletedIds,
                                       Instant nextSince, boolean hasMore, long totalRemaining) {
}
