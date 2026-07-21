package com.walletapp.backend.walletimport.infrastructure.persistence;

import com.walletapp.backend.walletimport.domain.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataImportRepository extends JpaRepository<ImportEntity, UUID> {

    Optional<ImportEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<ImportEntity> findFirstByUserIdAndStatusOrderByLastActivityAtDesc(UUID userId, ImportStatus status);
}
