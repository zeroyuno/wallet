package com.walletapp.backend.walletimport.domain;

import java.time.Instant;
import java.util.Objects;

public record ImportError(ExternalEntityType entityType, String externalId, String reason, Instant occurredAt) {

    public ImportError {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(externalId, "externalId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
