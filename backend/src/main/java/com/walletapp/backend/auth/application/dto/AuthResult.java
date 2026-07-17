package com.walletapp.backend.auth.application.dto;

import java.time.Instant;

public record AuthResult(String accessToken, String tokenType, Instant expiresAt) {
}
