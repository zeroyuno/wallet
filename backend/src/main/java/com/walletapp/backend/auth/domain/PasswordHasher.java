package com.walletapp.backend.auth.domain;

/** Puerto de salida: detalle de hashing (BCrypt, Argon2, ...) resuelto en infrastructure. */
public interface PasswordHasher {

    PasswordHash hash(RawPassword rawPassword);

    /**
     * Compara un candidato contra un hash existente. Recibe un String plano (no {@link RawPassword})
     * a propósito: la política de longitud mínima aplica al crear/cambiar una contraseña, no al
     * verificar una ya existente — una contraseña corta ingresada en login debe fallar como
     * credencial inválida genérica (FR-005), no como error de validación distinto.
     */
    boolean matches(String rawPassword, PasswordHash hash);
}
