package com.walletapp.backend.auth.application;

import com.walletapp.backend.auth.domain.RevokedToken;
import com.walletapp.backend.auth.domain.RevokedTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class LogoutUseCase {

    private final RevokedTokenRepository revokedTokenRepository;

    public LogoutUseCase(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public void execute(String jti, Instant expiresAt) {
        revokedTokenRepository.save(new RevokedToken(jti, expiresAt));
    }
}
