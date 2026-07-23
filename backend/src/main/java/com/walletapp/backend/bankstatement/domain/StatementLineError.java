package com.walletapp.backend.bankstatement.domain;

import java.time.Instant;
import java.util.Objects;

public record StatementLineError(String rawText, String reason, Instant occurredAt) {

    public StatementLineError {
        Objects.requireNonNull(rawText, "rawText must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
