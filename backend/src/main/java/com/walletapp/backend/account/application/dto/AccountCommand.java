package com.walletapp.backend.account.application.dto;

import com.walletapp.backend.account.domain.AccountType;

import java.math.BigDecimal;

public record AccountCommand(String name, AccountType type, String currency, BigDecimal initialBalance) {
}
