package com.walletapp.backend.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdAndUserId(TransactionId id, UUID userId);

    boolean existsByIdAndUserId(TransactionId id, UUID userId);

    // Además de borrar la fila, registra un DeletedTransactionTombstone (feature 007, research.md #2)
    // para que el feed de sincronización pueda avisarle al cliente que este id desapareció.
    void deleteByIdAndUserId(TransactionId id, UUID userId);

    // Filtros (accountId, categoryId, dateFrom, dateTo) nullable — sin filtrar por ese criterio si es null.
    List<Transaction> findAllByUserId(UUID userId, UUID accountId, UUID categoryId, LocalDate dateFrom,
                                       LocalDate dateTo);

    // Ingresos menos gastos de esa cuenta; ZERO si no hay transacciones (usado para calcular el saldo).
    BigDecimal sumNetAmountForAccount(UUID userId, UUID accountId);

    // Feed de sincronización incremental (feature 007, research.md #1): creados/editados y eliminados
    // desde `since` (no-nullable; el caso "desde el principio" se resuelve pasando Instant.EPOCH),
    // ordenados por updatedAt/deletedAt ascendente, hasta `limit` filas cada uno.
    List<Transaction> findChangedSince(UUID userId, Instant since, int limit);

    List<DeletedTransactionTombstone> findDeletedSince(UUID userId, Instant since, int limit);

    // Cantidad total de cambios desde `since` (sin paginar) — usado solo para mostrar una barra de
    // progreso en el cliente ("258/1000"), no para la paginación en sí.
    long countChangedSince(UUID userId, Instant since);

    long countDeletedSince(UUID userId, Instant since);
}
