package com.walletapp.backend.bankstatement.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto para la tabla de deduplicación (FR-006, research.md #3): no representa un concepto de
 * dominio visible para el usuario, solo el hash de (cuenta+fecha+monto+descripción) -> id de la
 * transacción ya creada.
 */
public interface StatementLineHashRepository {

    Optional<UUID> findInternalTransactionId(UUID userId, String hash);

    void save(UUID userId, UUID accountId, String hash, UUID internalTransactionId);
}
