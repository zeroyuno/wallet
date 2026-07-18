package com.walletapp.backend.account.application;

import com.walletapp.backend.account.application.dto.CategoryCommand;
import com.walletapp.backend.account.application.dto.CategoryView;
import com.walletapp.backend.account.domain.Category;
import com.walletapp.backend.account.domain.CategoryId;
import com.walletapp.backend.account.domain.CategoryRepository;
import com.walletapp.backend.account.domain.CategoryType;
import com.walletapp.backend.account.domain.exception.CategoryNotFoundException;
import com.walletapp.backend.account.domain.exception.DuplicateCategoryException;
import com.walletapp.backend.account.domain.exception.InvalidCategoryHierarchyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    CategoryRepository categoryRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createsCategoryWithoutParent() {
        when(categoryRepository.existsByUserIdAndTypeAndName(any(), any(), any())).thenReturn(false);
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryService service = new CategoryService(categoryRepository);
        CategoryView result = service.create(userId, new CategoryCommand("Comida", CategoryType.EXPENSE, null));

        assertThat(result.name()).isEqualTo("Comida");
        assertThat(result.parentCategoryId()).isNull();
    }

    @Test
    void rejectsDuplicateNameAndType() {
        when(categoryRepository.existsByUserIdAndTypeAndName(userId, CategoryType.EXPENSE, "Comida"))
                .thenReturn(true);

        CategoryService service = new CategoryService(categoryRepository);

        assertThatThrownBy(() -> service.create(userId, new CategoryCommand("Comida", CategoryType.EXPENSE, null)))
                .isInstanceOf(DuplicateCategoryException.class);
    }

    @Test
    void listsOnlyCategoriesOfTheGivenUser() {
        Category category = Category.create(userId, "Salario", CategoryType.INCOME, null);
        when(categoryRepository.findAllByUserId(userId)).thenReturn(List.of(category));

        CategoryService service = new CategoryService(categoryRepository);
        List<CategoryView> result = service.list(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Salario");
    }

    @Test
    void updateRejectsCategoryNotOwnedByUser() {
        when(categoryRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        CategoryService service = new CategoryService(categoryRepository);

        assertThatThrownBy(() -> service.update(userId, CategoryId.newId(),
                new CategoryCommand("Nuevo", CategoryType.EXPENSE, null)))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void deleteRemovesOwnedCategory() {
        Category category = Category.create(userId, "Comida", CategoryType.EXPENSE, null);
        when(categoryRepository.findByIdAndUserId(category.id(), userId)).thenReturn(Optional.of(category));

        CategoryService service = new CategoryService(categoryRepository);
        service.delete(userId, category.id());

        verify(categoryRepository).deleteByIdAndUserId(category.id(), userId);
    }

    @Test
    void rejectsParentFromAnotherUserOrType() {
        Category parent = Category.create(UUID.randomUUID(), "Comida", CategoryType.EXPENSE, null);
        when(categoryRepository.existsByUserIdAndTypeAndName(any(), any(), any())).thenReturn(false);
        when(categoryRepository.findByIdAndUserId(parent.id(), userId)).thenReturn(Optional.empty());

        CategoryService service = new CategoryService(categoryRepository);

        assertThatThrownBy(() -> service.create(userId,
                new CategoryCommand("Supermercado", CategoryType.EXPENSE, parent.id().value())))
                .isInstanceOf(InvalidCategoryHierarchyException.class);
    }

    @Test
    void rejectsCyclicHierarchyOnUpdate() {
        Category grandparent = Category.create(userId, "Comida", CategoryType.EXPENSE, null);
        Category parent = Category.create(userId, "Supermercado", CategoryType.EXPENSE, grandparent.id());

        when(categoryRepository.findByIdAndUserId(grandparent.id(), userId)).thenReturn(Optional.of(grandparent));
        when(categoryRepository.findByIdAndUserId(parent.id(), userId)).thenReturn(Optional.of(parent));

        CategoryService service = new CategoryService(categoryRepository);

        // Intentar que "Comida" (el abuelo) pase a ser hija de "Supermercado" (su propio nieto) — ciclo.
        assertThatThrownBy(() -> service.update(userId, grandparent.id(),
                new CategoryCommand("Comida", CategoryType.EXPENSE, parent.id().value())))
                .isInstanceOf(InvalidCategoryHierarchyException.class);
    }
}
