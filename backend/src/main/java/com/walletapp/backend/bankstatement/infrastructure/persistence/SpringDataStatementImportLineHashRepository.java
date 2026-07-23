package com.walletapp.backend.bankstatement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataStatementImportLineHashRepository extends JpaRepository<StatementImportLineHashEntity, UUID> {

    Optional<StatementImportLineHashEntity> findByUserIdAndHash(UUID userId, String hash);
}
