package com.walletapp.backend.account.application;

import com.walletapp.backend.account.application.dto.CategoryCommand;
import com.walletapp.backend.account.application.dto.CategoryView;
import com.walletapp.backend.account.domain.Category;
import com.walletapp.backend.account.domain.CategoryId;
import com.walletapp.backend.account.domain.CategoryRepository;
import com.walletapp.backend.account.domain.CategoryType;
import com.walletapp.backend.account.domain.exception.CategoryHasChildrenException;
import com.walletapp.backend.account.domain.exception.CategoryNotFoundException;
import com.walletapp.backend.account.domain.exception.DuplicateCategoryException;
import com.walletapp.backend.account.domain.exception.InvalidCategoryHierarchyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public CategoryView create(UUID userId, CategoryCommand command) {
        if (categoryRepository.existsByUserIdAndTypeAndName(userId, command.type(), command.name())) {
            throw new DuplicateCategoryException(
                    "Category already exists: " + command.name() + " (" + command.type() + ")");
        }
        validateParent(userId, command.type(), null, command.parentCategoryId());
        CategoryId parentId = command.parentCategoryId() == null ? null : CategoryId.of(command.parentCategoryId());
        Category category = Category.create(userId, command.name(), command.type(), parentId);
        return toView(categoryRepository.save(category));
    }

    public List<CategoryView> list(UUID userId) {
        return categoryRepository.findAllByUserId(userId).stream().map(CategoryService::toView).toList();
    }

    public CategoryView update(UUID userId, CategoryId id, CategoryCommand command) {
        Category category = findOwned(userId, id);
        validateParent(userId, category.type(), id, command.parentCategoryId());
        CategoryId parentId = command.parentCategoryId() == null ? null : CategoryId.of(command.parentCategoryId());
        category.rename(command.name(), parentId);
        return toView(categoryRepository.save(category));
    }

    public void delete(UUID userId, CategoryId id) {
        findOwned(userId, id);
        if (categoryRepository.existsByParentCategoryIdAndUserId(id, userId)) {
            throw new CategoryHasChildrenException(
                    "Cannot delete a category that has subcategories: " + id.value());
        }
        categoryRepository.deleteByIdAndUserId(id, userId);
    }

    private Category findOwned(UUID userId, CategoryId id) {
        return categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + id.value()));
    }

    /**
     * Valida que un padre propuesto exista, sea del mismo usuario y tipo, y no forme un ciclo.
     * {@code categoryIdOrNull} es null durante la creación (todavía no hay id propio que pueda
     * aparecer en la cadena de ancestros).
     */
    private void validateParent(UUID userId, CategoryType type, CategoryId categoryIdOrNull, UUID parentCategoryIdRaw) {
        if (parentCategoryIdRaw == null) {
            return;
        }
        CategoryId parentId = CategoryId.of(parentCategoryIdRaw);
        if (parentId.equals(categoryIdOrNull)) {
            throw new InvalidCategoryHierarchyException("A category cannot be its own parent");
        }
        Category parent = categoryRepository.findByIdAndUserId(parentId, userId)
                .orElseThrow(() -> new InvalidCategoryHierarchyException(
                        "Parent category not found or not owned by the user"));
        if (parent.type() != type) {
            throw new InvalidCategoryHierarchyException("Parent category must have the same type");
        }
        if (categoryIdOrNull != null) {
            CategoryId cursor = parent.parentCategoryId().orElse(null);
            while (cursor != null) {
                if (cursor.equals(categoryIdOrNull)) {
                    throw new InvalidCategoryHierarchyException("Cyclic category hierarchy detected");
                }
                cursor = categoryRepository.findByIdAndUserId(cursor, userId)
                        .flatMap(Category::parentCategoryId)
                        .orElse(null);
            }
        }
    }

    private static CategoryView toView(Category category) {
        return new CategoryView(category.id().value(), category.name(), category.type(),
                category.parentCategoryId().map(CategoryId::value).orElse(null));
    }
}
