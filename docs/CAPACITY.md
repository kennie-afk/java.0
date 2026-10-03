# Capacity models

Status: PROVISIONAL. Every input below is an assumption chosen on 2026-10-03, not a measurement and not a
customer commitment. Change an input, redo the arithmetic, resize. A system "handles N" only after the load
test in the last column passes on a real multi-node cluster; until then the target is a goal, not a claim.

Method: users -> peak concurrency -> requests/s -> writes/s -> rows/year -> sizing. Peak factor 3x average,
headroom 2x on top of peak (a node loss or a surge must not be an outage).

| System | Provisional target | Peak requests/s | Peak writes/s | Dominant cost | Proof required |
|---|---|---|---|---|---|
| Mara POS | 30,000 terminals (10,000 merchants x 3) | ~1,000 | ~500 | ledger append + fiscal receipt per sale | 500 sales/s sustained 30 min, p99 < 300 ms, zero lost or duplicated sales across a pod kill |
| Soko | 1,000,000 monthly buyers | ~1,500 (read ~90%) | ~150 | catalogue reads; order + M-Pesa callback bursts | 1,500 req/s mixed, callbacks 100% processed once after a restart |
| SmartRE | 500,000 monthly visitors | ~800 (read ~95%) | ~40 | search/listing reads, images | 800 req/s, image bytes served from object store/CDN not the JVM |
| SmartSeason | 200,000 farmers, 500,000 devices @ 15 min | ~2,000 | ~600 (telemetry ~550) | telemetry ingest | 600 writes/s ingest, retention keeps tables bounded, p99 < 500 ms |
| HMS | 100-hospital group, 50,000 staff, 5M patients | ~700 | ~120 | reads: portal, FHIR, reports; big clinical tables | 700 req/s, reports on a replica never slow a ward write |
| CMS, Forecourt, Aegis, Sifa | tens to hundreds of tenants, each small | < 100 | < 20 | none | current sizing is enough; verify once |

## Arithmetic (so it can be redone)

**Mara.** 30,000 terminals; at a busy hour a till rings one sale per ~60 s -> 500 sales/s average in that
hour; peak factor makes the design point ~1,000 req/s (a sale is several calls: lease check, sale, payment,
receipt) and ~500 writes/s. Ledger rows: ~3 per sale -> 1,500 rows/s peak, ~2.4 billion rows/year at a
sustained 8h x 25% duty -> must be partitioned (tenant hash + month) and old partitions archived.

**Soko.** 1M monthly buyers, ~10 sessions/month each, ~30 requests/session -> 300M requests/month ~ 115/s
average, x3 peak ~ 350/s; campaigns/pay-day push the design point to ~1,500/s. Writes (orders, cart edits,
callbacks) ~10% -> ~150/s.

**SmartRE.** 500k visitors/month, ~20 pages each, ~15 API calls/page -> 150M/month ~ 58/s avg; viral
listings and weekend evenings push the design point to ~800/s, almost all reads.

**SmartSeason.** 500,000 devices / 900 s = ~555 telemetry writes/s steady (not peak-dependent: devices
report on a clock; a power-cut reconnect storm is the peak, design for 2x). 555/s x 86,400 = 48M rows/day
raw -> retention roll-up-then-delete is mandatory, partitions by day.

**HMS.** 50,000 staff, ~10% concurrent at day peak = 5,000 users x ~1 request/10 s = 500/s + portal and
FHIR ~200/s. Writes ~120/s (vitals, notes, dispensing, billing). 5M patients x ~12 encounters/year.

## Resize rules derived from the models

- Stateless services: replicas = ceil(peak req/s / measured req/s per replica) x 2 (headroom), minimum 2,
  PodDisruptionBudget minAvailable 1. Per-replica rate must be MEASURED (CMS measured ~110-190 req/s per
  replica on a shared 4-core box; do not reuse that number as a target, remeasure on the cluster).
- Postgres: primary sized for peak writes with a synchronous-or-async standby; a read replica per read-heavy
  surface (HMS reports/FHIR/portal, Soko catalogue, SmartRE listings); PgBouncer in transaction mode
  everywhere, pool sizes from (replicas x per-replica concurrency), not guessed.
- Partition anything that grows by rows/s: Mara ledger, SmartSeason telemetry, HMS audit/vitals/observations.
- Queues absorb bursts: Soko order intake and callbacks, Mara sync.
- Autoscaler: CPU 65-70% target; scale-down slow (a JVM service costs about a minute to start).

## Order of work

1. Mara (write-heavy, highest risk): partitioning, standby, load test.
2. SmartSeason: telemetry partitions + row-level security + reference validation, load test.
3. HMS: read replicas + partitioning, load test.
4. Soko, SmartRE: read tier, queue intake, CDN, load test.
Load tests run one system at a time on a real multi-node cluster (this laptop cannot host it: 11 GiB).
