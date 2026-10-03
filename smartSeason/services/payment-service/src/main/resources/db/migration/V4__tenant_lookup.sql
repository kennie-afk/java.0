-- The M-Pesa callback is unauthenticated, so no tenant is known until the checkout request id
-- it quotes is matched to a transaction. Row-level security makes an unbound query see no rows,
-- so this function - which runs as the schema owner and returns ONLY a tenant id - is the one
-- door through the policy for that lookup. See the identity service's V4 for the same pattern.

CREATE OR REPLACE FUNCTION ss_payment_tenant_by_checkout(p_checkout text) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
    AS $$ SELECT tenant_id FROM mpesa_transactions WHERE checkout_request_id = p_checkout LIMIT 1 $$;

REVOKE ALL ON FUNCTION ss_payment_tenant_by_checkout(text) FROM PUBLIC;
