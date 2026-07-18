-- Permite hasta 9 decimales en el monto de una transacción (antes NUMERIC(19,4)).
ALTER TABLE transactions ALTER COLUMN amount TYPE NUMERIC(28, 9);
