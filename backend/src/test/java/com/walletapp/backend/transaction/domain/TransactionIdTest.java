package com.walletapp.backend.transaction.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionIdTest {

    // UUID v7: los primeros 48 bits son un timestamp, así que dos ids generados en momentos
    // distintos son ordenables por valor — no adivinables (no secuenciales), pero sí ordenables
    // temporalmente (FR-011, ver research.md).
    @Test
    void generatedIdsAreTemporallyOrdered() throws InterruptedException {
        TransactionId first = TransactionId.newId();
        Thread.sleep(5);
        TransactionId second = TransactionId.newId();

        assertThat(second.value()).isGreaterThan(first.value());
    }
}
