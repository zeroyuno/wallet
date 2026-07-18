package com.walletapp.backend.transaction.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdAndUserId(TransactionId id, UUID userId);

    boolean existsByIdAndUserId(TransactionId id, UUID userId);

    void deleteByIdAndUserId(TransactionId id, UUID userId);

    // Filtros (accountId, categoryId, dateFrom, dateTo) nullable — sin filtrar por ese criterio si es null.
    List<Transaction> findAllByUserId(UUID userId, UUID accountId, UUID categoryId, LocalDate dateFrom,
                                       LocalDate dateTo);

    // Ingresos menos gastos de esa cuenta; ZERO si no hay transacciones (usado para calcular el saldo).
    BigDecimal sumNetAmountForAccount(UUID userId, UUID accountId);
}
