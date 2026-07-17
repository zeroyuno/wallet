package com.walletapp.backend.auth.application;

import com.walletapp.backend.auth.domain.Email;
import com.walletapp.backend.auth.domain.UserId;

import java.time.Instant;

/** Puerto de salida: emitir un JWT es un detalle de infraestructura (ver research.md). */
public interface TokenIssuer {

    IssuedToken issue(UserId userId, Email email);

    record IssuedToken(String accessToken, String jti, Instant expiresAt) {
    }
}
