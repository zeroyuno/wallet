package com.walletapp.backend.bankstatement.infrastructure.persistence;

import com.walletapp.backend.bankstatement.domain.StatementLineHashRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaStatementLineHashRepository implements StatementLineHashRepository {

    private final SpringDataStatementImportLineHashRepository springDataRepository;

    JpaStatementLineHashRepository(SpringDataStatementImportLineHashRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<UUID> findInternalTransactionId(UUID userId, String hash) {
        return springDataRepository.findByUserIdAndHash(userId, hash)
                .map(StatementImportLineHashEntity::getInternalTransactionId);
    }

    @Override
    public void save(UUID userId, UUID accountId, String hash, UUID internalTransactionId) {
        springDataRepository.save(new StatementImportLineHashEntity(UUID.randomUUID(), userId, accountId, hash,
                internalTransactionId));
    }
}
