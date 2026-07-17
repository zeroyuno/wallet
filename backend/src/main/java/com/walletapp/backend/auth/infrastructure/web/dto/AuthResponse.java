package com.walletapp.backend.auth.infrastructure.web.dto;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt) {
}
