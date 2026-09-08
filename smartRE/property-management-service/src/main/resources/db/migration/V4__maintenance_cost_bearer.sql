-- Who pays for a repair.
--
-- The cost was already recorded; who bears it never was. That is the part landlords and
-- tenants actually dispute, and in Kenyan practice the split is real: structural and
-- fair-wear repairs fall to the landlord, damage caused by the tenant is recharged, and
-- some jobs are shared. A cost with no attributed payer cannot settle any of those
-- conversations, and cannot feed a deposit deduction later.
--
-- Nullable on purpose. Existing resolved jobs genuinely do not have an answer, and
-- back-filling them with a guess would invent a decision nobody made.

ALTER TABLE maintenance_requests
    ADD COLUMN cost_borne_by VARCHAR(16),
    ADD COLUMN tenant_charge NUMERIC(12,2);

ALTER TABLE maintenance_requests
    ADD CONSTRAINT chk_maintenance_cost_borne_by
        CHECK (cost_borne_by IS NULL OR cost_borne_by IN ('LANDLORD', 'TENANT', 'SHARED'));

-- A tenant charge only means something when the tenant is bearing some of it, and a
-- shared job without a split is an unfinished decision rather than a valid state.
ALTER TABLE maintenance_requests
    ADD CONSTRAINT chk_maintenance_tenant_charge
        CHECK (
            (cost_borne_by IS NULL      AND tenant_charge IS NULL)
         OR (cost_borne_by = 'LANDLORD' AND tenant_charge IS NULL)
         OR (cost_borne_by = 'TENANT')
         OR (cost_borne_by = 'SHARED'   AND tenant_charge IS NOT NULL)
        );

COMMENT ON COLUMN maintenance_requests.cost_borne_by IS
    'Who bears the repair cost. NULL until the job is resolved and a decision is taken.';
COMMENT ON COLUMN maintenance_requests.tenant_charge IS
    'Portion recharged to the tenant. Required for SHARED, forbidden for LANDLORD.';
