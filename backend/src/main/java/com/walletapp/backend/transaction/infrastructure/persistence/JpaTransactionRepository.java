package com.walletapp.backend.transaction.infrastructure.persistence;

import com.walletapp.backend.transaction.domain.DeletedTransactionTombstone;
import com.walletapp.backend.transaction.domain.Transaction;
import com.walletapp.backend.transaction.domain.TransactionId;
import com.walletapp.backend.transaction.domain.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
class JpaTransactionRepository implements TransactionRepository {

    private final SpringDataTransactionRepository springDataRepository;
    private final SpringDataLabelRepository labelRepository;
    private final SpringDataDeletedTransactionRepository deletedTransactionRepository;

    JpaTransactionRepository(SpringDataTransactionRepository springDataRepository,
                              SpringDataLabelRepository labelRepository,
                              SpringDataDeletedTransactionRepository deletedTransactionRepository) {
        this.springDataRepository = springDataRepository;
        this.labelRepository = labelRepository;
        this.deletedTransactionRepository = deletedTransactionRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        TransactionEntity saved = springDataRepository.save(toEntity(transaction));
        return toDomain(saved);
    }

    @Override
    public Optional<Transaction> findByIdAndUserId(TransactionId id, UUID userId) {
        return springDataRepository.findByIdAndUserId(id.value(), userId).map(JpaTransactionRepository::toDomain);
    }

    @Override
    public boolean existsByIdAndUserId(TransactionId id, UUID userId) {
        return springDataRepository.existsByIdAndUserId(id.value(), userId);
    }

    @Override
    public void deleteByIdAndUserId(TransactionId id, UUID userId) {
        springDataRepository.deleteByIdAndUserId(id.value(), userId);
        deletedTransactionRepository.save(
                new DeletedTransactionEntity(UUID.randomUUID(), id.value(), userId, Instant.now()));
    }

    @Override
    public List<Transaction> findAllByUserId(UUID userId, UUID accountId, UUID categoryId, LocalDate dateFrom,
                                              LocalDate dateTo) {
        return springDataRepository.findAllByUserId(userId).stream()
                .filter(entity -> accountId == null || accountId.equals(entity.getAccountId()))
                .filter(entity -> categoryId == null || categoryId.equals(entity.getCategoryId()))
                .filter(entity -> dateFrom == null || !entity.getDate().isBefore(dateFrom))
                .filter(entity -> dateTo == null || !entity.getDate().isAfter(dateTo))
                .map(JpaTransactionRepository::toDomain)
                .toList();
    }

    @Override
    public BigDecimal sumNetAmountForAccount(UUID userId, UUID accountId) {
        return springDataRepository.sumNetAmountForAccount(userId, accountId);
    }

    @Override
    public List<Transaction> findChangedSince(UUID userId, Instant since, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return springDataRepository.findChangedSince(userId, since, pageable).stream()
                .map(JpaTransactionRepository::toDomain)
                .toList();
    }

    @Override
    public List<DeletedTransactionTombstone> findDeletedSince(UUID userId, Instant since, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return deletedTransactionRepository.findChangedSince(userId, since, pageable).stream()
                .map(e -> new DeletedTransactionTombstone(e.getTransactionId(), e.getUserId(), e.getDeletedAt()))
                .toList();
    }

    private TransactionEntity toEntity(Transaction transaction) {
        Set<LabelEntity> labelEntities = resolveLabels(transaction.userId(), transaction.labels());
        return new TransactionEntity(
                transaction.id().value(),
                transaction.userId(),
                transaction.type(),
                transaction.amount(),
                transaction.date(),
                transaction.description().orElse(null),
                transaction.accountId(),
                transaction.categoryId().orElse(null),
                transaction.createdAt(),
                transaction.updatedAt(),
                transaction.counterParty().orElse(null),
                transaction.paymentType().orElse(null),
                transaction.recordState().orElse(null),
                transaction.walletTransferId().orElse(null),
                labelEntities);
    }

    // Encuentra o crea la etiqueta por (userId, name) — las etiquetas se reutilizan entre
    // transacciones del mismo usuario en vez de duplicarse por cada importación (ver research.md).
    private Set<LabelEntity> resolveLabels(UUID userId, Set<String> names) {
        Set<LabelEntity> result = new LinkedHashSet<>();
        for (String name : names) {
            LabelEntity label = labelRepository.findByUserIdAndName(userId, name)
                    .orElseGet(() -> labelRepository.save(new LabelEntity(UUID.randomUUID(), userId, name)));
            result.add(label);
        }
        return result;
    }

    private static Transaction toDomain(TransactionEntity entity) {
        Set<String> labelNames = entity.getLabels().stream().map(LabelEntity::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Transaction.reconstitute(
                TransactionId.of(entity.getId()),
                entity.getUserId(),
                entity.getType(),
                entity.getAmount(),
                entity.getDate(),
                entity.getDescription(),
                entity.getAccountId(),
                entity.getCategoryId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCounterParty(),
                entity.getPaymentType(),
                entity.getRecordState(),
                entity.getWalletTransferId(),
                labelNames);
    }
}
