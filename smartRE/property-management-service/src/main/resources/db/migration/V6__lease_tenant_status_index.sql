-- A tenant's active lease is looked up on every maintenance request they raise.
--
-- Until now that path loaded every ACTIVE lease on the platform and filtered in the
-- JVM. The code now asks for one tenant's lease directly; idx_leases_tenant already
-- serves it, and this composite lets Postgres satisfy the status predicate from the
-- index too rather than rechecking each matching row against the heap.
--
-- Forward-only: V1 is applied everywhere and must never be edited. Changing an applied
-- migration changes its checksum and blocks start-up.
CREATE INDEX IF NOT EXISTS idx_leases_tenant_status ON leases (tenant_id, status);
