package com.walletapp.backend.account.infrastructure.persistence;

import com.walletapp.backend.account.domain.CategoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType type;

    @Column(name = "parent_category_id")
    private UUID parentCategoryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CategoryEntity() {
        // JPA
    }

    public CategoryEntity(UUID id, UUID userId, String name, CategoryType type, UUID parentCategoryId,
                           Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.parentCategoryId = parentCategoryId;
        this.createdAt = createdAt;
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

    public CategoryType getType() {
        return type;
    }

    public UUID getParentCategoryId() {
        return parentCategoryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
