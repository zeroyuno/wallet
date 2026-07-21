package com.walletapp.backend.walletimport.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WalletRecordDto(String id, String accountId, BigDecimal amount, LocalDate recordDate,
                               String recordType, String categoryId, String counterParty, String note) {
}
