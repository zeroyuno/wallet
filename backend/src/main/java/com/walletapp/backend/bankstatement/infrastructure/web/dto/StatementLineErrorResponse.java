package com.walletapp.backend.bankstatement.infrastructure.web.dto;

public record StatementLineErrorResponse(String rawText, String reason) {
}
