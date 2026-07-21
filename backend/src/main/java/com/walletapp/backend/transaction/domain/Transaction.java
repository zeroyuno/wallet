package com.walletapp.backend.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Transaction {

    private final TransactionId id;
    private final UUID userId;
    private final TransactionType type;
    private BigDecimal amount;
    private LocalDate date;
    private String description;
    private final UUID accountId;
    private UUID categoryId;
    private final Instant createdAt;
    // Campos de solo lectura poblados al importar desde BudgetBakers Wallet (feature 005) — no
    // editables vía update(), ausentes (null/vacío) en una transacción creada manualmente.
    private final String counterParty;
    private final String paymentType;
    private final String recordState;
    private final String walletTransferId;
    private final Set<String> labels;

    private Transaction(TransactionId id, UUID userId, TransactionType type, BigDecimal amount, LocalDate date,
                         String description, UUID accountId, UUID categoryId, Instant createdAt,
                         String counterParty, String paymentType, String recordState, String walletTransferId,
                         Set<String> labels) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.createdAt = createdAt;
        this.counterParty = counterParty;
        this.paymentType = paymentType;
        this.recordState = recordState;
        this.walletTransferId = walletTransferId;
        this.labels = new LinkedHashSet<>(labels);
    }

    // id es opcional: si el cliente lo provee (creación offline, FR-011) se usa tal cual; si no,
    // se genera uno nuevo (UUID v7, ver TransactionId).
    public static Transaction create(Optional<TransactionId> id, UUID userId, TransactionType type,
                                      BigDecimal amount, LocalDate date, String description, UUID accountId,
                                      UUID categoryId) {
        return create(id, userId, type, amount, date, description, accountId, categoryId, null, null, null, null,
                Set.of());
    }

    // Variante usada por la importación desde Wallet (feature 005): además de los campos propios,
    // captura counterParty/paymentType/recordState/walletTransferId/labels tal como vienen de Wallet
    // (ver research.md e ImportProcessor) — el resto de la app no los setea ni los edita.
    public static Transaction create(Optional<TransactionId> id, UUID userId, TransactionType type,
                                      BigDecimal amount, LocalDate date, String description, UUID accountId,
                                      UUID categoryId, String counterParty, String paymentType, String recordState,
                                      String walletTransferId, Set<String> labels) {
        requirePositiveAmount(amount);
        requireNonNull(date, "date");
        return new Transaction(id.orElseGet(TransactionId::newId), userId, type, amount, date, description,
                accountId, categoryId, Instant.now(), counterParty, paymentType, recordState, walletTransferId,
                labels == null ? Set.of() : labels);
    }

    public static Transaction reconstitute(TransactionId id, UUID userId, TransactionType type, BigDecimal amount,
                                            LocalDate date, String description, UUID accountId, UUID categoryId,
                                            Instant createdAt, String counterParty, String paymentType,
                                            String recordState, String walletTransferId, Set<String> labels) {
        return new Transaction(id, userId, type, amount, date, description, accountId, categoryId, createdAt,
                counterParty, paymentType, recordState, walletTransferId, labels == null ? Set.of() : labels);
    }

    // type y accountId son inmutables tras la creación (FR-005, ver research.md).
    public void update(BigDecimal amount, LocalDate date, String description, UUID categoryId) {
        requirePositiveAmount(amount);
        requireNonNull(date, "date");
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.categoryId = categoryId;
    }

    private static void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }
    }

    private static void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Transaction " + field + " must not be null");
        }
    }

    public TransactionId id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public TransactionType type() {
        return type;
    }

    public BigDecimal amount() {
        return amount;
    }

    public LocalDate date() {
        return date;
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public UUID accountId() {
        return accountId;
    }

    public Optional<UUID> categoryId() {
        return Optional.ofNullable(categoryId);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<String> counterParty() {
        return Optional.ofNullable(counterParty);
    }

    public Optional<String> paymentType() {
        return Optional.ofNullable(paymentType);
    }

    public Optional<String> recordState() {
        return Optional.ofNullable(recordState);
    }

    public Optional<String> walletTransferId() {
        return Optional.ofNullable(walletTransferId);
    }

    public Set<String> labels() {
        return Collections.unmodifiableSet(labels);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
