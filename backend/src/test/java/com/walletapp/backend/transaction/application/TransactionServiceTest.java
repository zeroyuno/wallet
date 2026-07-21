package com.walletapp.backend.transaction.application;

import com.walletapp.backend.account.application.AccountService;
import com.walletapp.backend.account.application.CategoryService;
import com.walletapp.backend.transaction.application.dto.TransactionCommand;
import com.walletapp.backend.transaction.application.dto.TransactionFilter;
import com.walletapp.backend.transaction.application.dto.TransactionUpdateCommand;
import com.walletapp.backend.transaction.application.dto.TransactionView;
import com.walletapp.backend.transaction.domain.Transaction;
import com.walletapp.backend.transaction.domain.TransactionId;
import com.walletapp.backend.transaction.domain.TransactionRepository;
import com.walletapp.backend.transaction.domain.TransactionType;
import com.walletapp.backend.transaction.domain.exception.CategoryTypeMismatchException;
import com.walletapp.backend.transaction.domain.exception.DuplicateTransactionIdException;
import com.walletapp.backend.transaction.domain.exception.InvalidTransactionAccountException;
import com.walletapp.backend.transaction.domain.exception.InvalidTransactionCategoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    AccountService accountService;

    @Mock
    CategoryService categoryService;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @Test
    void createsTransactionForOwnedAccountWithoutCategory() {
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(true);
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);
        TransactionView result = service.create(userId, new TransactionCommand(null, TransactionType.EXPENSE,
                new BigDecimal("50"), LocalDate.of(2026, 7, 18), null, accountId, null));

        assertThat(result.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(result.amount()).isEqualByComparingTo("50");
        assertThat(result.categoryId()).isNull();
        assertThat(result.id()).isNotNull();
    }

    @Test
    void createRejectsAccountNotOwnedByUser() {
        when(accountService.existsOwnedByUser(any(), any())).thenReturn(false);

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThatThrownBy(() -> service.create(userId, new TransactionCommand(null, TransactionType.EXPENSE,
                new BigDecimal("50"), LocalDate.now(), null, accountId, null)))
                .isInstanceOf(InvalidTransactionAccountException.class);
    }

    @Test
    void createRejectsCategoryNotOwnedByUser() {
        UUID categoryId = UUID.randomUUID();
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(true);
        when(categoryService.findTypeIfOwnedByUser(userId, categoryId)).thenReturn(Optional.empty());

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThatThrownBy(() -> service.create(userId, new TransactionCommand(null, TransactionType.EXPENSE,
                new BigDecimal("50"), LocalDate.now(), null, accountId, categoryId)))
                .isInstanceOf(InvalidTransactionCategoryException.class);
    }

    @Test
    void createRejectsCategoryTypeMismatch() {
        UUID categoryId = UUID.randomUUID();
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(true);
        when(categoryService.findTypeIfOwnedByUser(userId, categoryId)).thenReturn(Optional.of("INCOME"));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThatThrownBy(() -> service.create(userId, new TransactionCommand(null, TransactionType.EXPENSE,
                new BigDecimal("50"), LocalDate.now(), null, accountId, categoryId)))
                .isInstanceOf(CategoryTypeMismatchException.class);
    }

    @Test
    void createRejectsNonPositiveAmount() {
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(true);

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThatThrownBy(() -> service.create(userId, new TransactionCommand(null, TransactionType.EXPENSE,
                BigDecimal.ZERO, LocalDate.now(), null, accountId, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRespectsClientSuppliedId() {
        UUID clientId = UUID.randomUUID();
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(true);
        when(transactionRepository.existsByIdAndUserId(TransactionId.of(clientId), userId)).thenReturn(false);
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);
        TransactionView result = service.create(userId, new TransactionCommand(clientId, TransactionType.EXPENSE,
                new BigDecimal("50"), LocalDate.now(), null, accountId, null));

        assertThat(result.id()).isEqualTo(clientId);
    }

    @Test
    void createRejectsDuplicateClientSuppliedId() {
        UUID clientId = UUID.randomUUID();
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(true);
        when(transactionRepository.existsByIdAndUserId(TransactionId.of(clientId), userId)).thenReturn(true);

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThatThrownBy(() -> service.create(userId, new TransactionCommand(clientId, TransactionType.EXPENSE,
                new BigDecimal("50"), LocalDate.now(), null, accountId, null)))
                .isInstanceOf(DuplicateTransactionIdException.class);
    }

    @Test
    void listsOnlyTransactionsOfTheGivenUser() {
        Transaction transaction = Transaction.create(Optional.empty(), userId, TransactionType.INCOME,
                new BigDecimal("100"), LocalDate.now(), null, accountId, null);
        when(transactionRepository.findAllByUserId(userId, null, null, null, null)).thenReturn(List.of(transaction));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);
        List<TransactionView> result = service.list(userId, TransactionFilter.none());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo("100");
    }

    @Test
    void getReturnsOwnedTransaction() {
        Transaction transaction = Transaction.create(Optional.empty(), userId, TransactionType.EXPENSE,
                new BigDecimal("50"), LocalDate.now(), null, accountId, null);
        when(transactionRepository.findByIdAndUserId(transaction.id(), userId)).thenReturn(Optional.of(transaction));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThat(service.get(userId, transaction.id()).amount()).isEqualByComparingTo("50");
    }

    @Test
    void getRejectsTransactionNotOwnedByUser() {
        when(transactionRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThatThrownBy(() -> service.get(userId, TransactionId.newId()))
                .isInstanceOf(com.walletapp.backend.transaction.domain.exception.TransactionNotFoundException.class);
    }

    @Test
    void listPassesFilterCriteriaToTheRepository() {
        UUID categoryId = UUID.randomUUID();
        LocalDate dateFrom = LocalDate.of(2026, 7, 1);
        LocalDate dateTo = LocalDate.of(2026, 7, 31);
        when(transactionRepository.findAllByUserId(userId, accountId, categoryId, dateFrom, dateTo))
                .thenReturn(List.of());

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);
        service.list(userId, new TransactionFilter(accountId, categoryId, dateFrom, dateTo));

        org.mockito.Mockito.verify(transactionRepository)
                .findAllByUserId(userId, accountId, categoryId, dateFrom, dateTo);
    }

    @Test
    void updateAppliesOnlyTheNewAmountNotTheOldPlusNew() {
        Transaction transaction = Transaction.create(Optional.empty(), userId, TransactionType.EXPENSE,
                new BigDecimal("50"), LocalDate.now(), null, accountId, null);
        when(transactionRepository.findByIdAndUserId(transaction.id(), userId)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);
        TransactionView result = service.update(userId, transaction.id(),
                new TransactionUpdateCommand(new BigDecimal("80"), LocalDate.now(), null, null));

        assertThat(result.amount()).isEqualByComparingTo("80");
    }

    @Test
    void updateRejectsTransactionNotOwnedByUser() {
        when(transactionRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThatThrownBy(() -> service.update(userId, TransactionId.newId(),
                new TransactionUpdateCommand(new BigDecimal("80"), LocalDate.now(), null, null)))
                .isInstanceOf(com.walletapp.backend.transaction.domain.exception.TransactionNotFoundException.class);
    }

    @Test
    void updateRejectsMismatchedCategoryType() {
        Transaction transaction = Transaction.create(Optional.empty(), userId, TransactionType.EXPENSE,
                new BigDecimal("50"), LocalDate.now(), null, accountId, null);
        UUID categoryId = UUID.randomUUID();
        when(transactionRepository.findByIdAndUserId(transaction.id(), userId)).thenReturn(Optional.of(transaction));
        when(categoryService.findTypeIfOwnedByUser(userId, categoryId)).thenReturn(Optional.of("INCOME"));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThatThrownBy(() -> service.update(userId, transaction.id(),
                new TransactionUpdateCommand(new BigDecimal("80"), LocalDate.now(), null, categoryId)))
                .isInstanceOf(CategoryTypeMismatchException.class);
    }

    @Test
    void deleteRemovesOwnedTransaction() {
        Transaction transaction = Transaction.create(Optional.empty(), userId, TransactionType.EXPENSE,
                new BigDecimal("50"), LocalDate.now(), null, accountId, null);
        when(transactionRepository.findByIdAndUserId(transaction.id(), userId)).thenReturn(Optional.of(transaction));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);
        service.delete(userId, transaction.id());

        org.mockito.Mockito.verify(transactionRepository).deleteByIdAndUserId(transaction.id(), userId);
    }

    @Test
    void deleteRejectsTransactionNotOwnedByUser() {
        when(transactionRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThatThrownBy(() -> service.delete(userId, TransactionId.newId()))
                .isInstanceOf(com.walletapp.backend.transaction.domain.exception.TransactionNotFoundException.class);
    }

    @Test
    void createFromExternalImportCreatesTransactionWithMatchingCategory() {
        UUID categoryId = UUID.randomUUID();
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(true);
        when(categoryService.findTypeIfOwnedByUser(userId, categoryId)).thenReturn(Optional.of("EXPENSE"));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);
        UUID id = service.createFromExternalImport(userId, "EXPENSE", new BigDecimal("30"), LocalDate.now(),
                "Super", accountId, categoryId, null, null, null, null, Set.of());

        assertThat(id).isNotNull();
    }

    // research.md #3: si el tipo de la categoría no coincide, se importa igual pero sin categoría,
    // en vez de fallar toda la importación de ese movimiento.
    @Test
    void createFromExternalImportDropsCategoryOnTypeMismatchInsteadOfFailing() {
        UUID categoryId = UUID.randomUUID();
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(true);
        when(categoryService.findTypeIfOwnedByUser(userId, categoryId)).thenReturn(Optional.of("INCOME"));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);
        UUID id = service.createFromExternalImport(userId, "EXPENSE", new BigDecimal("30"), LocalDate.now(),
                null, accountId, categoryId, null, null, null, null, Set.of());

        assertThat(id).isNotNull();
        org.mockito.ArgumentCaptor<Transaction> captor = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        org.mockito.Mockito.verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().categoryId()).isEmpty();
    }

    @Test
    void createFromExternalImportRejectsAccountNotOwnedByUser() {
        when(accountService.existsOwnedByUser(any(), any())).thenReturn(false);

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);

        assertThatThrownBy(() -> service.createFromExternalImport(userId, "EXPENSE", new BigDecimal("30"),
                LocalDate.now(), null, accountId, null, null, null, null, null, Set.of()))
                .isInstanceOf(InvalidTransactionAccountException.class);
    }

    // El resto de los campos propios de Wallet (counterParty/paymentType/recordState/
    // walletTransferId/labels) deben quedar en la Transaction guardada tal como llegan.
    @Test
    void createFromExternalImportPersistsAllWalletOnlyFields() {
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(true);
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionService service = new TransactionService(transactionRepository, accountService, categoryService);
        service.createFromExternalImport(userId, "EXPENSE", new BigDecimal("30"), LocalDate.now(), "Nota",
                accountId, null, "Starbucks", "CARD", "CONFIRMED", "wallet-transfer-1", Set.of("viaje", "trabajo"));

        org.mockito.ArgumentCaptor<Transaction> captor = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        org.mockito.Mockito.verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.counterParty()).contains("Starbucks");
        assertThat(saved.paymentType()).contains("CARD");
        assertThat(saved.recordState()).contains("CONFIRMED");
        assertThat(saved.walletTransferId()).contains("wallet-transfer-1");
        assertThat(saved.labels()).containsExactlyInAnyOrder("viaje", "trabajo");
    }
}
