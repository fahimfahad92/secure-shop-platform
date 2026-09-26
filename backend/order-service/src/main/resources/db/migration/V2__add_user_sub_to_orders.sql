-- Orders placed before Phase 3 have no owner and there is no correct value to backfill:
-- the column is NOT NULL because an unowned order is meaningless from here on, and this is
-- a development dataset, so the existing rows go.
DELETE FROM orders;

ALTER TABLE orders ADD COLUMN user_sub VARCHAR(36) NOT NULL;

CREATE INDEX idx_orders_user_sub ON orders (user_sub);
