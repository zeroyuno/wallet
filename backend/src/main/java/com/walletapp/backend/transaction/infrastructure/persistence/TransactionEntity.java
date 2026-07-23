package com.walletapp.backend.transaction.infrastructure.persistence;

import com.walletapp.backend.transaction.domain.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
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

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "counter_party")
    private String counterParty;

    @Column(name = "payment_type")
    private String paymentType;

    @Column(name = "record_state")
    private String recordState;

    @Column(name = "wallet_transfer_id")
    private String walletTransferId;

    // EAGER a propósito, mismo motivo que ImportEntity.errors: se lee fuera de sesiones abiertas
    // largas (ej. durante la importación en @Async) y es una colección pequeña por transacción.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "transaction_labels", joinColumns = @JoinColumn(name = "transaction_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id"))
    private Set<LabelEntity> labels = new HashSet<>();

    protected TransactionEntity() {
        // JPA
    }

    public TransactionEntity(UUID id, UUID userId, TransactionType type, BigDecimal amount, LocalDate date,
                              String description, UUID accountId, UUID categoryId, Instant createdAt,
                              Instant updatedAt, String counterParty, String paymentType, String recordState,
                              String walletTransferId, Set<LabelEntity> labels) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.counterParty = counterParty;
        this.paymentType = paymentType;
        this.recordState = recordState;
        this.walletTransferId = walletTransferId;
        this.labels = new HashSet<>(labels);
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCounterParty() {
        return counterParty;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public String getRecordState() {
        return recordState;
    }

    public String getWalletTransferId() {
        return walletTransferId;
    }

    public Set<LabelEntity> getLabels() {
        return labels;
    }
}
