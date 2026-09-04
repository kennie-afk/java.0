CREATE TABLE rent_invoices (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lease_id           UUID          NOT NULL REFERENCES leases(id),
    unit_id            UUID          NOT NULL,
    tenant_id          UUID          NOT NULL,
    landlord_id        UUID          NOT NULL,
    invoice_number     VARCHAR(32)   NOT NULL,
    period_start       DATE          NOT NULL,
    period_end         DATE          NOT NULL,
    due_date           DATE          NOT NULL,
    amount_due         NUMERIC(14,2) NOT NULL,
    amount_paid        NUMERIC(14,2) NOT NULL DEFAULT 0,
    status             VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    last_reminder_day  INT,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_invoice_number UNIQUE (invoice_number),
    CONSTRAINT uq_invoice_lease_period UNIQUE (lease_id, period_start),
    CONSTRAINT ck_invoice_status CHECK (status IN ('PENDING','PARTIAL','PAID','OVERDUE','WRITTEN_OFF')),
    CONSTRAINT ck_invoice_amounts CHECK (amount_due >= 0 AND amount_paid >= 0),
    CONSTRAINT ck_invoice_period CHECK (period_end >= period_start)
);

CREATE INDEX idx_invoices_lease    ON rent_invoices (lease_id, period_start DESC);
CREATE INDEX idx_invoices_landlord ON rent_invoices (landlord_id, due_date DESC);
CREATE INDEX idx_invoices_tenant   ON rent_invoices (tenant_id, due_date DESC);
CREATE INDEX idx_invoices_chaseable ON rent_invoices (due_date)
    WHERE status IN ('PENDING','PARTIAL','OVERDUE');

CREATE TABLE rent_payments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id     UUID          NOT NULL REFERENCES rent_invoices(id),
    lease_id       UUID          NOT NULL,
    payment_id     UUID,
    amount         NUMERIC(14,2) NOT NULL,
    method         VARCHAR(24)   NOT NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    mpesa_receipt  VARCHAR(40),
    recorded_by    UUID,
    note           VARCHAR(500),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    paid_at        TIMESTAMP,
    CONSTRAINT uq_rent_payment_payment_id UNIQUE (payment_id),
    CONSTRAINT ck_rent_payment_method CHECK (method IN ('MPESA_STK','MPESA_PAYBILL','BANK','CASH')),
    CONSTRAINT ck_rent_payment_status CHECK (status IN ('PENDING','CONFIRMED','FAILED')),
    CONSTRAINT ck_rent_payment_amount CHECK (amount > 0)
);

CREATE INDEX idx_rent_payments_invoice ON rent_payments (invoice_id);
CREATE INDEX idx_rent_payments_lease   ON rent_payments (lease_id);
