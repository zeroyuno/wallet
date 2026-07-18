package com.walletapp.backend.account.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Category {

    private final CategoryId id;
    private final UUID userId;
    private String name;
    private final CategoryType type;
    private CategoryId parentCategoryId;
    private final Instant createdAt;

    private Category(CategoryId id, UUID userId, String name, CategoryType type, CategoryId parentCategoryId,
                      Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.parentCategoryId = parentCategoryId;
        this.createdAt = createdAt;
    }

    public static Category create(UUID userId, String name, CategoryType type, CategoryId parentCategoryId) {
        requireNonBlankName(name);
        Category category = new Category(CategoryId.newId(), userId, name.trim(), type, null, Instant.now());
        category.parentCategoryId = parentCategoryId;
        if (parentCategoryId != null && parentCategoryId.equals(category.id)) {
            throw new IllegalArgumentException("A category cannot be its own parent");
        }
        return category;
    }

    public static Category reconstitute(CategoryId id, UUID userId, String name, CategoryType type,
                                         CategoryId parentCategoryId, Instant createdAt) {
        return new Category(id, userId, name, type, parentCategoryId, createdAt);
    }

    public void rename(String name, CategoryId parentCategoryId) {
        requireNonBlankName(name);
        if (parentCategoryId != null && parentCategoryId.equals(this.id)) {
            throw new IllegalArgumentException("A category cannot be its own parent");
        }
        this.name = name.trim();
        this.parentCategoryId = parentCategoryId;
    }

    private static void requireNonBlankName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
    }

    public CategoryId id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public CategoryType type() {
        return type;
    }

    public Optional<CategoryId> parentCategoryId() {
        return Optional.ofNullable(parentCategoryId);
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category category)) return false;
        return Objects.equals(id, category.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
