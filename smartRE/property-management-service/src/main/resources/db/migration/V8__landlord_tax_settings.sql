-- Monthly rental income tax (MRI) readiness. The statutory rate is NOT hard-coded as a fact: sources checked
-- on 2026-10-03 disagree (7.5% vs 10%), so the platform default comes from configuration (pms.mri.rate-percent)
-- and a landlord, or their accountant, can override it here with the rate they have confirmed with KRA.
CREATE TABLE landlord_tax_settings (
    landlord_id       UUID PRIMARY KEY,
    mri_rate_percent  NUMERIC(5,2) CHECK (mri_rate_percent IS NULL OR (mri_rate_percent >= 0 AND mri_rate_percent <= 100)),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);
