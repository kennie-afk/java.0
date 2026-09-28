-- Keyset pagination indexes.
--
-- These match the ORDER BY of the cursor queries exactly: (tenant_id, created_at DESC,
-- id DESC). An index that differs in column order or direction is not a partial win,
-- it is unused - Postgres falls back to sorting the tenant's entire history to return
-- twenty-five rows, which is the cost keyset paging exists to remove.
--
-- Forward-only. V1 is applied everywhere and must never be edited; changing an applied
-- migration changes its checksum and blocks start-up.

CREATE INDEX IF NOT EXISTS ix_commodities_keyset
    ON commodities (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_products_keyset
    ON products (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_product_variants_keyset
    ON product_variants (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_grade_standards_keyset
    ON grade_standards (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_certifications_keyset
    ON certifications (tenant_id, created_at DESC, id DESC);
