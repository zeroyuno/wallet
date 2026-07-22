package com.walletapp.backend.bankstatement.domain;

import java.util.Optional;
import java.util.UUID;

public interface StatementImportRepository {

    StatementImport save(StatementImport statementImport);

    Optional<StatementImport> findById(StatementImportId id);

    Optional<StatementImport> findByIdAndUserId(StatementImportId id, UUID userId);
}
