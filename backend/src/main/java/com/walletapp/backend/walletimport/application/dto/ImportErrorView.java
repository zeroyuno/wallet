package com.walletapp.backend.walletimport.application.dto;

public record ImportErrorView(String entityType, String externalId, String reason) {
}
