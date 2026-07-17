package com.walletapp.backend.auth.infrastructure.persistence;

import com.walletapp.backend.auth.domain.RevokedToken;
import com.walletapp.backend.auth.domain.RevokedTokenRepository;
import org.springframework.stereotype.Repository;

@Repository
class JpaRevokedTokenRepository implements RevokedTokenRepository {

    private final SpringDataRevokedTokenRepository springDataRepository;

    JpaRevokedTokenRepository(SpringDataRevokedTokenRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void save(RevokedToken revokedToken) {
        springDataRepository.save(new RevokedTokenEntity(revokedToken.jti(), revokedToken.expiresAt()));
    }

    @Override
    public boolean existsByJti(String jti) {
        return springDataRepository.existsById(jti);
    }
}
