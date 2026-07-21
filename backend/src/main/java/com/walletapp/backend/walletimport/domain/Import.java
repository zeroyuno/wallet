package com.walletapp.backend.walletimport.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Import {

    private final ImportId id;
    private final UUID userId;
    private ImportStatus status;
    private ImportCursorPhase cursorPhase;
    private LocalDate cursorRecordDate;
    private int accountsImported;
    private int categoriesImported;
    private int transactionsImported;
    private final List<ImportError> errors;
    private final Instant startedAt;
    private Instant lastActivityAt;

    private Import(ImportId id, UUID userId, ImportStatus status, ImportCursorPhase cursorPhase,
                    LocalDate cursorRecordDate, int accountsImported, int categoriesImported,
                    int transactionsImported, List<ImportError> errors, Instant startedAt, Instant lastActivityAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.cursorPhase = cursorPhase;
        this.cursorRecordDate = cursorRecordDate;
        this.accountsImported = accountsImported;
        this.categoriesImported = categoriesImported;
        this.transactionsImported = transactionsImported;
        this.errors = new ArrayList<>(errors);
        this.startedAt = startedAt;
        this.lastActivityAt = lastActivityAt;
    }

    public static Import create(UUID userId) {
        Instant now = Instant.now();
        return new Import(ImportId.newId(), userId, ImportStatus.IN_PROGRESS, ImportCursorPhase.ACCOUNTS, null,
                0, 0, 0, List.of(), now, now);
    }

    public static Import reconstitute(ImportId id, UUID userId, ImportStatus status, ImportCursorPhase cursorPhase,
                                       LocalDate cursorRecordDate, int accountsImported, int categoriesImported,
                                       int transactionsImported, List<ImportError> errors, Instant startedAt,
                                       Instant lastActivityAt) {
        return new Import(id, userId, status, cursorPhase, cursorRecordDate, accountsImported, categoriesImported,
                transactionsImported, errors, startedAt, lastActivityAt);
    }

    public void resume() {
        this.status = ImportStatus.IN_PROGRESS;
        touch();
    }

    public void recordAccountImported() {
        this.accountsImported++;
        touch();
    }

    public void recordCategoryImported() {
        this.categoriesImported++;
        touch();
    }

    public void recordTransactionImported() {
        this.transactionsImported++;
        touch();
    }

    public void recordError(ExternalEntityType entityType, String externalId, String reason) {
        errors.add(new ImportError(entityType, externalId, reason, Instant.now()));
        touch();
    }

    public void advanceToCategories() {
        this.cursorPhase = ImportCursorPhase.CATEGORIES;
        touch();
    }

    public void advanceToTransactions() {
        this.cursorPhase = ImportCursorPhase.TRANSACTIONS;
        touch();
    }

    public void updateTransactionsCursor(LocalDate recordDate) {
        this.cursorRecordDate = recordDate;
        touch();
    }

    public void pauseForRateLimit() {
        this.status = ImportStatus.PAUSED_RATE_LIMIT;
        touch();
    }

    public void markCompleted() {
        this.status = ImportStatus.COMPLETED;
        this.cursorPhase = ImportCursorPhase.DONE;
        touch();
    }

    private void touch() {
        this.lastActivityAt = Instant.now();
    }

    public ImportId id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public ImportStatus status() {
        return status;
    }

    public ImportCursorPhase cursorPhase() {
        return cursorPhase;
    }

    public LocalDate cursorRecordDate() {
        return cursorRecordDate;
    }

    public int accountsImported() {
        return accountsImported;
    }

    public int categoriesImported() {
        return categoriesImported;
    }

    public int transactionsImported() {
        return transactionsImported;
    }

    public List<ImportError> errors() {
        return Collections.unmodifiableList(errors);
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant lastActivityAt() {
        return lastActivityAt;
    }
}
