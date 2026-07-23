ALTER TABLE transactions ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
UPDATE transactions SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE transactions ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX idx_transactions_user_id_updated_at ON transactions (user_id, updated_at);

CREATE TABLE deleted_transactions (
    row_id      UUID PRIMARY KEY,
    id          UUID NOT NULL,
    user_id     UUID NOT NULL,
    deleted_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_deleted_transactions_user_id_deleted_at ON deleted_transactions (user_id, deleted_at);
