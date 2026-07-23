ALTER TABLE statement_imports
    ADD COLUMN expense_column_header VARCHAR(200),
    ADD COLUMN income_column_header  VARCHAR(200);

CREATE TABLE statement_import_lines (
    statement_import_id  UUID NOT NULL REFERENCES statement_imports (id) ON DELETE CASCADE,
    line_date             DATE NOT NULL,
    amount                NUMERIC(28, 9) NOT NULL,
    type                  VARCHAR(10) NOT NULL,
    description           VARCHAR(500) NOT NULL,
    column_header         VARCHAR(200) NOT NULL
);

CREATE INDEX idx_statement_import_lines_import_id ON statement_import_lines (statement_import_id);
