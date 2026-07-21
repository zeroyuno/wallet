package com.walletapp.backend.walletimport.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportResponse(UUID id, String status, int accountsImported, int categoriesImported,
                              int transactionsImported, List<ImportErrorItemResponse> errors, Instant startedAt,
                              Instant lastActivityAt) {
}
