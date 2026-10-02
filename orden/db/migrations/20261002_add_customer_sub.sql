-- Run once against the existing PostgreSQL schema before deploying the new Orden image.
-- Do not infer ownership of legacy rows from customer_email; leave them NULL.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_sub varchar(255);
CREATE INDEX IF NOT EXISTS orders_customer_sub_created_at_idx
    ON orders (customer_sub, created_at DESC)
    WHERE customer_sub IS NOT NULL;
