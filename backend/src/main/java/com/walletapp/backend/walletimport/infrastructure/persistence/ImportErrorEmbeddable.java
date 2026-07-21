package com.walletapp.backend.walletimport.infrastructure.persistence;

import com.walletapp.backend.walletimport.domain.ExternalEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

@Embeddable
public class ImportErrorEmbeddable {

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private ExternalEntityType entityType;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ImportErrorEmbeddable() {
        // JPA
    }

    public ImportErrorEmbeddable(ExternalEntityType entityType, String externalId, String reason,
                                  Instant occurredAt) {
        this.entityType = entityType;
        this.externalId = externalId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public ExternalEntityType getEntityType() {
        return entityType;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
