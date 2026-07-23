package com.walletapp.backend.transaction.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deleted_transactions")
public class DeletedTransactionEntity {

    @Id
    @Column(name = "row_id")
    private UUID rowId;

    @Column(name = "id", nullable = false)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    protected DeletedTransactionEntity() {
        // JPA
    }

    public DeletedTransactionEntity(UUID rowId, UUID transactionId, UUID userId, Instant deletedAt) {
        this.rowId = rowId;
        this.transactionId = transactionId;
        this.userId = userId;
        this.deletedAt = deletedAt;
    }

    public UUID getRowId() {
        return rowId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
