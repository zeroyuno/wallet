ALTER TABLE transactions ADD COLUMN counter_party       VARCHAR(255);
ALTER TABLE transactions ADD COLUMN payment_type        VARCHAR(50);
ALTER TABLE transactions ADD COLUMN record_state        VARCHAR(50);
ALTER TABLE transactions ADD COLUMN wallet_transfer_id  VARCHAR(255);

CREATE TABLE labels (
    id       UUID PRIMARY KEY,
    user_id  UUID NOT NULL,
    name     VARCHAR(100) NOT NULL,
    UNIQUE (user_id, name)
);

CREATE TABLE transaction_labels (
    transaction_id  UUID NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    label_id        UUID NOT NULL REFERENCES labels (id) ON DELETE CASCADE,
    PRIMARY KEY (transaction_id, label_id)
);

CREATE INDEX idx_transaction_labels_label_id ON transaction_labels (label_id);
