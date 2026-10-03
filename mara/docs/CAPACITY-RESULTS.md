# Mara: measured capacity results

Everything here was run on 2026-10-03 on one HP ProBook (4 cores, 11 GiB RAM, spinning-class Docker
overlay disk), with Postgres 16 in a container at its defaults (`shared_buffers` 128 MB,
`synchronous_commit` on, fsync on) and other containers running alongside. These are floor numbers
for a laptop, not a statement about a production cluster, and each section says what it did NOT prove.
Targets come from `java.0/docs/CAPACITY.md` (30,000 terminals, ~1,000 requests/s, ~500 writes/s).

## 1. Hash partitioning of the ledger, postings, sales and the server journal

Dataset: 1.5M sales, 1.5M ledger transactions, 4.5M postings, 300 tenants (skewed, largest 87,000
sales), 3,000 terminals. Same data in the flat V1/V2 schema and in the partitioned schema
(`core-service/src/main/resources/db/optional/partition-ledger-and-sales.sql`, 16 hash partitions on
`tenant_id`). Queries run as `mara_app` with the tenant bound, so row-level security applies.

| Query (best of 3, warm) | flat | partitioned | partitions touched |
|---|---|---|---|
| sale by (terminal, sequence) (ingest idempotency) | 0.12 ms | 0.28 ms | 1 |
| fiscal number in use? | 0.33 ms | 0.33 ms | 1 |
| postings of one transaction | 0.19 ms | 0.33 ms | 1 |
| trial balance, typical tenant (5,000 sales) | 6.6 ms | 12.3 ms | 1 |
| trial balance, largest tenant (87,000 sales) | 154 ms | 190 ms | 1 |
| 100 latest sales for a tenant | 0.35 ms | 0.44 ms | 1 |

Write path (pgbench: one transaction = ledger transaction + 3 postings + sale, balance trigger live,
random tenant, 8 clients, 25-30 s): flat 205-279 tps, partitioned 111-175 tps. The runs were noisy
(the disk is shared); the honest summary is **partitioning cost roughly 15-35% of write throughput
here and bought no read speed at 7.5M rows**. Pruning works (one partition per tenant-scoped
statement), so the cost is routing and per-partition index and trigger overhead.

Migration of the 7.5M existing rows into partitions: 3 min 31 s (superuser, one transaction).

**Decision:** partitioning is an OPT-IN script, not a Flyway migration. Its case is operational
(vacuum and index size, moving or archiving a partition) and starts at hundreds of millions of rows;
a first customer should not pay a write-throughput cost for it. Tested on existing data as a
non-superuser owner (`PartitionMigrationTest` in core and sync). A finding from that test, and the
reason the script is not just `INSERT ... SELECT` then `DROP`: a non-superuser table owner is subject
to FORCE ROW LEVEL SECURITY and sees ZERO rows of its own tables, so a naive copy-and-drop silently
loses all data. The script un-forces the old tables (and `account`, which the new foreign key
validates against) first. The tests only ever ran as a superuser before this was written, which hid it.

Not proven: behaviour beyond 7.5M rows (where the benefit is expected, not measured); the effect of
a larger `shared_buffers` or an SSD on the write comparison; a tenant that dominates one partition.
Observation, unrelated to partitioning: the trial balance for the largest tenant takes 154 ms
because it aggregates every posting; at that size it needs a running-balance table, not an index.
