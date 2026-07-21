package com.walletapp.backend.walletimport.infrastructure.persistence;

import com.walletapp.backend.walletimport.domain.ExternalEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "import_external_refs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "entity_type", "external_id"}))
public class ExternalReferenceEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private ExternalEntityType entityType;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "internal_id", nullable = false)
    private UUID internalId;

    protected ExternalReferenceEntity() {
        // JPA
    }

    public ExternalReferenceEntity(UUID id, UUID userId, ExternalEntityType entityType, String externalId,
                                    UUID internalId) {
        this.id = id;
        this.userId = userId;
        this.entityType = entityType;
        this.externalId = externalId;
        this.internalId = internalId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ExternalEntityType getEntityType() {
        return entityType;
    }

    public String getExternalId() {
        return externalId;
    }

    public UUID getInternalId() {
        return internalId;
    }
}
