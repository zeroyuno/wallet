package com.walletapp.backend.shared.security;

import com.walletapp.backend.auth.domain.RevokedTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

/**
 * Valida firma y expiración del JWT, y consulta revocación (FR-007/FR-008) vía el puerto de dominio
 * {@link RevokedTokenRepository} del contexto auth — excepción documentada en plan.md/Complexity
 * Tracking: infraestructura transversal que se apoya en el dominio de auth, no al revés.
 */
@Component
public class JwtTokenValidator {

    private final SecretKey key;
    private final RevokedTokenRepository revokedTokenRepository;

    public JwtTokenValidator(JwtProperties properties, RevokedTokenRepository revokedTokenRepository) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public Optional<AuthenticatedUser> validate(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String jti = claims.getId();
            if (jti != null && revokedTokenRepository.existsByJti(jti)) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            Instant expiresAt = claims.getExpiration().toInstant();
            return Optional.of(new AuthenticatedUser(userId, email, jti, expiresAt));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
