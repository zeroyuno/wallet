package com.walletapp.backend.account.domain;

import java.util.Objects;
import java.util.UUID;

public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "CategoryId value must not be null");
    }

    public static CategoryId newId() {
        return new CategoryId(UUID.randomUUID());
    }

    public static CategoryId of(UUID value) {
        return new CategoryId(value);
    }
}
