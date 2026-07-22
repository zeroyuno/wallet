package com.walletapp.backend.walletimport.application;

import com.walletapp.backend.account.application.AccountService;
import com.walletapp.backend.account.application.CategoryService;
import com.walletapp.backend.transaction.application.TransactionService;
import com.walletapp.backend.walletimport.application.dto.WalletAccountDto;
import com.walletapp.backend.walletimport.application.dto.WalletCategoryDto;
import com.walletapp.backend.walletimport.application.dto.WalletRecordDto;
import com.walletapp.backend.walletimport.application.dto.WalletRecordPage;
import com.walletapp.backend.walletimport.domain.ExternalReferenceRepository;
import com.walletapp.backend.walletimport.domain.Import;
import com.walletapp.backend.walletimport.domain.ImportCursorPhase;
import com.walletapp.backend.walletimport.domain.ImportId;
import com.walletapp.backend.walletimport.domain.ImportRepository;
import com.walletapp.backend.walletimport.domain.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.walletapp.backend.walletimport.domain.ExternalEntityType.ACCOUNT;
import static com.walletapp.backend.walletimport.domain.ExternalEntityType.CATEGORY;
import static com.walletapp.backend.walletimport.domain.ExternalEntityType.TRANSACTION;

/**
 * Orquesta la importación en segundo plano (research.md #7). Es un bean aparte de
 * {@link ImportService} a propósito: así el {@code @Async} de {@link #run} se dispara a través del
 * proxy de Spring cuando {@code ImportService.start} lo invoca como colaborador inyectado, en vez de
 * una auto-invocación dentro de la misma clase (que Spring no interceptaría).
 */
