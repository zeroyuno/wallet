package com.walletapp.backend.walletimport.domain;

import java.util.Objects;
import java.util.UUID;

public record ImportId(UUID value) {

    public ImportId {
        Objects.requireNonNull(value, "ImportId value must not be null");
    }

    public static ImportId newId() {
        return new ImportId(UUID.randomUUID());
    }

    public static ImportId of(UUID value) {
        return new ImportId(value);
    }
}
