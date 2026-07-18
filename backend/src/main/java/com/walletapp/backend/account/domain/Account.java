package com.walletapp.backend.account.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class Account {

    private final AccountId id;
    private final UUID userId;
    private String name;
    private AccountType type;
    private CurrencyCode currency;
    private final BigDecimal initialBalance;
    private final Instant createdAt;

    private Account(AccountId id, UUID userId, String name, AccountType type, CurrencyCode currency,
                     BigDecimal initialBalance, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.initialBalance = initialBalance;
        this.createdAt = createdAt;
    }

    public static Account create(UUID userId, String name, AccountType type, CurrencyCode currency,
                                  BigDecimal initialBalance) {
        requireNonBlankName(name);
        return new Account(AccountId.newId(), userId, name.trim(), type, currency, initialBalance, Instant.now());
    }

    public static Account reconstitute(AccountId id, UUID userId, String name, AccountType type,
                                        CurrencyCode currency, BigDecimal initialBalance, Instant createdAt) {
        return new Account(id, userId, name, type, currency, initialBalance, createdAt);
    }

    public void rename(String name, AccountType type, CurrencyCode currency) {
        requireNonBlankName(name);
        this.name = name.trim();
        this.type = type;
        this.currency = currency;
    }

    private static void requireNonBlankName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Account name must not be blank");
        }
    }

    public AccountId id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public AccountType type() {
        return type;
    }

    public CurrencyCode currency() {
        return currency;
    }

    public BigDecimal initialBalance() {
        return initialBalance;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
