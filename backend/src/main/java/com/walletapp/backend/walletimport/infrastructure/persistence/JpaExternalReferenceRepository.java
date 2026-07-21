package com.walletapp.backend.walletimport.infrastructure.persistence;

import com.walletapp.backend.walletimport.domain.ExternalEntityType;
import com.walletapp.backend.walletimport.domain.ExternalReferenceRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaExternalReferenceRepository implements ExternalReferenceRepository {

    private final SpringDataExternalReferenceRepository springDataRepository;

    JpaExternalReferenceRepository(SpringDataExternalReferenceRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<UUID> findInternalId(UUID userId, ExternalEntityType entityType, String externalId) {
        return springDataRepository.findByUserIdAndEntityTypeAndExternalId(userId, entityType, externalId)
                .map(ExternalReferenceEntity::getInternalId);
    }

    @Override
    public void save(UUID userId, ExternalEntityType entityType, String externalId, UUID internalId) {
        springDataRepository.save(new ExternalReferenceEntity(UUID.randomUUID(), userId, entityType, externalId,
                internalId));
    }
}
