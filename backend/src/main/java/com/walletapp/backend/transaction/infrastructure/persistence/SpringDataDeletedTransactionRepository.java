package com.walletapp.backend.transaction.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataDeletedTransactionRepository extends JpaRepository<DeletedTransactionEntity, UUID> {

    @Query("""
            SELECT d FROM DeletedTransactionEntity d
            WHERE d.userId = :userId AND d.deletedAt > :since
            ORDER BY d.deletedAt ASC, d.transactionId ASC
            """)
    List<DeletedTransactionEntity> findChangedSince(@Param("userId") UUID userId, @Param("since") Instant since,
                                                      Pageable pageable);
}
