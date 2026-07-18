package com.walletapp.backend.transaction.infrastructure.persistence;

import com.walletapp.backend.transaction.domain.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    @Column
    private String description;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransactionEntity() {
        // JPA
    }

    public TransactionEntity(UUID id, UUID userId, TransactionType type, BigDecimal amount, LocalDate date,
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

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
