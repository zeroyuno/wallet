package com.walletapp.backend.auth.application.dto;

public record LoginCommand(String email, String rawPassword) {
}
