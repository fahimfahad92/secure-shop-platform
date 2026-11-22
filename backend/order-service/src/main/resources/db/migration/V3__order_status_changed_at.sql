-- When the order last changed status (ADR-0015). Confirm and cancel set it; the cancel window
-- (orders.cancel-window, ADR-0016) is measured from it, and stuck in-flight rows are found by it.
-- Existing rows have had their status since their last update, so updated_at is the best backfill.
ALTER TABLE orders ADD COLUMN status_changed_at TIMESTAMPTZ;

UPDATE orders SET status_changed_at = updated_at;

ALTER TABLE orders ALTER COLUMN status_changed_at SET NOT NULL;

CREATE INDEX idx_orders_status_status_changed_at ON orders (status, status_changed_at);
