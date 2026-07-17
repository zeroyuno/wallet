package com.walletapp.backend.auth.infrastructure.security;

import com.walletapp.backend.auth.domain.PasswordHash;
import com.walletapp.backend.auth.domain.PasswordHasher;
import com.walletapp.backend.auth.domain.RawPassword;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public PasswordHash hash(RawPassword rawPassword) {
        return new PasswordHash(encoder.encode(rawPassword.value()));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return encoder.matches(rawPassword, hash.value());
    }
}
