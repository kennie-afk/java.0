ALTER TABLE payments ADD COLUMN IF NOT EXISTS bill_ref_number VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_payments_bill_ref ON payments (bill_ref_number)
    WHERE bill_ref_number IS NOT NULL;

ALTER TABLE payments ADD COLUMN IF NOT EXISTS mpesa_transaction_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_mpesa_transaction_id
    ON payments (mpesa_transaction_id) WHERE mpesa_transaction_id IS NOT NULL;
