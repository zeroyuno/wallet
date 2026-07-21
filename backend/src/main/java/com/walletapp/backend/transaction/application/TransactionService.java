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
import com.walletapp.backend.transaction.domain.exception.TransactionNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;

    public TransactionService(TransactionRepository transactionRepository, AccountService accountService,
                               CategoryService categoryService) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
    }

    public TransactionView create(UUID userId, TransactionCommand command) {
        validateAccount(userId, command.accountId());
        validateCategory(userId, command.categoryId(), command.type());

        Optional<TransactionId> id = Optional.ofNullable(command.id()).map(TransactionId::of);
        if (id.isPresent() && transactionRepository.existsByIdAndUserId(id.get(), userId)) {
            throw new DuplicateTransactionIdException("Transaction id already exists: " + command.id());
        }

        Transaction transaction = Transaction.create(id, userId, command.type(), command.amount(), command.date(),
                command.description(), command.accountId(), command.categoryId());
        return toView(transactionRepository.save(transaction));
    }

    // FR-007: un usuario puede "ver" una transacción propia puntual (no solo listarlas todas).
    public TransactionView get(UUID userId, TransactionId id) {
        return toView(findOwned(userId, id));
    }

    public List<TransactionView> list(UUID userId, TransactionFilter filter) {
        return transactionRepository
                .findAllByUserId(userId, filter.accountId(), filter.categoryId(), filter.dateFrom(), filter.dateTo())
                .stream()
                .map(TransactionService::toView)
                .toList();
    }

    public TransactionView update(UUID userId, TransactionId id, TransactionUpdateCommand command) {
        Transaction transaction = findOwned(userId, id);
        validateCategory(userId, command.categoryId(), transaction.type());
        transaction.update(command.amount(), command.date(), command.description(), command.categoryId());
        return toView(transactionRepository.save(transaction));
    }

    public void delete(UUID userId, TransactionId id) {
        findOwned(userId, id);
        transactionRepository.deleteByIdAndUserId(id, userId);
    }

    private Transaction findOwned(UUID userId, TransactionId id) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id.value()));
    }

    // saldo actual = saldo inicial de la cuenta (account.application) + ingresos - gastos (propios).
    // No se muta ni desnormaliza ningún campo en accounts — se calcula bajo demanda (ver research.md).
    public BigDecimal getAccountBalance(UUID userId, UUID accountId) {
        BigDecimal initialBalance = accountService.getInitialBalanceIfOwnedByUser(userId, accountId)
                .orElseThrow(() -> new InvalidTransactionAccountException(
                        "Account not found or not owned: " + accountId));
        return initialBalance.add(transactionRepository.sumNetAmountForAccount(userId, accountId));
    }

    // Método de escritura para otros bounded contexts (ej. walletimport) — recibe y devuelve
    // únicamente tipos primitivos, nunca TransactionType ni ningún tipo de transaction.domain
    // (mismo criterio que accountService/categoryService, ver research.md de la feature 005).
    // A diferencia de create(), si la categoría no coincide en tipo no falla toda la operación:
    // el movimiento se importa igual pero sin esa categoría (research.md #3, limitación aceptada).
    public UUID createFromExternalImport(UUID userId, String transactionTypeName, BigDecimal amount,
                                          LocalDate date, String description, UUID accountId, UUID categoryId) {
        validateAccount(userId, accountId);
        TransactionType type = TransactionType.valueOf(transactionTypeName);

        UUID resolvedCategoryId = null;
        if (categoryId != null) {
            String categoryType = categoryService.findTypeIfOwnedByUser(userId, categoryId).orElse(null);
            if (type.name().equals(categoryType)) {
                resolvedCategoryId = categoryId;
            }
        }

        Transaction transaction = Transaction.create(Optional.empty(), userId, type, amount, date, description,
                accountId, resolvedCategoryId);
        return transactionRepository.save(transaction).id().value();
    }

    private void validateAccount(UUID userId, UUID accountId) {
        if (!accountService.existsOwnedByUser(userId, accountId)) {
            throw new InvalidTransactionAccountException("Account not found or not owned: " + accountId);
        }
    }

    private void validateCategory(UUID userId, UUID categoryId, TransactionType type) {
        if (categoryId == null) {
            return;
        }
        String categoryType = categoryService.findTypeIfOwnedByUser(userId, categoryId)
                .orElseThrow(() -> new InvalidTransactionCategoryException(
                        "Category not found or not owned: " + categoryId));
        if (!categoryType.equals(type.name())) {
            throw new CategoryTypeMismatchException("Category type does not match transaction type");
        }
    }

    private static TransactionView toView(Transaction transaction) {
        return new TransactionView(transaction.id().value(), transaction.type(), transaction.amount(),
                transaction.date(), transaction.description().orElse(null), transaction.accountId(),
                transaction.categoryId().orElse(null));
    }
}
