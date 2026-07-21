package com.walletapp.backend.walletimport.application.dto;

import java.util.List;

public record WalletRecordPage(List<WalletRecordDto> records, boolean hasMore) {
}
