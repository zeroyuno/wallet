package com.walletapp.backend.account.infrastructure.persistence;

import com.walletapp.backend.account.domain.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private String currency;

    @Column(name = "initial_balance", nullable = false)
    private BigDecimal initialBalance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountEntity() {
        // JPA
    }

    public AccountEntity(UUID id, UUID userId, String name, AccountType type, String currency,
                          BigDecimal initialBalance, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.initialBalance = initialBalance;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
