package com.walletapp.backend.bankstatement.infrastructure.persistence;

import com.walletapp.backend.bankstatement.domain.StatementImport;
import com.walletapp.backend.bankstatement.domain.StatementImportId;
import com.walletapp.backend.bankstatement.domain.StatementImportRepository;
import com.walletapp.backend.bankstatement.domain.StatementLineError;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaStatementImportRepository implements StatementImportRepository {

    private final SpringDataStatementImportRepository springDataRepository;

    JpaStatementImportRepository(SpringDataStatementImportRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public StatementImport save(StatementImport statementImport) {
        return toDomain(springDataRepository.save(toEntity(statementImport)));
    }

    @Override
    public Optional<StatementImport> findById(StatementImportId id) {
        return springDataRepository.findById(id.value()).map(JpaStatementImportRepository::toDomain);
    }

    @Override
    public Optional<StatementImport> findByIdAndUserId(StatementImportId id, UUID userId) {
        return springDataRepository.findByIdAndUserId(id.value(), userId).map(JpaStatementImportRepository::toDomain);
    }

    private static StatementImportEntity toEntity(StatementImport statementImport) {
        List<StatementLineErrorEmbeddable> errors = statementImport.errors().stream()
                .map(e -> new StatementLineErrorEmbeddable(e.rawText(), e.reason(), e.occurredAt()))
                .toList();
        return new StatementImportEntity(statementImport.id().value(), statementImport.userId(),
                statementImport.accountId(), statementImport.status(), statementImport.transactionsImported(),
                errors, statementImport.failureReason(), statementImport.startedAt(),
                statementImport.lastActivityAt());
    }

    private static StatementImport toDomain(StatementImportEntity entity) {
        List<StatementLineError> errors = entity.getErrors().stream()
                .map(e -> new StatementLineError(e.getRawText(), e.getReason(), e.getOccurredAt()))
                .toList();
        return StatementImport.reconstitute(StatementImportId.of(entity.getId()), entity.getUserId(),
                entity.getAccountId(), entity.getStatus(), entity.getTransactionsImported(), errors,
                entity.getFailureReason(), entity.getStartedAt(), entity.getLastActivityAt());
    }
}
