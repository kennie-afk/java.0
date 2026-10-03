-- M-Pesa moves whole shillings. amount_cents is what was collected (rounded up), due_cents what the
-- order or invoice asked for. Existing rows were collected as ceil(amount): keep amount, due = amount
-- rounded as it was recorded, since the exact figure was not stored before this migration.
ALTER TABLE mpesa_payments ADD COLUMN due_cents bigint;
UPDATE mpesa_payments SET due_cents = amount_cents;
ALTER TABLE mpesa_payments ALTER COLUMN due_cents SET NOT NULL;
