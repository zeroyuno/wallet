package com.walletapp.backend.bankstatement.domain;

import java.util.Objects;
import java.util.UUID;

public record StatementImportId(UUID value) {

    public StatementImportId {
        Objects.requireNonNull(value, "StatementImportId value must not be null");
    }

    public static StatementImportId newId() {
        return new StatementImportId(UUID.randomUUID());
    }

    public static StatementImportId of(UUID value) {
        return new StatementImportId(value);
    }
}
