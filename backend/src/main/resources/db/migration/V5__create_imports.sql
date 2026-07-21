CREATE TABLE imports (
    id                     UUID PRIMARY KEY,
    user_id                UUID NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    cursor_phase           VARCHAR(20) NOT NULL,
    cursor_record_date     DATE,
    accounts_imported      INT NOT NULL DEFAULT 0,
    categories_imported    INT NOT NULL DEFAULT 0,
    transactions_imported  INT NOT NULL DEFAULT 0,
    started_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_imports_user_id ON imports (user_id);
CREATE INDEX idx_imports_user_id_status ON imports (user_id, status);

CREATE TABLE import_errors (
    import_id    UUID NOT NULL REFERENCES imports (id) ON DELETE CASCADE,
    entity_type  VARCHAR(20) NOT NULL,
    external_id  VARCHAR(255) NOT NULL,
    reason       VARCHAR(500) NOT NULL,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_import_errors_import_id ON import_errors (import_id);

CREATE TABLE import_external_refs (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    entity_type  VARCHAR(20) NOT NULL,
    external_id  VARCHAR(255) NOT NULL,
    internal_id  UUID NOT NULL,
    UNIQUE (user_id, entity_type, external_id)
);
