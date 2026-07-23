package com.walletapp.backend.transaction.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataTransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);

    // El filtrado real pasa en JpaTransactionRepository (Java) en vez de con parámetros opcionales
    // en SQL: pasar un LocalDate null como bind parameter hace que Postgres/Hibernate no puedan
    // inferirle un tipo ("could not determine data type of parameter"; forzar CAST(...) tampoco
    // funciona porque un bind null sin tipo se manda como bytea y ese cast falla). A esta escala
    // (uso personal) traer todo y filtrar en memoria es simple, correcto, y evita ese problema.
    List<TransactionEntity> findAllByUserId(UUID userId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN t.type = com.walletapp.backend.transaction.domain.TransactionType.INCOME
                                      THEN t.amount ELSE -t.amount END), 0)
            FROM TransactionEntity t
            WHERE t.userId = :userId AND t.accountId = :accountId
            """)
    BigDecimal sumNetAmountForAccount(@Param("userId") UUID userId, @Param("accountId") UUID accountId);

    @Query("SELECT t FROM TransactionEntity t WHERE t.userId = :userId AND t.updatedAt > :since "
            + "ORDER BY t.updatedAt ASC, t.id ASC")
    List<TransactionEntity> findChangedSince(@Param("userId") UUID userId, @Param("since") Instant since,
                                              Pageable pageable);

    long countByUserIdAndUpdatedAtAfter(UUID userId, Instant since);
}
