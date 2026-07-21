package com.walletapp.backend.walletimport.application;

import com.walletapp.backend.account.application.AccountService;
import com.walletapp.backend.account.application.CategoryService;
import com.walletapp.backend.transaction.application.TransactionService;
import com.walletapp.backend.walletimport.application.dto.WalletAccountDto;
import com.walletapp.backend.walletimport.application.dto.WalletCategoryDto;
import com.walletapp.backend.walletimport.application.dto.WalletRecordDto;
import com.walletapp.backend.walletimport.domain.ExternalEntityType;
import com.walletapp.backend.walletimport.domain.ExternalReferenceRepository;
import com.walletapp.backend.walletimport.domain.Import;
import com.walletapp.backend.walletimport.domain.ImportCursorPhase;
import com.walletapp.backend.walletimport.domain.ImportRepository;
import com.walletapp.backend.walletimport.domain.ImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportProcessorTest {

    @Mock
    ImportRepository importRepository;

    @Mock
    AccountService accountService;

    @Mock
    CategoryService categoryService;

    @Mock
    TransactionService transactionService;

    // Fake en memoria (no Mockito mock): así save() y findInternalId() reflejan el mismo estado
    // dentro de una corrida, igual que la tabla real — necesario para probar dedup/resolución.
    private final ExternalReferenceRepository externalReferenceRepository = new InMemoryExternalReferenceRepository();

    private final UUID userId = UUID.randomUUID();

    @Test
    void importsAccountsCategoriesAndTransactionsEndToEnd() {
        Import imp = Import.create(userId);
        when(importRepository.findById(imp.id())).thenReturn(Optional.of(imp));
        when(accountService.createFromExternalImport(eq(userId), anyString(), anyString(), anyString(), any()))
                .thenReturn(UUID.randomUUID());
        when(categoryService.createFromExternalImport(eq(userId), anyString(), anyString()))
                .thenReturn(UUID.randomUUID());
        when(transactionService.createFromExternalImport(eq(userId), anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        FakeWalletImportGateway gateway = new FakeWalletImportGateway()
                .withAccounts(new WalletAccountDto("acc-1", "Efectivo", "Cash", "USD", new BigDecimal("100")))
                .withCategories(new WalletCategoryDto("cat-1", "Comida", null, null))
                .withRecords(new WalletRecordDto("rec-1", "acc-1", new BigDecimal("30"), LocalDate.now(),
                        "EXPENSE", "cat-1", "Super", null, "CARD", "CONFIRMED", null, List.of("viaje")));

        ImportProcessor processor = new ImportProcessor(importRepository, externalReferenceRepository, gateway,
                accountService, categoryService, transactionService);
        processor.run(imp.id().value(), "token");

        assertThat(imp.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(imp.cursorPhase()).isEqualTo(ImportCursorPhase.DONE);
        assertThat(imp.accountsImported()).isEqualTo(1);
        assertThat(imp.categoriesImported()).isEqualTo(1);
        assertThat(imp.transactionsImported()).isEqualTo(1);
        assertThat(imp.errors()).isEmpty();
    }

    @Test
    void skipsRecordsAlreadyMappedToAvoidDuplicates() {
        Import imp = Import.create(userId);
        when(importRepository.findById(imp.id())).thenReturn(Optional.of(imp));
        externalReferenceRepository.save(userId, ExternalEntityType.ACCOUNT, "acc-1", UUID.randomUUID());

        FakeWalletImportGateway gateway = new FakeWalletImportGateway()
                .withAccounts(new WalletAccountDto("acc-1", "Efectivo", "Cash", "USD", new BigDecimal("100")));

        ImportProcessor processor = new ImportProcessor(importRepository, externalReferenceRepository, gateway,
                accountService, categoryService, transactionService);
        processor.run(imp.id().value(), "token");

        verify(accountService, never()).createFromExternalImport(any(), any(), any(), any(), any());
        assertThat(imp.accountsImported()).isZero();
        assertThat(imp.status()).isEqualTo(ImportStatus.COMPLETED);
    }

    @Test
    void pausesForRateLimitAndKeepsCursorPhase() {
        Import imp = Import.create(userId);
        when(importRepository.findById(imp.id())).thenReturn(Optional.of(imp));

        FakeWalletImportGateway gateway = new FakeWalletImportGateway().rateLimitedAfter(0);

        ImportProcessor processor = new ImportProcessor(importRepository, externalReferenceRepository, gateway,
                accountService, categoryService, transactionService);
        processor.run(imp.id().value(), "token");

        assertThat(imp.status()).isEqualTo(ImportStatus.PAUSED_RATE_LIMIT);
        assertThat(imp.cursorPhase()).isEqualTo(ImportCursorPhase.ACCOUNTS);
    }

    // FR-007: un error puntual en un registro no debe interrumpir el resto de la importación.
    @Test
    void recordsPerItemErrorWithoutHaltingTheImport() {
        Import imp = Import.create(userId);
        when(importRepository.findById(imp.id())).thenReturn(Optional.of(imp));
        when(accountService.createFromExternalImport(eq(userId), anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Currency inválida"));

        FakeWalletImportGateway gateway = new FakeWalletImportGateway()
                .withAccounts(new WalletAccountDto("acc-1", "Efectivo", "Cash", "XXX", new BigDecimal("100")));

        ImportProcessor processor = new ImportProcessor(importRepository, externalReferenceRepository, gateway,
                accountService, categoryService, transactionService);
        processor.run(imp.id().value(), "token");

        assertThat(imp.errors()).hasSize(1);
        assertThat(imp.errors().get(0).entityType()).isEqualTo(ExternalEntityType.ACCOUNT);
        assertThat(imp.errors().get(0).externalId()).isEqualTo("acc-1");
        assertThat(imp.status()).isEqualTo(ImportStatus.COMPLETED);
    }

    @Test
    void resumesFromTransactionsPhaseWithoutRefetchingAccountsOrCategories() {
        Import imp = Import.create(userId);
        imp.advanceToCategories();
        imp.advanceToTransactions();
        when(importRepository.findById(imp.id())).thenReturn(Optional.of(imp));
        externalReferenceRepository.save(userId, ExternalEntityType.ACCOUNT, "acc-1", UUID.randomUUID());
        when(transactionService.createFromExternalImport(eq(userId), anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        FakeWalletImportGateway gateway = new FakeWalletImportGateway()
                .withAccounts(new WalletAccountDto("acc-1", "Efectivo", "Cash", "USD", new BigDecimal("100")))
                .withRecords(new WalletRecordDto("rec-1", "acc-1", new BigDecimal("30"), LocalDate.now(),
                        "EXPENSE", null, null, "Nota", null, null, null, List.of()));

        ImportProcessor processor = new ImportProcessor(importRepository, externalReferenceRepository, gateway,
                accountService, categoryService, transactionService);
        processor.run(imp.id().value(), "token");

        verify(accountService, never()).createFromExternalImport(any(), any(), any(), any(), any());
        assertThat(imp.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(imp.transactionsImported()).isEqualTo(1);
    }

    private static class InMemoryExternalReferenceRepository implements ExternalReferenceRepository {
        private final Map<String, UUID> store = new HashMap<>();

        @Override
        public Optional<UUID> findInternalId(UUID userId, ExternalEntityType entityType, String externalId) {
            return Optional.ofNullable(store.get(key(userId, entityType, externalId)));
        }

        @Override
        public void save(UUID userId, ExternalEntityType entityType, String externalId, UUID internalId) {
            store.put(key(userId, entityType, externalId), internalId);
        }

        private static String key(UUID userId, ExternalEntityType entityType, String externalId) {
            return userId + "|" + entityType + "|" + externalId;
        }
    }
}
