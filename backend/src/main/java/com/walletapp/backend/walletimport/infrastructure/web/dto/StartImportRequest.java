package com.walletapp.backend.walletimport.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record StartImportRequest(@NotBlank String walletApiToken) {
}
