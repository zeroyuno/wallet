package com.walletapp.backend.transaction.infrastructure.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceResponse(UUID accountId, BigDecimal balance) {
}
