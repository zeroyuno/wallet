package com.walletapp.backend.walletimport.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto para la tabla de mapeo de idempotencia (FR-006, research.md #6): no representa un
 * concepto de dominio visible al usuario, solo la relación id-externo (Wallet) -> id propio.
 */
public interface ExternalReferenceRepository {

    Optional<UUID> findInternalId(UUID userId, ExternalEntityType entityType, String externalId);

    void save(UUID userId, ExternalEntityType entityType, String externalId, UUID internalId);
}
