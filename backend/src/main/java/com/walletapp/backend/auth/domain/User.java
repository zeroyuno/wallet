package com.walletapp.backend.auth.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Aggregate root del contexto auth. El bloqueo tras intentos fallidos (FR-010) se modela como
 * "N fallos consecutivos sin login exitoso de por medio" en vez de una ventana deslizante de tiempo
 * exacta — simplificación deliberada (constitución, principio V) que sigue cumpliendo el requisito:
 * el bloqueo resultante SÍ dura una ventana de tiempo corta (15 minutos).
 */
public final class User {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserId id;
    private final Email email;
    private PasswordHash passwordHash;
    private final String displayName;
    private final Instant createdAt;
    private int failedLoginAttempts;
    private Instant lockedUntil;

    private User(UserId id, Email email, PasswordHash passwordHash, String displayName, Instant createdAt,
                  int failedLoginAttempts, Instant lockedUntil) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.createdAt = createdAt;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
    }

    public static User register(Email email, PasswordHash passwordHash, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name must not be blank");
        }
        return new User(UserId.newId(), email, passwordHash, displayName.trim(), Instant.now(), 0, null);
    }

    /** Reconstruye un User ya existente desde persistencia — no reaplica invariantes de creación. */
    public static User reconstitute(UserId id, Email email, PasswordHash passwordHash, String displayName,
                                     Instant createdAt, int failedLoginAttempts, Instant lockedUntil) {
        return new User(id, email, passwordHash, displayName, createdAt, failedLoginAttempts, lockedUntil);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean verifyPassword(String rawPassword, PasswordHasher hasher) {
        return hasher.matches(rawPassword, passwordHash);
    }

    public void registerFailedLogin(Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    public void registerSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }
}
