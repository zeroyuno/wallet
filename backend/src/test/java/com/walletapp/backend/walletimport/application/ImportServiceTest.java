package com.walletapp.backend.walletimport.application;

import com.walletapp.backend.walletimport.application.dto.ImportView;
import com.walletapp.backend.walletimport.application.dto.WalletAccountDto;
import com.walletapp.backend.walletimport.domain.Import;
import com.walletapp.backend.walletimport.domain.ImportId;
import com.walletapp.backend.walletimport.domain.ImportRepository;
import com.walletapp.backend.walletimport.domain.exception.ImportNotFoundException;
import com.walletapp.backend.walletimport.domain.exception.InvalidWalletTokenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    ImportRepository importRepository;

    @Mock
    ImportProcessor importProcessor;

    private final UUID userId = UUID.randomUUID();

    @Test
    void startValidatesTokenCreatesImportAndTriggersProcessing() {
        FakeWalletImportGateway gateway = new FakeWalletImportGateway()
                .withAccounts(new WalletAccountDto("acc-1", "Efectivo", "Cash", "USD", BigDecimal.TEN));
        when(importRepository.findMostRecentPausedByUserId(userId)).thenReturn(Optional.empty());
        when(importRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ImportService service = new ImportService(importRepository, gateway, importProcessor);
        ImportView view = service.start(userId, "valid-token");

        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        assertThat(view.id()).isNotNull();
        verify(importProcessor).run(view.id(), "valid-token");
    }

    @Test
    void startRejectsInvalidTokenWithoutCreatingAnImport() {
        FakeWalletImportGateway gateway = new FakeWalletImportGateway().rejectingToken("bad-token");
        when(importRepository.findMostRecentPausedByUserId(userId)).thenReturn(Optional.empty());

        ImportService service = new ImportService(importRepository, gateway, importProcessor);

        assertThatThrownBy(() -> service.start(userId, "bad-token"))
                .isInstanceOf(InvalidWalletTokenException.class);
        verify(importRepository, org.mockito.Mockito.never()).save(any());
        verify(importProcessor, org.mockito.Mockito.never()).run(any(), any());
    }

    @Test
    void startResumesAPausedImportInsteadOfCreatingANewOne() {
        Import paused = Import.create(userId);
        paused.pauseForRateLimit();
        FakeWalletImportGateway gateway = new FakeWalletImportGateway();
        when(importRepository.findMostRecentPausedByUserId(userId)).thenReturn(Optional.of(paused));
        when(importRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ImportService service = new ImportService(importRepository, gateway, importProcessor);
        ImportView view = service.start(userId, "token");

        assertThat(view.id()).isEqualTo(paused.id().value());
        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        verify(importProcessor).run(paused.id().value(), "token");
    }

    @Test
    void getReturnsOwnedImport() {
        Import imp = Import.create(userId);
        when(importRepository.findByIdAndUserId(imp.id(), userId)).thenReturn(Optional.of(imp));

        ImportService service = new ImportService(importRepository, new FakeWalletImportGateway(), importProcessor);
        ImportView view = service.get(userId, imp.id().value());

        assertThat(view.id()).isEqualTo(imp.id().value());
    }

    @Test
    void getRejectsImportNotOwnedByUser() {
        when(importRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        ImportService service = new ImportService(importRepository, new FakeWalletImportGateway(), importProcessor);

        assertThatThrownBy(() -> service.get(userId, ImportId.newId().value()))
                .isInstanceOf(ImportNotFoundException.class);
    }
}
