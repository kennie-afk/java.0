CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE tenants (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name          varchar(200) NOT NULL,
    slug          varchar(80)  NOT NULL UNIQUE,
    created_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email         varchar(200) NOT NULL,
    full_name     varchar(200) NOT NULL,
    password_hash varchar(200) NOT NULL,
    role          varchar(40)  NOT NULL DEFAULT 'OPERATOR',
    status        varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT users_email_unique UNIQUE (email)
);
CREATE INDEX idx_users_tenant ON users(tenant_id);

CREATE TABLE suppliers (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name          varchar(200) NOT NULL,
    county        varchar(100) NOT NULL,
    lead_time_hours    integer NOT NULL,
    cold_chain    boolean NOT NULL DEFAULT false,
    reliability   numeric(4,3) NOT NULL DEFAULT 0.900,
    status        varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_suppliers_tenant ON suppliers(tenant_id, status);

CREATE TABLE products (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sku             varchar(60)  NOT NULL,
    name            varchar(200) NOT NULL,
    category        varchar(60)  NOT NULL,
    unit            varchar(20)  NOT NULL,
    perishable      boolean NOT NULL DEFAULT false,
    requires_cold_chain boolean NOT NULL DEFAULT false,
    shelf_life_hours    integer NOT NULL,
    list_price_cents    bigint  NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT products_sku_unique UNIQUE (tenant_id, sku)
);
CREATE INDEX idx_products_tenant_category ON products(tenant_id, category);

CREATE TABLE offers (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    supplier_id   uuid NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    product_id    uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    cost_cents    bigint  NOT NULL,
    available_qty integer NOT NULL,
    harvested_at  timestamptz,
    status        varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT offers_unique UNIQUE (supplier_id, product_id)
);
CREATE INDEX idx_offers_routing ON offers(tenant_id, product_id, status, cost_cents);

CREATE TABLE customers (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name          varchar(200) NOT NULL,
    phone         varchar(30)  NOT NULL,
    county        varchar(100) NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_customers_tenant ON customers(tenant_id);

CREATE TABLE orders (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    reference      varchar(30) NOT NULL,
    customer_id    uuid NOT NULL REFERENCES customers(id),
    status         varchar(30) NOT NULL DEFAULT 'ROUTED',
    revenue_cents  bigint NOT NULL DEFAULT 0,
    cost_cents     bigint NOT NULL DEFAULT 0,
    margin_cents   bigint NOT NULL DEFAULT 0,
    placed_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT orders_reference_unique UNIQUE (tenant_id, reference)
);
CREATE INDEX idx_orders_tenant_placed ON orders(tenant_id, placed_at DESC);

CREATE TABLE order_lines (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    order_id        uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      uuid NOT NULL REFERENCES products(id),
    supplier_id     uuid REFERENCES suppliers(id),
    quantity        integer NOT NULL,
    unit_price_cents bigint NOT NULL,
    unit_cost_cents bigint NOT NULL DEFAULT 0,
    status          varchar(30) NOT NULL DEFAULT 'PENDING',
    routing_reason  varchar(200),
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_lines_order ON order_lines(order_id);
CREATE INDEX idx_order_lines_tenant_status ON order_lines(tenant_id, status);
