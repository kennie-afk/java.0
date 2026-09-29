-- What Soko-the-platform earns from a distributor tenant, as opposed to what
-- that distributor earns from their own customers (orders.margin_cents,
-- unaffected by any of this). See com.soko.billing for the code that reads
-- and writes these tables.

CREATE TABLE subscriptions (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan               varchar(20) NOT NULL,
    monthly_fee_cents  bigint NOT NULL,
    commission_bps     integer NOT NULL,
    started_at         timestamptz NOT NULL DEFAULT now(),
    ended_at           timestamptz
);
-- At most one *active* subscription per tenant at a time; a plan change
-- ends the old row and inserts a new one rather than mutating in place.
CREATE UNIQUE INDEX uq_subscriptions_one_active ON subscriptions(tenant_id)
    WHERE ended_at IS NULL;

CREATE TABLE platform_commissions (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    order_id          uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    plan              varchar(20) NOT NULL,
    commission_bps    integer NOT NULL,
    gross_cents       bigint NOT NULL,
    commission_cents  bigint NOT NULL,
    status            varchar(20) NOT NULL DEFAULT 'ACCRUED',
    invoice_id        uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT platform_commissions_order_unique UNIQUE (order_id)
);
CREATE INDEX idx_platform_commissions_billing ON platform_commissions(tenant_id, status, created_at);

CREATE TABLE invoices (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    reference               varchar(30) NOT NULL,
    period_start            timestamptz NOT NULL,
    period_end              timestamptz NOT NULL,
    subscription_fee_cents  bigint NOT NULL,
    commission_cents        bigint NOT NULL,
    total_cents             bigint NOT NULL,
    status                  varchar(20) NOT NULL DEFAULT 'ISSUED',
    issued_at               timestamptz NOT NULL DEFAULT now(),
    due_at                  timestamptz NOT NULL,
    paid_at                 timestamptz,
    CONSTRAINT invoices_reference_unique UNIQUE (tenant_id, reference)
);
CREATE INDEX idx_invoices_tenant_status ON invoices(tenant_id, status);

ALTER TABLE platform_commissions
    ADD CONSTRAINT fk_platform_commissions_invoice
    FOREIGN KEY (invoice_id) REFERENCES invoices(id);

-- Append-only. A correction is a new offsetting entry, never an UPDATE or
-- DELETE on an existing row -- what makes sum(entries) a proof, not a claim.
CREATE TABLE platform_ledger (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    entry_type     varchar(30) NOT NULL,
    reference_type varchar(20) NOT NULL,
    reference_id   uuid NOT NULL,
    amount_cents   bigint NOT NULL,
    description    varchar(200),
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_platform_ledger_tenant ON platform_ledger(tenant_id, created_at DESC);
CREATE INDEX idx_platform_ledger_reference ON platform_ledger(reference_type, reference_id);
