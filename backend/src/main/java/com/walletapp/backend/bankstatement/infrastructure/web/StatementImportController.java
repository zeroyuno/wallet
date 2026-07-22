package com.walletapp.backend.bankstatement.infrastructure.web;

import com.walletapp.backend.bankstatement.application.StatementImportService;
import com.walletapp.backend.bankstatement.application.dto.StatementImportView;
import com.walletapp.backend.bankstatement.application.dto.StatementLineErrorView;
import com.walletapp.backend.bankstatement.infrastructure.web.dto.StatementImportResponse;
import com.walletapp.backend.bankstatement.infrastructure.web.dto.StatementLineErrorResponse;
import com.walletapp.backend.shared.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/statement-imports")
public class StatementImportController {

    private static final Logger log = LoggerFactory.getLogger(StatementImportController.class);

    private final StatementImportService statementImportService;

    public StatementImportController(StatementImportService statementImportService) {
        this.statementImportService = statementImportService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatementImportResponse start(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam("accountId") UUID accountId) {
        log.info("POST /api/statement-imports userId={} accountId={}", principal.id(), accountId);
        StatementImportView view = statementImportService.start(principal.id(), accountId, readBytes(file));
        return toResponse(view);
    }

    @GetMapping("/{id}")
    public StatementImportResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        log.info("GET /api/statement-imports/{} userId={}", id, principal.id());
        return toResponse(statementImportService.get(principal.id(), id));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }

    private static StatementImportResponse toResponse(StatementImportView view) {
        return new StatementImportResponse(view.id(), view.accountId(), view.status(),
                view.transactionsImported(), view.errors().stream().map(StatementImportController::toErrorResponse)
                        .toList(), view.failureReason(), view.startedAt(), view.lastActivityAt());
    }

    private static StatementLineErrorResponse toErrorResponse(StatementLineErrorView error) {
        return new StatementLineErrorResponse(error.rawText(), error.reason());
    }
}
