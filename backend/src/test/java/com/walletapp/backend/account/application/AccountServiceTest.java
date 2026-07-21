package com.walletapp.backend.account.application;

import com.walletapp.backend.account.application.dto.AccountCommand;
import com.walletapp.backend.account.application.dto.AccountView;
import com.walletapp.backend.account.domain.Account;
import com.walletapp.backend.account.domain.AccountId;
import com.walletapp.backend.account.domain.AccountRepository;
import com.walletapp.backend.account.domain.AccountType;
import com.walletapp.backend.account.domain.CurrencyCode;
import com.walletapp.backend.account.domain.exception.AccountNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    AccountRepository accountRepository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createsAccountForOwner() {
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AccountService service = new AccountService(accountRepository);
        AccountView result = service.create(userId,
                new AccountCommand("Efectivo", AccountType.CASH, "USD", new BigDecimal("100")));

        assertThat(result.name()).isEqualTo("Efectivo");
        assertThat(result.type()).isEqualTo(AccountType.CASH);
        assertThat(result.currency()).isEqualTo("USD");
    }

    @Test
    void rejectsAccountWithBlankName() {
        AccountService service = new AccountService(accountRepository);

        assertThatThrownBy(() -> service.create(userId,
                new AccountCommand(" ", AccountType.CASH, "USD", BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsOnlyAccountsOfTheGivenUser() {
        Account account = Account.create(userId, "Banco", AccountType.BANK, new CurrencyCode("USD"), BigDecimal.TEN);
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account));

        AccountService service = new AccountService(accountRepository);
        List<AccountView> result = service.list(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Banco");
    }

    @Test
    void updateRejectsAccountNotOwnedByUser() {
        when(accountRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        AccountService service = new AccountService(accountRepository);

        assertThatThrownBy(() -> service.update(userId, AccountId.newId(),
                new AccountCommand("Nuevo nombre", AccountType.CASH, "USD", BigDecimal.ZERO)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void deleteRemovesOwnedAccount() {
        Account account = Account.create(userId, "Efectivo", AccountType.CASH, new CurrencyCode("USD"), BigDecimal.ZERO);
        when(accountRepository.findByIdAndUserId(account.id(), userId)).thenReturn(Optional.of(account));

        AccountService service = new AccountService(accountRepository);
        service.delete(userId, account.id());

        verify(accountRepository).deleteByIdAndUserId(account.id(), userId);
    }

    @Test
    void existsOwnedByUserReflectsRepository() {
        Account account = Account.create(userId, "Efectivo", AccountType.CASH, new CurrencyCode("USD"), BigDecimal.ZERO);
        when(accountRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUserId(AccountId.of(account.id().value()), userId))
                .thenReturn(Optional.of(account));

        AccountService service = new AccountService(accountRepository);

        assertThat(service.existsOwnedByUser(userId, account.id().value())).isTrue();
        assertThat(service.existsOwnedByUser(userId, UUID.randomUUID())).isFalse();
    }

    @Test
    void getInitialBalanceIfOwnedByUserReturnsEmptyWhenNotOwned() {
        Account account = Account.create(userId, "Efectivo", AccountType.CASH, new CurrencyCode("USD"), new BigDecimal("42"));
        when(accountRepository.findByIdAndUserId(AccountId.of(account.id().value()), userId))
                .thenReturn(Optional.of(account));

        AccountService service = new AccountService(accountRepository);

        assertThat(service.getInitialBalanceIfOwnedByUser(userId, account.id().value()))
                .contains(new BigDecimal("42"));
        assertThat(service.getInitialBalanceIfOwnedByUser(UUID.randomUUID(), account.id().value())).isEmpty();
    }

    @Test
    void createFromExternalImportCreatesAccountAndReturnsItsId() {
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AccountService service = new AccountService(accountRepository);
        UUID id = service.createFromExternalImport(userId, "Efectivo Wallet", "CASH", "USD",
                new BigDecimal("50"));

        assertThat(id).isNotNull();
    }
}
