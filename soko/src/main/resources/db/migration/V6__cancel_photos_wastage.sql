ALTER TABLE orders ADD COLUMN cancelled_at timestamptz;
ALTER TABLE orders ADD COLUMN cancel_reason varchar(200);

ALTER TABLE products ADD COLUMN photo_url varchar(500);

CREATE TABLE wastage_records (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    offer_id     uuid NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    product_id   uuid NOT NULL REFERENCES products(id),
    supplier_id  uuid NOT NULL REFERENCES suppliers(id),
    quantity     integer NOT NULL,
    reason       varchar(30) NOT NULL DEFAULT 'EXPIRED',
    value_cents  bigint NOT NULL,
    recorded_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_wastage_tenant ON wastage_records(tenant_id, recorded_at DESC);
