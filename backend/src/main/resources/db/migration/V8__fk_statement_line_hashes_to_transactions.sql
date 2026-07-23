-- Limpia hashes huérfanos que ya puedan existir (apuntando a transacciones borradas manualmente
-- antes de este fix) y agrega la FK que faltaba para que esto no vuelva a pasar: al borrar una
-- transacción, su hash de deduplicación de estado de cuenta se borra con ella.
DELETE FROM statement_import_line_hashes
WHERE internal_transaction_id NOT IN (SELECT id FROM transactions);

ALTER TABLE statement_import_line_hashes
    ADD CONSTRAINT fk_statement_import_line_hashes_transaction
    FOREIGN KEY (internal_transaction_id) REFERENCES transactions (id) ON DELETE CASCADE;
