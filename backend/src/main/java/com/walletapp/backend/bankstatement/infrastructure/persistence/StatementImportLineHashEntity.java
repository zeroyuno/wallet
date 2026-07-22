package com.walletapp.backend.bankstatement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "statement_import_line_hashes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "hash"}))
public class StatementImportLineHashEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String hash;

    @Column(name = "internal_transaction_id", nullable = false)
    private UUID internalTransactionId;

    protected StatementImportLineHashEntity() {
        // JPA
    }

    public StatementImportLineHashEntity(UUID id, UUID userId, UUID accountId, String hash,
                                          UUID internalTransactionId) {
        this.id = id;
        this.userId = userId;
        this.accountId = accountId;
        this.hash = hash;
        this.internalTransactionId = internalTransactionId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getHash() {
        return hash;
    }

    public UUID getInternalTransactionId() {
        return internalTransactionId;
    }
}
