package com.walletapp.backend.account.infrastructure.persistence;

import com.walletapp.backend.account.domain.Account;
import com.walletapp.backend.account.domain.AccountId;
import com.walletapp.backend.account.domain.AccountRepository;
import com.walletapp.backend.account.domain.CurrencyCode;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaAccountRepository implements AccountRepository {

    private final SpringDataAccountRepository springDataRepository;

    JpaAccountRepository(SpringDataAccountRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Account save(Account account) {
        AccountEntity saved = springDataRepository.save(toEntity(account));
        return toDomain(saved);
    }

    @Override
    public List<Account> findAllByUserId(UUID userId) {
        return springDataRepository.findAllByUserId(userId).stream().map(JpaAccountRepository::toDomain).toList();
    }

    @Override
    public Optional<Account> findByIdAndUserId(AccountId id, UUID userId) {
        return springDataRepository.findByIdAndUserId(id.value(), userId).map(JpaAccountRepository::toDomain);
    }

    @Override
    public void deleteByIdAndUserId(AccountId id, UUID userId) {
        springDataRepository.deleteByIdAndUserId(id.value(), userId);
    }

    private static AccountEntity toEntity(Account account) {
        return new AccountEntity(
                account.id().value(),
                account.userId(),
                account.name(),
                account.type(),
                account.currency().value(),
                account.initialBalance(),
                account.createdAt());
    }

    private static Account toDomain(AccountEntity entity) {
        return Account.reconstitute(
                AccountId.of(entity.getId()),
                entity.getUserId(),
                entity.getName(),
                entity.getType(),
                new CurrencyCode(entity.getCurrency()),
                entity.getInitialBalance(),
                entity.getCreatedAt());
    }
}
