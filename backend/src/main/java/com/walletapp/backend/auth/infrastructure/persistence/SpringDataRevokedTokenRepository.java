package com.walletapp.backend.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRevokedTokenRepository extends JpaRepository<RevokedTokenEntity, String> {
}
