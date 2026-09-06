ALTER TABLE users ADD COLUMN supplier_id uuid REFERENCES suppliers(id) ON DELETE CASCADE;
ALTER TABLE users ADD COLUMN customer_id uuid REFERENCES customers(id) ON DELETE CASCADE;

CREATE INDEX idx_users_supplier ON users(supplier_id) WHERE supplier_id IS NOT NULL;
CREATE INDEX idx_users_customer ON users(customer_id) WHERE customer_id IS NOT NULL;

ALTER TABLE order_lines ADD COLUMN dispatched_at timestamptz;
ALTER TABLE order_lines ADD COLUMN delivered_at  timestamptz;
ALTER TABLE order_lines ADD COLUMN tracking_note varchar(200);

CREATE INDEX idx_order_lines_supplier_status
    ON order_lines(supplier_id, status) WHERE supplier_id IS NOT NULL;

ALTER TABLE orders ADD COLUMN placed_by_customer boolean NOT NULL DEFAULT false;

CREATE INDEX idx_orders_customer ON orders(customer_id, placed_at DESC);
