package com.walletapp.backend.walletimport.infrastructure.web;

import com.walletapp.backend.shared.security.AuthenticatedUser;
import com.walletapp.backend.walletimport.application.ImportService;
import com.walletapp.backend.walletimport.application.dto.ImportErrorView;
import com.walletapp.backend.walletimport.application.dto.ImportView;
import com.walletapp.backend.walletimport.infrastructure.web.dto.ImportErrorItemResponse;
import com.walletapp.backend.walletimport.infrastructure.web.dto.ImportResponse;
import com.walletapp.backend.walletimport.infrastructure.web.dto.StartImportRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportResponse start(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody StartImportRequest request) {
        log.info("POST /api/imports userId={}", principal.id());
        return toResponse(importService.start(principal.id(), request.walletApiToken()));
    }

    @GetMapping("/{id}")
    public ImportResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        log.info("GET /api/imports/{} userId={}", id, principal.id());
        return toResponse(importService.get(principal.id(), id));
    }

    private static ImportResponse toResponse(ImportView view) {
        return new ImportResponse(view.id(), view.status(), view.accountsImported(), view.categoriesImported(),
                view.transactionsImported(), view.errors().stream().map(ImportController::toErrorResponse).toList(),
                view.startedAt(), view.lastActivityAt());
    }

    private static ImportErrorItemResponse toErrorResponse(ImportErrorView error) {
        return new ImportErrorItemResponse(error.entityType(), error.externalId(), error.reason());
    }
}
