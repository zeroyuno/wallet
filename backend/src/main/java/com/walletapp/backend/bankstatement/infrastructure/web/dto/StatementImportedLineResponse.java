package com.walletapp.backend.bankstatement.infrastructure.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementImportedLineResponse(LocalDate date, BigDecimal amount, String type, String description,
                                             String columnHeader) {
}
