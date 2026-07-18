package com.walletapp.backend.account.infrastructure.persistence;

import com.walletapp.backend.account.domain.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataCategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findAllByUserId(UUID userId);

    Optional<CategoryEntity> findByIdAndUserId(UUID id, UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndTypeAndName(UUID userId, CategoryType type, String name);

    boolean existsByParentCategoryIdAndUserId(UUID parentCategoryId, UUID userId);
}
