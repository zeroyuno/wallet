package com.walletapp.backend.account.application;

import com.walletapp.backend.account.application.dto.AccountCommand;
import com.walletapp.backend.account.application.dto.AccountView;
import com.walletapp.backend.account.domain.Account;
import com.walletapp.backend.account.domain.AccountId;
import com.walletapp.backend.account.domain.AccountRepository;
import com.walletapp.backend.account.domain.CurrencyCode;
import com.walletapp.backend.account.domain.exception.AccountNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountView create(UUID userId, AccountCommand command) {
        Account account = Account.create(userId, command.name(), command.type(),
                new CurrencyCode(command.currency()), command.initialBalance());
        return toView(accountRepository.save(account));
    }

    public List<AccountView> list(UUID userId) {
        return accountRepository.findAllByUserId(userId).stream().map(AccountService::toView).toList();
    }

    public AccountView update(UUID userId, AccountId id, AccountCommand command) {
        Account account = findOwned(userId, id);
        account.rename(command.name(), command.type(), new CurrencyCode(command.currency()));
        return toView(accountRepository.save(account));
    }

    public void delete(UUID userId, AccountId id) {
        findOwned(userId, id);
        accountRepository.deleteByIdAndUserId(id, userId);
    }

    // Métodos de solo lectura para otros bounded contexts (ej. transaction) — devuelven únicamente
    // tipos primitivos, nunca AccountView ni ningún tipo de account.domain, para no arrastrar una
    // dependencia de dominio a través de la frontera del contexto (ver research.md de la feature 003).
    public boolean existsOwnedByUser(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUserId(AccountId.of(accountId), userId).isPresent();
    }

    public Optional<BigDecimal> getInitialBalanceIfOwnedByUser(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUserId(AccountId.of(accountId), userId).map(Account::initialBalance);
    }

    private Account findOwned(UUID userId, AccountId id) {
        return accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + id.value()));
    }

    private static AccountView toView(Account account) {
        return new AccountView(account.id().value(), account.name(), account.type(),
                account.currency().value(), account.initialBalance());
    }
}
