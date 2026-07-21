package com.walletapp.backend.transaction.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataLabelRepository extends JpaRepository<LabelEntity, UUID> {

    Optional<LabelEntity> findByUserIdAndName(UUID userId, String name);
}
