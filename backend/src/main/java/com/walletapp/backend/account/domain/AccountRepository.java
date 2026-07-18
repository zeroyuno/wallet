package com.walletapp.backend.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Account save(Account account);

    List<Account> findAllByUserId(UUID userId);

    Optional<Account> findByIdAndUserId(AccountId id, UUID userId);

    void deleteByIdAndUserId(AccountId id, UUID userId);
}
