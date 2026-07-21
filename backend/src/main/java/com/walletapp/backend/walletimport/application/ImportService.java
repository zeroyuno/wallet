package com.walletapp.backend.walletimport.application;

import com.walletapp.backend.walletimport.application.dto.ImportErrorView;
import com.walletapp.backend.walletimport.application.dto.ImportView;
import com.walletapp.backend.walletimport.domain.Import;
import com.walletapp.backend.walletimport.domain.ImportError;
import com.walletapp.backend.walletimport.domain.ImportId;
import com.walletapp.backend.walletimport.domain.ImportRepository;
import com.walletapp.backend.walletimport.domain.exception.ImportNotFoundException;
import com.walletapp.backend.walletimport.domain.exception.InvalidWalletTokenException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * A propósito SIN {@code @Transactional} de clase (a diferencia de AccountService/CategoryService/
 * TransactionService): {@link #start} dispara el procesamiento en segundo plano después de guardar
 * el registro {@code Import}, y ese guardado debe quedar confirmado (commit) ANTES de que
 * {@link ImportProcessor#run} lo busque en otro hilo. Envolver todo en una transacción de clase
 * retrasaría el commit hasta que el método retorne, después de ya haber disparado el {@code @Async}
 * — una carrera real. Cada llamada al repositorio ya es transaccional por sí sola (Spring Data JPA).
 */
@Component
public class ImportService {

    private final ImportRepository importRepository;
    private final WalletImportGateway walletImportGateway;
    private final ImportProcessor importProcessor;

    public ImportService(ImportRepository importRepository, WalletImportGateway walletImportGateway,
                          ImportProcessor importProcessor) {
        this.importRepository = importRepository;
        this.walletImportGateway = walletImportGateway;
        this.importProcessor = importProcessor;
    }

    public ImportView start(UUID userId, String walletApiToken) {
        Optional<Import> paused = importRepository.findMostRecentPausedByUserId(userId);
        Import imp;
        if (paused.isPresent()) {
            imp = paused.get();
            imp.resume();
        } else {
            validateToken(walletApiToken);
            imp = Import.create(userId);
        }
        imp = importRepository.save(imp);
        importProcessor.run(imp.id().value(), walletApiToken);
        return toView(imp);
    }

    public ImportView get(UUID userId, UUID importId) {
        Import imp = importRepository.findByIdAndUserId(ImportId.of(importId), userId)
                .orElseThrow(() -> new ImportNotFoundException("Import not found: " + importId));
        return toView(imp);
    }

    // FR-009: token inválido rechazado con 400 antes de crear ningún registro de Import.
    private void validateToken(String walletApiToken) {
        if (walletApiToken == null || walletApiToken.isBlank()) {
            throw new InvalidWalletTokenException("Wallet API token must not be blank");
        }
        walletImportGateway.fetchAccounts(walletApiToken);
    }

    private static ImportView toView(Import imp) {
        return new ImportView(imp.id().value(), imp.status().name(), imp.accountsImported(),
                imp.categoriesImported(), imp.transactionsImported(), imp.errors().stream()
                        .map(ImportService::toErrorView).toList(),
                imp.startedAt(), imp.lastActivityAt());
    }

    private static ImportErrorView toErrorView(ImportError error) {
        return new ImportErrorView(error.entityType().name(), error.externalId(), error.reason());
    }
}
