package com.walletapp.backend.auth.infrastructure.security;

import com.walletapp.backend.auth.application.TokenIssuer;
import com.walletapp.backend.auth.domain.Email;
import com.walletapp.backend.auth.domain.UserId;
import com.walletapp.backend.shared.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
class JjwtTokenIssuer implements TokenIssuer {

    private final SecretKey key;
    private final JwtProperties properties;

    JjwtTokenIssuer(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public IssuedToken issue(UserId userId, Email email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.expiration());
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject(userId.value().toString())
                .claim("email", email.value())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();

        return new IssuedToken(token, jti, expiresAt);
    }
}
