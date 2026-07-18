package com.walletapp.backend.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataAccountRepository extends JpaRepository<AccountEntity, UUID> {

    List<AccountEntity> findAllByUserId(UUID userId);

    Optional<AccountEntity> findByIdAndUserId(UUID id, UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
