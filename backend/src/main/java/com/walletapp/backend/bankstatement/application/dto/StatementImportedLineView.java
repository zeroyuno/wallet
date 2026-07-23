package com.walletapp.backend.bankstatement.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementImportedLineView(LocalDate date, BigDecimal amount, String type, String description,
                                         String columnHeader) {
}
