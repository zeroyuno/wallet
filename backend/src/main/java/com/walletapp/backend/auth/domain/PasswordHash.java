package com.walletapp.backend.auth.domain;

public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PasswordHash must not be blank");
        }
    }
}
