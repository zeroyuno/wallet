package com.walletapp.backend.auth.infrastructure.web.dto;

import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName) {
}
