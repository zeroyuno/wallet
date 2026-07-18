package com.walletapp.backend.account.application.dto;

import com.walletapp.backend.account.domain.CategoryType;

import java.util.UUID;

public record CategoryView(UUID id, String name, CategoryType type, UUID parentCategoryId) {
}
