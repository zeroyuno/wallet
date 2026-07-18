package com.walletapp.backend.account.domain;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "AccountId value must not be null");
    }

    public static AccountId newId() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }
}
