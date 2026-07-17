package com.walletapp.backend.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "revoked_tokens")
public class RevokedTokenEntity {

    @Id
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected RevokedTokenEntity() {
        // JPA
    }

    public RevokedTokenEntity(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    public String getJti() {
        return jti;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
