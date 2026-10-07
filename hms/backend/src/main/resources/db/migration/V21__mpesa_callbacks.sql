-- M-Pesa confirmations arrive from Safaricom with no organisation and no staff token. Two things are needed to receive them safely.
--
-- 1. A narrow owner-rights lookup, like staff login: given a CheckoutRequestID it says which organisation (and facility) the pending
--    payment belongs to, so the callback can then run inside that organisation's row-level security like any other write.
-- 2. A place to keep money that cannot be applied (unknown reference, amount that differs from the request, voided invoice, ...). It is
--    never discarded and never edited: the application role may insert but not change or delete, so a bug or a stolen credential in the
--    API cannot make received money disappear. Finance reads it as the schema owner (see docs/DEPLOY.md).

CREATE TABLE mpesa_unclaimed (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  received_at timestamptz NOT NULL DEFAULT now(),
  -- Why it was not applied. The callback is always acknowledged so Safaricom stops retrying; this row is the record.
  reason text NOT NULL CHECK (reason IN ('UNKNOWN_REFERENCE', 'AMOUNT_MISMATCH', 'INVOICE_VOID', 'OVERPAYMENT', 'RECEIPT_REUSED', 'INCOMPLETE_CALLBACK')),
  checkout_request_id text,
  merchant_request_id text,
  receipt text,
  amount numeric(12,2),
  phone text,
  result_code int,
  result_desc text CHECK (length(result_desc) <= 500),
  -- Filled when the reference was known but the money could not be applied.
  org_id uuid,
  payment_id uuid,
  raw jsonb NOT NULL
);
-- A retried callback is recorded once.
CREATE UNIQUE INDEX mpesa_unclaimed_checkout ON mpesa_unclaimed (checkout_request_id) WHERE checkout_request_id IS NOT NULL;
CREATE UNIQUE INDEX mpesa_unclaimed_receipt ON mpesa_unclaimed (receipt) WHERE receipt IS NOT NULL;
CREATE TRIGGER mpesa_unclaimed_append_only BEFORE UPDATE OR DELETE ON mpesa_unclaimed FOR EACH ROW EXECUTE FUNCTION forbid_change();
REVOKE UPDATE, DELETE, TRUNCATE ON mpesa_unclaimed FROM ${appUser};

CREATE FUNCTION mpesa_find_payment(p_checkout text)
RETURNS TABLE (org_id uuid, payment_id uuid, facility_id uuid)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS
$$ SELECT p.org_id, p.id, p.facility_id FROM payments p WHERE p.method = 'MPESA' AND p.mpesa_checkout_id = p_checkout LIMIT 1 $$;
REVOKE ALL ON FUNCTION mpesa_find_payment(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION mpesa_find_payment(text) TO ${appUser};
