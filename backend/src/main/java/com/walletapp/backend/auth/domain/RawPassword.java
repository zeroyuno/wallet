package com.walletapp.backend.auth.domain;

import com.walletapp.backend.auth.domain.exception.InvalidPasswordException;

/** Contraseña sin hashear, validada contra la política mínima antes de pasar por {@link PasswordHasher}. */
public record RawPassword(String value) {

    private static final int MIN_LENGTH = 8;

    public RawPassword {
        if (value == null || value.length() < MIN_LENGTH) {
            throw new InvalidPasswordException("Password must be at least " + MIN_LENGTH + " characters long");
        }
    }
}
