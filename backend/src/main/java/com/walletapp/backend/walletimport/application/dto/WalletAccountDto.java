package com.walletapp.backend.walletimport.application.dto;

import java.math.BigDecimal;

public record WalletAccountDto(String id, String name, String accountType, String currencyCode,
                                BigDecimal initialBalance) {
}
