package com.walletapp.backend.bankstatement.infrastructure.persistence;

import com.walletapp.backend.bankstatement.domain.StatementImportStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "statement_imports")
public class StatementImportEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatementImportStatus status;

    @Column(name = "transactions_imported", nullable = false)
    private int transactionsImported;

    // EAGER a propósito: se lee fuera de sesiones abiertas largas (procesamiento en @Async), mismo
    // motivo que ImportEntity.errors de la feature 005.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "statement_import_errors", joinColumns = @JoinColumn(name = "statement_import_id"))
    private List<StatementLineErrorEmbeddable> errors = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "statement_import_lines", joinColumns = @JoinColumn(name = "statement_import_id"))
    private List<StatementImportedLineEmbeddable> importedLines = new ArrayList<>();

    @Column(name = "expense_column_header")
    private String expenseColumnHeader;

    @Column(name = "income_column_header")
    private String incomeColumnHeader;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected StatementImportEntity() {
        // JPA
    }

    public StatementImportEntity(UUID id, UUID userId, UUID accountId, StatementImportStatus status,
                                  int transactionsImported, List<StatementLineErrorEmbeddable> errors,
                                  List<StatementImportedLineEmbeddable> importedLines, String expenseColumnHeader,
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

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public StatementImportStatus getStatus() {
        return status;
    }

    public int getTransactionsImported() {
        return transactionsImported;
    }

    public List<StatementLineErrorEmbeddable> getErrors() {
        return errors;
    }

    public List<StatementImportedLineEmbeddable> getImportedLines() {
        return importedLines;
    }

    public String getExpenseColumnHeader() {
        return expenseColumnHeader;
    }

    public String getIncomeColumnHeader() {
        return incomeColumnHeader;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }
}
