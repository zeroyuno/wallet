package com.walletapp.backend.account.infrastructure.web.dto;

import com.walletapp.backend.account.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank String name,
        @NotNull AccountType type,
        @NotBlank String currency,
        @NotNull BigDecimal initialBalance) {
}