@Component
class ImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImportProcessor.class);
    private static final int RECORDS_PAGE_SIZE = 100;

    private final ImportRepository importRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final WalletImportGateway walletImportGateway;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;

    ImportProcessor(ImportRepository importRepository, ExternalReferenceRepository externalReferenceRepository,
                     WalletImportGateway walletImportGateway, AccountService accountService,
                     CategoryService categoryService, TransactionService transactionService) {
        this.importRepository = importRepository;
        this.externalReferenceRepository = externalReferenceRepository;
        this.walletImportGateway = walletImportGateway;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
    }

    @Async
    void run(UUID importId, String walletApiToken) {
        Import imp = importRepository.findById(ImportId.of(importId))
                .orElseThrow(() -> new IllegalStateException("Import not found: " + importId));
        try {
            if (imp.cursorPhase() == ImportCursorPhase.ACCOUNTS) {
                importAccounts(imp, walletApiToken);
                imp.advanceToCategories();
                importRepository.save(imp);
            }
            if (imp.cursorPhase() == ImportCursorPhase.CATEGORIES) {
                importCategories(imp, walletApiToken);
                imp.advanceToTransactions();
                importRepository.save(imp);
            }
            if (imp.cursorPhase() == ImportCursorPhase.TRANSACTIONS) {
                importTransactions(imp, walletApiToken);
                imp.markCompleted();
                importRepository.save(imp);
            }
        } catch (RateLimitExceededException e) {
            log.info("Import {} paused by Wallet rate limit at phase {}", importId, imp.cursorPhase());
            imp.pauseForRateLimit();
            importRepository.save(imp);
        }
    }

    private void importAccounts(Import imp, String walletApiToken) {
        List<WalletAccountDto> accounts = walletImportGateway.fetchAccounts(walletApiToken);
        for (WalletAccountDto dto : accounts) {
            if (externalReferenceRepository.findInternalId(imp.userId(), ACCOUNT, dto.id()).isPresent()) {
                continue;
            }
            try {
                UUID internalId = accountService.createFromExternalImport(imp.userId(), dto.name(),
                        mapAccountType(dto.accountType()), dto.currencyCode(), dto.initialBalance());
                externalReferenceRepository.save(imp.userId(), ACCOUNT, dto.id(), internalId);
                imp.recordAccountImported();
            } catch (RuntimeException e) {
                imp.recordError(ACCOUNT, dto.id(), e.getMessage());
            }
        }
        importRepository.save(imp);
    }

    private void importCategories(Import imp, String walletApiToken) {
        List<WalletCategoryDto> categories = walletImportGateway.fetchCategories(walletApiToken);

        // Primera pasada: crear todas sin padre (research.md #4).
        for (WalletCategoryDto dto : categories) {
            if (externalReferenceRepository.findInternalId(imp.userId(), CATEGORY, dto.id()).isPresent()) {
                continue;
            }
            try {
                String type = "income".equalsIgnoreCase(dto.groupId()) ? "INCOME" : "EXPENSE";
                UUID internalId = categoryService.createFromExternalImport(imp.userId(), dto.name(), type);
                externalReferenceRepository.save(imp.userId(), CATEGORY, dto.id(), internalId);
                imp.recordCategoryImported();
            } catch (RuntimeException e) {
                imp.recordError(CATEGORY, dto.id(), e.getMessage());
            }
        }
        importRepository.save(imp);

        // Segunda pasada: resolver jerarquía padre/hijo ya con todos los ids propios asignados.
        for (WalletCategoryDto dto : categories) {
            if (dto.parentId() == null) {
                continue;
            }
            Optional<UUID> ownId = externalReferenceRepository.findInternalId(imp.userId(), CATEGORY, dto.id());
            Optional<UUID> parentId = externalReferenceRepository
                    .findInternalId(imp.userId(), CATEGORY, dto.parentId());
            if (ownId.isEmpty() || parentId.isEmpty()) {
                continue;
            }
            try {
                categoryService.setParentCategoryIfOwnedByUser(imp.userId(), ownId.get(), parentId.get());
            } catch (RuntimeException e) {
                imp.recordError(CATEGORY, dto.id(), "No se pudo asignar categoría padre: " + e.getMessage());
            }
        }
        importRepository.save(imp);
    }

    private void importTransactions(Import imp, String walletApiToken) {
        // Wallet devuelve los movimientos del más nuevo al más viejo. El filtro `recordDate` debe
        // quedar FIJO durante toda la corrida (calculado una sola vez acá, al valor del cursor de
        // reanudación si lo hay) — recalcularlo en cada página a partir de imp.cursorRecordDate()
        // (que se va actualizando página a página con la fecha más vieja vista hasta el momento)
        // termina subiendo el piso del filtro y excluye justo los movimientos más viejos que todavía
        // faltan traer, cortando la importación mucho antes de llegar al historial completo.
        LocalDate fromDate = imp.cursorRecordDate();
        int offset = 0;
        boolean hasMore = true;
        while (hasMore) {
            WalletRecordPage page = walletImportGateway.fetchRecords(walletApiToken, fromDate, offset,
                    RECORDS_PAGE_SIZE);
            for (WalletRecordDto dto : page.records()) {
                if (externalReferenceRepository.findInternalId(imp.userId(), TRANSACTION, dto.id()).isEmpty()) {
                    importOneTransaction(imp, dto);
                }
                imp.updateTransactionsCursor(dto.recordDate());
            }
            importRepository.save(imp);
            hasMore = page.hasMore();
            offset += RECORDS_PAGE_SIZE;
        }
    }

    private void importOneTransaction(Import imp, WalletRecordDto dto) {
        try {
            UUID accountId = externalReferenceRepository.findInternalId(imp.userId(), ACCOUNT, dto.accountId())
                    .orElseThrow(() -> new IllegalStateException("Cuenta asociada no encontrada: " + dto.accountId()));
            UUID categoryId = dto.categoryId() == null ? null
                    : externalReferenceRepository.findInternalId(imp.userId(), CATEGORY, dto.categoryId())
                            .orElse(null);
            Set<String> labels = dto.labels() == null ? Set.of() : new LinkedHashSet<>(dto.labels());
            UUID internalId = transactionService.createFromExternalImport(imp.userId(), dto.recordType().toUpperCase(),
                    dto.amount(), dto.recordDate(), dto.note(), accountId, categoryId, dto.counterParty(),
                    dto.paymentType(), dto.recordState(), dto.transferId(), labels);
            externalReferenceRepository.save(imp.userId(), TRANSACTION, dto.id(), internalId);
            imp.recordTransactionImported();
        } catch (RuntimeException e) {
            imp.recordError(TRANSACTION, dto.id(), e.getMessage());
        }
    }

    private static String mapAccountType(String walletAccountType) {
        if (walletAccountType == null) {
            return "OTHER";
        }
        return switch (walletAccountType) {
            case "Cash" -> "CASH";
            case "CurrentAccount", "SavingAccount" -> "BANK";
            case "CreditCard" -> "CREDIT_CARD";
            default -> "OTHER";
        };
    }
}
