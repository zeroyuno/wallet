CREATE TABLE transactions (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    account_id   UUID NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
    category_id  UUID REFERENCES categories (id) ON DELETE RESTRICT,
    type         VARCHAR(10) NOT NULL,
    amount       NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    date         DATE NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_transactions_user_id ON transactions (user_id);
CREATE INDEX idx_transactions_account_id ON transactions (account_id);
CREATE INDEX idx_transactions_category_id ON transactions (category_id);
CREATE INDEX idx_transactions_date ON transactions (date);
