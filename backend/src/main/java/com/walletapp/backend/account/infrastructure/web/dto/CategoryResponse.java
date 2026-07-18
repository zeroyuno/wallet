package com.walletapp.backend.account.infrastructure.web.dto;

import com.walletapp.backend.account.domain.CategoryType;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, CategoryType type, UUID parentCategoryId) {
}
