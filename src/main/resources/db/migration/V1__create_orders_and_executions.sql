CREATE TABLE orders (
    id UUID PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity NUMERIC(18, 6) NOT NULL CHECK (quantity > 0),
    price NUMERIC(18, 6) NOT NULL CHECK (price > 0),
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_symbol ON orders (symbol);
CREATE INDEX idx_orders_created_at ON orders (created_at);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE executions (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id),
    symbol VARCHAR(20) NOT NULL,
    quantity NUMERIC(18, 6) NOT NULL CHECK (quantity > 0),
    price NUMERIC(18, 6) NOT NULL CHECK (price > 0),
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_executions_order_id ON executions (order_id);
CREATE INDEX idx_executions_symbol ON executions (symbol);
