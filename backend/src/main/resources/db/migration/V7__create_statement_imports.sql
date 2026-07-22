CREATE TABLE statement_imports (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL,
    account_id              UUID NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    transactions_imported   INT NOT NULL DEFAULT 0,
    failure_reason          VARCHAR(500),
    started_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_statement_imports_user_id ON statement_imports (user_id);

CREATE TABLE statement_import_errors (
    statement_import_id  UUID NOT NULL REFERENCES statement_imports (id) ON DELETE CASCADE,
    raw_text             VARCHAR(1000) NOT NULL,
    reason               VARCHAR(500) NOT NULL,
    occurred_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_statement_import_errors_import_id ON statement_import_errors (statement_import_id);

CREATE TABLE statement_import_line_hashes (
    id                        UUID PRIMARY KEY,
    user_id                   UUID NOT NULL,
    account_id                UUID NOT NULL,
    hash                      VARCHAR(64) NOT NULL,
    internal_transaction_id   UUID NOT NULL,
    UNIQUE (user_id, hash)
);
