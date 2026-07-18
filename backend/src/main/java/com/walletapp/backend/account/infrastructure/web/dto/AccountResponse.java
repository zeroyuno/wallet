package com.walletapp.backend.account.infrastructure.web.dto;

import com.walletapp.backend.account.domain.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(UUID id, String name, AccountType type, String currency, BigDecimal initialBalance) {
}
