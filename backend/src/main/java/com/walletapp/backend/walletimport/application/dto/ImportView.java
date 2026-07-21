package com.walletapp.backend.walletimport.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportView(UUID id, String status, int accountsImported, int categoriesImported,
                          int transactionsImported, List<ImportErrorView> errors, Instant startedAt,
                          Instant lastActivityAt) {
}
