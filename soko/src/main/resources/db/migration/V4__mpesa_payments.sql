CREATE TABLE mpesa_payments (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    purpose              varchar(20) NOT NULL,
    reference_id         uuid NOT NULL,
    msisdn               varchar(15) NOT NULL,
    amount_cents         bigint NOT NULL,
    merchant_request_id  varchar(60),
    checkout_request_id  varchar(60),
    mpesa_receipt_number varchar(40),
    status               varchar(20) NOT NULL DEFAULT 'PENDING',
    result_desc          varchar(200),
    initiated_at         timestamptz NOT NULL DEFAULT now(),
    completed_at         timestamptz,
    CONSTRAINT mpesa_payments_checkout_unique UNIQUE (checkout_request_id)
);
CREATE INDEX idx_mpesa_payments_reference ON mpesa_payments(purpose, reference_id, status);
CREATE INDEX idx_mpesa_payments_receipt ON mpesa_payments(mpesa_receipt_number)
    WHERE mpesa_receipt_number IS NOT NULL;
