package com.walletapp.backend.account.application.dto;

import com.walletapp.backend.account.domain.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountView(UUID id, String name, AccountType type, String currency, BigDecimal initialBalance) {
}
