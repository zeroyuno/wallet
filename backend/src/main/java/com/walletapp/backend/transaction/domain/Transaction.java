package com.walletapp.backend.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Transaction {

    private final TransactionId id;
    private final UUID userId;
    private final TransactionType type;
    private BigDecimal amount;
    private LocalDate date;
    private String description;
    private final UUID accountId;
    private UUID categoryId;
    private final Instant createdAt;

    private Transaction(TransactionId id, UUID userId, TransactionType type, BigDecimal amount, LocalDate date,
                         String description, UUID accountId, UUID categoryId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.createdAt = createdAt;
    }

    // id es opcional: si el cliente lo provee (creación offline, FR-011) se usa tal cual; si no,
    // se genera uno nuevo (UUID v7, ver TransactionId).
    public static Transaction create(Optional<TransactionId> id, UUID userId, TransactionType type,
                                      BigDecimal amount, LocalDate date, String description, UUID accountId,
                                      UUID categoryId) {
        requirePositiveAmount(amount);
        requireNonNull(date, "date");
        return new Transaction(id.orElseGet(TransactionId::newId), userId, type, amount, date, description,
                accountId, categoryId, Instant.now());
    }

    public static Transaction reconstitute(TransactionId id, UUID userId, TransactionType type, BigDecimal amount,
                                            LocalDate date, String description, UUID accountId, UUID categoryId,
                                            Instant createdAt) {
        return new Transaction(id, userId, type, amount, date, description, accountId, categoryId, createdAt);
    }

    // type y accountId son inmutables tras la creación (FR-005, ver research.md).
    public void update(BigDecimal amount, LocalDate date, String description, UUID categoryId) {
        requirePositiveAmount(amount);
        requireNonNull(date, "date");
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.categoryId = categoryId;
    }

    private static void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }
    }

    private static void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Transaction " + field + " must not be null");
        }
    }

    public TransactionId id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public TransactionType type() {
        return type;
    }

    public BigDecimal amount() {
        return amount;
    }

    public LocalDate date() {
        return date;
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public UUID accountId() {
        return accountId;
    }

    public Optional<UUID> categoryId() {
        return Optional.ofNullable(categoryId);
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
