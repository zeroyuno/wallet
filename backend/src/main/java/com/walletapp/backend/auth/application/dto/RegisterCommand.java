package com.walletapp.backend.auth.application.dto;

public record RegisterCommand(String email, String rawPassword, String displayName) {
}
