package com.walletapp.backend.account.infrastructure.persistence;

import com.walletapp.backend.account.domain.Category;
import com.walletapp.backend.account.domain.CategoryId;
import com.walletapp.backend.account.domain.CategoryRepository;
import com.walletapp.backend.account.domain.CategoryType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaCategoryRepository implements CategoryRepository {

    private final SpringDataCategoryRepository springDataRepository;

    JpaCategoryRepository(SpringDataCategoryRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Category save(Category category) {
        CategoryEntity saved = springDataRepository.save(toEntity(category));
        return toDomain(saved);
    }

    @Override
    public List<Category> findAllByUserId(UUID userId) {
        return springDataRepository.findAllByUserId(userId).stream().map(JpaCategoryRepository::toDomain).toList();
    }

    @Override
    public Optional<Category> findByIdAndUserId(CategoryId id, UUID userId) {
        return springDataRepository.findByIdAndUserId(id.value(), userId).map(JpaCategoryRepository::toDomain);
    }

    @Override
    public void deleteByIdAndUserId(CategoryId id, UUID userId) {
        springDataRepository.deleteByIdAndUserId(id.value(), userId);
    }

    @Override
    public boolean existsByUserIdAndTypeAndName(UUID userId, CategoryType type, String name) {
        return springDataRepository.existsByUserIdAndTypeAndName(userId, type, name);
    }

    private static CategoryEntity toEntity(Category category) {
        return new CategoryEntity(
                category.id().value(),
                category.userId(),
                category.name(),
                category.type(),
                category.parentCategoryId().map(CategoryId::value).orElse(null),
                category.createdAt());
    }

    private static Category toDomain(CategoryEntity entity) {
        CategoryId parentId = entity.getParentCategoryId() == null ? null : CategoryId.of(entity.getParentCategoryId());
        return Category.reconstitute(
                CategoryId.of(entity.getId()),
                entity.getUserId(),
                entity.getName(),
                entity.getType(),
                parentId,
                entity.getCreatedAt());
    }
}
