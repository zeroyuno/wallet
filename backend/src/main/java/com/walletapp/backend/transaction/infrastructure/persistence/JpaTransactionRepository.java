package com.walletapp.backend.transaction.infrastructure.persistence;

import com.walletapp.backend.transaction.domain.Transaction;
import com.walletapp.backend.transaction.domain.TransactionId;
import com.walletapp.backend.transaction.domain.TransactionRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaTransactionRepository implements TransactionRepository {

    private final SpringDataTransactionRepository springDataRepository;

    JpaTransactionRepository(SpringDataTransactionRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
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

    private static TransactionEntity toEntity(Transaction transaction) {
        return new TransactionEntity(
                transaction.id().value(),
                transaction.userId(),
                transaction.type(),
                transaction.amount(),
                transaction.date(),
                transaction.description().orElse(null),
                transaction.accountId(),
                transaction.categoryId().orElse(null),
                transaction.createdAt());
    }

    private static Transaction toDomain(TransactionEntity entity) {
        return Transaction.reconstitute(
                TransactionId.of(entity.getId()),
                entity.getUserId(),
                entity.getType(),
                entity.getAmount(),
                entity.getDate(),
                entity.getDescription(),
                entity.getAccountId(),
                entity.getCategoryId(),
                entity.getCreatedAt());
    }
}
