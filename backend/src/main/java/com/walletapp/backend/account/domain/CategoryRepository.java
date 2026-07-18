package com.walletapp.backend.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    Category save(Category category);

    List<Category> findAllByUserId(UUID userId);

    Optional<Category> findByIdAndUserId(CategoryId id, UUID userId);

    void deleteByIdAndUserId(CategoryId id, UUID userId);

    boolean existsByUserIdAndTypeAndName(UUID userId, CategoryType type, String name);

    boolean existsByParentCategoryIdAndUserId(CategoryId parentCategoryId, UUID userId);
}
