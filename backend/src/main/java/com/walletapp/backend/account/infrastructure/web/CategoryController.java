package com.walletapp.backend.account.infrastructure.web;

import com.walletapp.backend.account.application.CategoryService;
import com.walletapp.backend.account.application.dto.CategoryCommand;
import com.walletapp.backend.account.application.dto.CategoryView;
import com.walletapp.backend.account.domain.CategoryId;
import com.walletapp.backend.account.infrastructure.web.dto.CategoryRequest;
import com.walletapp.backend.account.infrastructure.web.dto.CategoryResponse;
import com.walletapp.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        log.info("GET /api/categories userId={}", principal.id());
        return categoryService.list(principal.id()).stream().map(CategoryController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @Valid @RequestBody CategoryRequest request) {
        log.info("POST /api/categories userId={} name={}", principal.id(), request.name());
        CategoryView view = categoryService.create(principal.id(), toCommand(request));
        return toResponse(view);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        log.info("PUT /api/categories/{} userId={}", id, principal.id());
        CategoryView view = categoryService.update(principal.id(), CategoryId.of(id), toCommand(request));
        return toResponse(view);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        log.info("DELETE /api/categories/{} userId={}", id, principal.id());
        categoryService.delete(principal.id(), CategoryId.of(id));
    }

    private static CategoryCommand toCommand(CategoryRequest request) {
        return new CategoryCommand(request.name(), request.type(), request.parentCategoryId());
    }

    private static CategoryResponse toResponse(CategoryView view) {
        return new CategoryResponse(view.id(), view.name(), view.type(), view.parentCategoryId());
    }
}
