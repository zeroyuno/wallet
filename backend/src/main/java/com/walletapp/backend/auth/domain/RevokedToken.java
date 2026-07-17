package com.walletapp.backend.auth.domain;

import java.time.Instant;
import java.util.Objects;

public record RevokedToken(String jti, Instant expiresAt) {

    public RevokedToken {
        Objects.requireNonNull(jti, "jti must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
