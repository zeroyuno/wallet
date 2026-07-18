CREATE TABLE accounts (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL,
    name             VARCHAR(255) NOT NULL,
    type             VARCHAR(20) NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    initial_balance  NUMERIC(19, 4) NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);

CREATE TABLE categories (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    name                VARCHAR(255) NOT NULL,
    type                VARCHAR(20) NOT NULL,
    parent_category_id  UUID REFERENCES categories (id),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_categories_user_type_name UNIQUE (user_id, type, name)
);

CREATE INDEX idx_categories_user_id ON categories (user_id);
