ALTER TABLE orders
    ADD COLUMN order_type VARCHAR(12) NOT NULL DEFAULT 'LIMIT';

ALTER TABLE orders
    ADD COLUMN filled_quantity NUMERIC(18, 6) NOT NULL DEFAULT 0;

ALTER TABLE orders
    ALTER COLUMN price DROP NOT NULL;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_order_type CHECK (order_type IN ('LIMIT', 'MARKET'));

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_filled_quantity CHECK (filled_quantity >= 0 AND filled_quantity <= quantity);

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_limit_price CHECK (
        (order_type = 'LIMIT' AND price IS NOT NULL AND price > 0)
        OR (order_type = 'MARKET' AND price IS NULL)
    );
