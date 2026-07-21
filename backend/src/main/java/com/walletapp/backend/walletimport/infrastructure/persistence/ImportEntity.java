package com.walletapp.backend.walletimport.infrastructure.persistence;

import com.walletapp.backend.walletimport.domain.ImportCursorPhase;
import com.walletapp.backend.walletimport.domain.ImportStatus;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "imports")
public class ImportEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cursor_phase", nullable = false)
    private ImportCursorPhase cursorPhase;

    @Column(name = "cursor_record_date")
    private LocalDate cursorRecordDate;

    @Column(name = "accounts_imported", nullable = false)
    private int accountsImported;

    @Column(name = "categories_imported", nullable = false)
    private int categoriesImported;

    @Column(name = "transactions_imported", nullable = false)
    private int transactionsImported;

    // EAGER a propósito: ImportProcessor lee/mapea el Import completo (incluidos sus errores) fuera
    // de la transacción corta de findById/save (se ejecuta en el hilo @Async de ImportProcessor.run,
    // no dentro de una sesión de Hibernate abierta) — con el LAZY por defecto de @ElementCollection
    // eso lanza LazyInitializationException al mapear a dominio después de que la sesión ya cerró.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "import_errors", joinColumns = @JoinColumn(name = "import_id"))
    private List<ImportErrorEmbeddable> errors = new ArrayList<>();

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected ImportEntity() {
        // JPA
    }

    public ImportEntity(UUID id, UUID userId, ImportStatus status, ImportCursorPhase cursorPhase,
                         LocalDate cursorRecordDate, int accountsImported, int categoriesImported,
                         int transactionsImported, List<ImportErrorEmbeddable> errors, Instant startedAt,
                         Instant lastActivityAt) {
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

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public ImportCursorPhase getCursorPhase() {
        return cursorPhase;
    }

    public LocalDate getCursorRecordDate() {
        return cursorRecordDate;
    }

    public int getAccountsImported() {
        return accountsImported;
    }

    public int getCategoriesImported() {
        return categoriesImported;
    }

    public int getTransactionsImported() {
        return transactionsImported;
    }

    public List<ImportErrorEmbeddable> getErrors() {
        return errors;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }
}
