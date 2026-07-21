package com.walletapp.backend.walletimport.domain;

import java.util.Optional;
import java.util.UUID;

public interface ImportRepository {

    Import save(Import imp);

    Optional<Import> findById(ImportId id);

    Optional<Import> findByIdAndUserId(ImportId id, UUID userId);

    // Para reanudar automáticamente al iniciar una nueva corrida (FR-008, research.md #5).
    Optional<Import> findMostRecentPausedByUserId(UUID userId);
}
