package com.walletapp.backend.bankstatement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    private final List<StatementImportedLine> importedLines;
    private String expenseColumnHeader;
    private String incomeColumnHeader;
    private String failureReason;
    private final Instant startedAt;
    private Instant lastActivityAt;

    private StatementImport(StatementImportId id, UUID userId, UUID accountId, StatementImportStatus status,
                             int transactionsImported, List<StatementLineError> errors,
                             List<StatementImportedLine> importedLines, String expenseColumnHeader,
                             String incomeColumnHeader, String failureReason, Instant startedAt,
                             Instant lastActivityAt) {
        this.id = id;
        this.userId = userId;
        this.accountId = accountId;
        this.status = status;
        this.transactionsImported = transactionsImported;
        this.errors = new ArrayList<>(errors);
        this.importedLines = new ArrayList<>(importedLines);
        this.expenseColumnHeader = expenseColumnHeader;
        this.incomeColumnHeader = incomeColumnHeader;
        this.failureReason = failureReason;
        this.startedAt = startedAt;
        this.lastActivityAt = lastActivityAt;
    }

    public static StatementImport start(UUID userId, UUID accountId) {
        Instant now = Instant.now();
        return new StatementImport(StatementImportId.newId(), userId, accountId, StatementImportStatus.IN_PROGRESS,
                0, List.of(), List.of(), null, null, null, now, now);
    }

    public static StatementImport reconstitute(StatementImportId id, UUID userId, UUID accountId,
                                                StatementImportStatus status, int transactionsImported,
                                                List<StatementLineError> errors,
                                                List<StatementImportedLine> importedLines, String expenseColumnHeader,
                                                String incomeColumnHeader, String failureReason, Instant startedAt,
                                                Instant lastActivityAt) {
        return new StatementImport(id, userId, accountId, status, transactionsImported, errors, importedLines,
                expenseColumnHeader, incomeColumnHeader, failureReason, startedAt, lastActivityAt);
    }

    public void recordColumnHeaders(String expenseColumnHeader, String incomeColumnHeader) {
        this.expenseColumnHeader = expenseColumnHeader;
        this.incomeColumnHeader = incomeColumnHeader;
        touch();
    }

    public void recordTransactionImported(LocalDate date, BigDecimal amount, String type, String description,
                                           String columnHeader) {
        this.transactionsImported++;
        importedLines.add(new StatementImportedLine(date, amount, type, description, columnHeader));
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

    public List<StatementImportedLine> importedLines() {
        return Collections.unmodifiableList(importedLines);
    }

    public String expenseColumnHeader() {
        return expenseColumnHeader;
    }

    public String incomeColumnHeader() {
        return incomeColumnHeader;
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
