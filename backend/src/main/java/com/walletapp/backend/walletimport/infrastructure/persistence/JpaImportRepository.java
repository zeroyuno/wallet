package com.walletapp.backend.walletimport.infrastructure.persistence;

import com.walletapp.backend.walletimport.domain.Import;
import com.walletapp.backend.walletimport.domain.ImportError;
import com.walletapp.backend.walletimport.domain.ImportId;
import com.walletapp.backend.walletimport.domain.ImportRepository;
import com.walletapp.backend.walletimport.domain.ImportStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaImportRepository implements ImportRepository {

    private final SpringDataImportRepository springDataRepository;

    JpaImportRepository(SpringDataImportRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Import save(Import imp) {
        return toDomain(springDataRepository.save(toEntity(imp)));
    }

    @Override
    public Optional<Import> findById(ImportId id) {
        return springDataRepository.findById(id.value()).map(JpaImportRepository::toDomain);
    }

    @Override
    public Optional<Import> findByIdAndUserId(ImportId id, UUID userId) {
        return springDataRepository.findByIdAndUserId(id.value(), userId).map(JpaImportRepository::toDomain);
    }

    @Override
    public Optional<Import> findMostRecentPausedByUserId(UUID userId) {
        return springDataRepository
                .findFirstByUserIdAndStatusOrderByLastActivityAtDesc(userId, ImportStatus.PAUSED_RATE_LIMIT)
                .map(JpaImportRepository::toDomain);
    }

    private static ImportEntity toEntity(Import imp) {
        List<ImportErrorEmbeddable> errors = imp.errors().stream()
                .map(e -> new ImportErrorEmbeddable(e.entityType(), e.externalId(), e.reason(), e.occurredAt()))
                .toList();
        return new ImportEntity(imp.id().value(), imp.userId(), imp.status(), imp.cursorPhase(),
                imp.cursorRecordDate(), imp.accountsImported(), imp.categoriesImported(),
                imp.transactionsImported(), errors, imp.startedAt(), imp.lastActivityAt());
    }

    private static Import toDomain(ImportEntity entity) {
        List<ImportError> errors = entity.getErrors().stream()
                .map(e -> new ImportError(e.getEntityType(), e.getExternalId(), e.getReason(), e.getOccurredAt()))
                .toList();
        return Import.reconstitute(ImportId.of(entity.getId()), entity.getUserId(), entity.getStatus(),
                entity.getCursorPhase(), entity.getCursorRecordDate(), entity.getAccountsImported(),
                entity.getCategoriesImported(), entity.getTransactionsImported(), errors, entity.getStartedAt(),
                entity.getLastActivityAt());
    }
}
