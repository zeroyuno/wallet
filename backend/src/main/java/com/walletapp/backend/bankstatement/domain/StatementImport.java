package com.walletapp.backend.bankstatement.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class StatementImport {

    private final StatementImportId id;
    private final UUID userId;
    private final UUID accountId;
    private StatementImportStatus status;
    private int transactionsImported;
    private final List<StatementLineError> errors;
    private String failureReason;
    private final Instant startedAt;
    private Instant lastActivityAt;

    private StatementImport(StatementImportId id, UUID userId, UUID accountId, StatementImportStatus status,
                             int transactionsImported, List<StatementLineError> errors, String failureReason,
                             Instant startedAt, Instant lastActivityAt) {
        this.id = id;
        this.userId = userId;
        this.accountId = accountId;
        this.status = status;
        this.transactionsImported = transactionsImported;
        this.errors = new ArrayList<>(errors);
        this.failureReason = failureReason;
        this.startedAt = startedAt;
        this.lastActivityAt = lastActivityAt;
    }

    public static StatementImport start(UUID userId, UUID accountId) {
        Instant now = Instant.now();
        return new StatementImport(StatementImportId.newId(), userId, accountId, StatementImportStatus.IN_PROGRESS,
                0, List.of(), null, now, now);
    }

    public static StatementImport reconstitute(StatementImportId id, UUID userId, UUID accountId,
                                                StatementImportStatus status, int transactionsImported,
                                                List<StatementLineError> errors, String failureReason,
                                                Instant startedAt, Instant lastActivityAt) {
        return new StatementImport(id, userId, accountId, status, transactionsImported, errors, failureReason,
                startedAt, lastActivityAt);
    }

    public void recordTransactionImported() {
        this.transactionsImported++;
        touch();
    }

    public void recordLineError(String rawText, String reason) {
        errors.add(new StatementLineError(rawText, reason, Instant.now()));
        touch();
    }

    public void markCompleted() {
        this.status = StatementImportStatus.COMPLETED;
        touch();
    }

    public void markFailed(String reason) {
        this.status = StatementImportStatus.FAILED;
        this.failureReason = reason;
        touch();
    }

    private void touch() {
        this.lastActivityAt = Instant.now();
    }

    public StatementImportId id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID accountId() {
        return accountId;
    }

    public StatementImportStatus status() {
        return status;
    }

    public int transactionsImported() {
        return transactionsImported;
    }

    public List<StatementLineError> errors() {
        return Collections.unmodifiableList(errors);
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant lastActivityAt() {
        return lastActivityAt;
    }
}
