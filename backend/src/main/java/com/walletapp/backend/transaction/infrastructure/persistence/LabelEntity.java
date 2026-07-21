package com.walletapp.backend.transaction.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "labels", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
public class LabelEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    protected LabelEntity() {
        // JPA
    }

    public LabelEntity(UUID id, UUID userId, String name) {
        this.id = id;
        this.userId = userId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }
}
