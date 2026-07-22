package com.walletapp.backend.bankstatement.application;

import com.walletapp.backend.account.application.AccountService;
import com.walletapp.backend.bankstatement.application.dto.StatementImportView;
import com.walletapp.backend.bankstatement.domain.StatementImport;
import com.walletapp.backend.bankstatement.domain.StatementImportId;
import com.walletapp.backend.bankstatement.domain.StatementImportRepository;
import com.walletapp.backend.bankstatement.domain.exception.InvalidStatementAccountException;
import com.walletapp.backend.bankstatement.domain.exception.StatementImportNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementImportServiceTest {

    @Mock
    StatementImportRepository statementImportRepository;

    @Mock
    AccountService accountService;

    @Mock
    StatementImportProcessor statementImportProcessor;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @Test
    void startValidatesAccountCreatesImportAndTriggersProcessing() {
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(true);
        when(statementImportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        byte[] pdfBytes = {1, 2, 3};

        StatementImportService service = new StatementImportService(statementImportRepository, accountService,
                statementImportProcessor);
        StatementImportView view = service.start(userId, accountId, pdfBytes);

        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        assertThat(view.accountId()).isEqualTo(accountId);
        verify(statementImportProcessor).run(view.id(), pdfBytes);
    }

    @Test
    void startRejectsAccountNotOwnedByUserWithoutCreatingAnImport() {
        when(accountService.existsOwnedByUser(userId, accountId)).thenReturn(false);

        StatementImportService service = new StatementImportService(statementImportRepository, accountService,
                statementImportProcessor);

        assertThatThrownBy(() -> service.start(userId, accountId, new byte[]{1}))
                .isInstanceOf(InvalidStatementAccountException.class);
        verify(statementImportRepository, never()).save(any());
        verify(statementImportProcessor, never()).run(any(), any());
    }

    @Test
    void getReturnsOwnedImport() {
        StatementImport statementImport = StatementImport.start(userId, accountId);
        when(statementImportRepository.findByIdAndUserId(statementImport.id(), userId))
                .thenReturn(Optional.of(statementImport));

        StatementImportService service = new StatementImportService(statementImportRepository, accountService,
                statementImportProcessor);
        StatementImportView view = service.get(userId, statementImport.id().value());

        assertThat(view.id()).isEqualTo(statementImport.id().value());
    }

    @Test
    void getRejectsImportNotOwnedByUser() {
        when(statementImportRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        StatementImportService service = new StatementImportService(statementImportRepository, accountService,
                statementImportProcessor);

        assertThatThrownBy(() -> service.get(userId, StatementImportId.newId().value()))
                .isInstanceOf(StatementImportNotFoundException.class);
    }
}
