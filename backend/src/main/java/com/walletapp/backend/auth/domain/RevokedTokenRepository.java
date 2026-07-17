package com.walletapp.backend.auth.domain;

public interface RevokedTokenRepository {

    void save(RevokedToken revokedToken);

    boolean existsByJti(String jti);
}
