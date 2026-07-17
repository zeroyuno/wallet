package com.walletapp.backend.shared.security;

import java.time.Instant;
import java.util.UUID;

/** Identidad resuelta por {@link JwtTokenValidator} y expuesta a todos los contextos vía Spring Security. */
public record AuthenticatedUser(UUID id, String email, String jti, Instant expiresAt) {
}
