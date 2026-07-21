package com.walletapp.backend.walletimport.infrastructure.web.dto;

public record ImportErrorItemResponse(String entityType, String externalId, String reason) {
}
