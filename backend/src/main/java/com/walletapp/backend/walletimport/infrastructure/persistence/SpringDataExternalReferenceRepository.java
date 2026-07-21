package com.walletapp.backend.walletimport.infrastructure.persistence;

import com.walletapp.backend.walletimport.domain.ExternalEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataExternalReferenceRepository extends JpaRepository<ExternalReferenceEntity, UUID> {

    Optional<ExternalReferenceEntity> findByUserIdAndEntityTypeAndExternalId(UUID userId,
                                                                              ExternalEntityType entityType,
                                                                              String externalId);
}
