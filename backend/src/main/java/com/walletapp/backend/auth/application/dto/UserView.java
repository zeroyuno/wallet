package com.walletapp.backend.auth.application.dto;

import java.util.UUID;

public record UserView(UUID id, String email, String displayName) {
}
