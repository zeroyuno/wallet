package com.walletapp.backend.bankstatement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataStatementImportRepository extends JpaRepository<StatementImportEntity, UUID> {

    Optional<StatementImportEntity> findByIdAndUserId(UUID id, UUID userId);
}
