package com.walletapp.backend.account.application.dto;

import com.walletapp.backend.account.domain.CategoryType;

import java.util.UUID;

public record CategoryCommand(String name, CategoryType type, UUID parentCategoryId) {
}
