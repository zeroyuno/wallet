package com.walletapp.backend.bankstatement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

@Embeddable
public class StatementLineErrorEmbeddable {

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Column(nullable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected StatementLineErrorEmbeddable() {
        // JPA
    }

    public StatementLineErrorEmbeddable(String rawText, String reason, Instant occurredAt) {
        this.rawText = rawText;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public String getRawText() {
        return rawText;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
