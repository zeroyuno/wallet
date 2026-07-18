package com.walletapp.backend.transaction.domain;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID value) {

    public TransactionId {
        Objects.requireNonNull(value, "TransactionId value must not be null");
    }

    // UUID v7 (no v4): no adivinable como un id secuencial, y ordenable por tiempo de creación —
    // esto último importa porque un id puede originarse en el cliente (ver of()) para soportar
    // creación offline, sin depender de que el servidor se lo asigne primero (FR-011, research.md).
    public static TransactionId newId() {
        return new TransactionId(UuidCreator.getTimeOrderedEpoch());
    }

    public static TransactionId of(UUID value) {
        return new TransactionId(value);
    }
}
