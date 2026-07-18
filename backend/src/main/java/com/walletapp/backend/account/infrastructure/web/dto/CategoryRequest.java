package com.walletapp.backend.account.infrastructure.web.dto;

import com.walletapp.backend.account.domain.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CategoryRequest(@NotBlank String name, @NotNull CategoryType type, UUID parentCategoryId) {
}
