# Mara — architecture

A multi-tenant point-of-sale platform for retail and hospitality.

In Swahili, *mara* is a time or an occasion — *mara moja*, at once. That is the unit
this system is built around: a single transaction at a single counter, which must
complete correctly whether or not anything else in the world is reachable.

---

## 1. The problem this architecture exists to solve

A point of sale sits on a fault line between two requirements that pull in opposite
directions.

**The till must always sell.** A supermarket lane that stops when the network drops is
worse than no system at all: the queue stops, the stock is already in the customer's
hands, and the shop loses the sale. Availability at the leaf is non-negotiable.

**The money and the tax record must be exactly right.** Every sale is a tax event. In
Kenya the Revenue Authority now reconciles declared income against transmitted invoice
data; in most other jurisdictions an equivalent fiscal regime applies. A sale that is
recorded twice is fraud. A sale that is recorded never is fraud. A sale whose total does
not match its payments is fraud.

Those two requirements are, formally, the two sides of CAP. Under partition the till must
stay **available**, so it cannot ask a server whether a sale is allowed. But the ledger
must be **consistent**, so the server cannot simply accept whatever the till eventually
says.

Everything below is a consequence of resolving that tension honestly rather than
pretending one side of it away.

### The resolution

> The terminal is the authority for **what happened at its own counter**.
> The server is the authority for **what everything means together**.

A till never asks permission to sell. It records what it did, signs it, and is believed —
because it can prove authorship, prove ordering, and prove nothing was removed. The
server never invents sales; it ingests, reconciles, and raises exceptions where a till's
account of itself disagrees with the rest of the world.

That gives a precise definition of correctness:

- **Availability:** a terminal with no network can open, sell, take payment, print a
  compliant receipt and close a shift, indefinitely.
- **Integrity:** the server can detect any sale that was altered, back-dated, removed or
  replayed, without trusting the terminal that sent it.
- **Convergence:** once the partition heals, the server's ledger equals the sum of the
  terminals' journals, exactly once.

---

## 2. How the terminal stays correct offline

Four mechanisms, each doing one job.

### 2.1 Identity — the terminal proves who it is

Each terminal is enrolled once, out of band, by an owner. Enrolment generates an
**Ed25519 keypair inside the terminal**; the private key never leaves it. The server
stores only the public key against a terminal record.

Transport is **mTLS**, so an unenrolled machine cannot even open a connection. But
transport security is not enough on its own: the sales it uploads must still be
attributable after they are at rest, long after the TLS session is gone. So every sale is
**signed by the terminal key** and the signature is stored with it. A row in the sales
table can be verified years later, by anyone, against a public key.

This matters because the fraud this system exists to catch is usually *inside* the shop.

### 2.2 Ordering — the terminal cannot hide a sale

Every terminal keeps a **strictly monotonic sequence number**, incremented once per
recorded sale and never reused. The sequence is part of the signed payload.

The server tracks the highest sequence seen per terminal. Sequence `n+2` arriving after
`n` means `n+1` exists and has not been delivered. That is not an error to be swallowed —
it is a **gap**, and gaps are the signature of a deleted sale. The server records the gap,
holds the terminal's reconciliation open and raises an exception for the owner.

Deleting a sale from a till's local database therefore does not remove it from the
record. It removes it from the *upload*, and the hole in the sequence names it.

### 2.3 Tamper evidence — a sale cannot be altered after the fact

The terminal's journal is a **hash chain**. Each sale stores the digest of the sale before
it:

```
digest(n) = SHA-256( digest(n-1) ‖ terminal_id ‖ sequence ‖ occurred_at
                     ‖ canonical(lines) ‖ canonical(payments) ‖ total_minor )
```

Editing any field of any historic sale changes its digest, which breaks every digest after
it. To forge one line item you must rewrite the entire tail of the journal — and you
cannot, because you would also have to re-sign each one, and the server already holds the
originals.

The chain is per-terminal, not global, precisely so that a terminal can extend it while
partitioned.

### 2.4 Fiscal numbering — a compliant invoice offline

This is the part most offline POS designs get wrong.

A tax invoice needs a number from a gapless, authority-recognised sequence. If the
terminal invents one, it may collide with another terminal's. If it waits for the server,
it cannot sell offline. Both are unacceptable.

Mara **pre-allocates blocks**. While online, each terminal leases a contiguous range of
fiscal numbers (say 5,000) from `fiscal-service`, recorded server-side as issued to that
terminal. Offline, the terminal draws from its lease. Numbers are unique by construction
because no two leases overlap, and the server knows which terminal owns which range before
it ever hears about the sale.

A lease has a **low-water mark**: at 20% remaining the terminal renews eagerly, so a shop
that is online for ten minutes a day never runs dry. If a lease is exhausted while
offline, the terminal keeps selling and marks the invoices `FISCAL_PENDING` — it stops
being able to hand the customer a fully numbered tax invoice, but it never stops being
able to take money. Degradation is graded, not binary.

Unused numbers in a lease are **voided on return**, not recycled, because a reissued
fiscal number is worse than a missing one.

### 2.5 What is deliberately *not* guaranteed offline

Stock is eventually consistent, and the design says so out loud rather than implying
otherwise. Two tills partitioned from each other can both sell the last unit. Pretending
otherwise would require distributed consensus in the checkout path, which would break
requirement one.

Instead: the terminal decrements a local cache to keep the cashier informed, the server
reconciles on sync, and a resulting negative on-hand raises an **oversell exception** with
both sales attached. A supermarket resolves that commercially — refund, substitute,
backorder. It is a business event, not a crash.

---

## 3. Service boundaries

Four deployables, not eleven. Each one exists for a reason that can be stated in a
sentence; anything that could not justify its own network hop stays in the core.

| Deployable | Owns | Why it is separate |
| --- | --- | --- |
| `api-gateway` | nothing | mTLS termination, JWT pre-verification, tenant claim signing, rate limiting. Fails closed. |
| `identity-service` | tenants, branches, staff, roles, terminals, enrolment | The trust root. Compromise here is total, so it holds nothing else and is deployed with its own credentials and its own database. |
| `core-service` | catalog, inventory, tabs, sales, payments, ledger, fiscal leases, hospitality | The transactional heart. See below. |
| `sync-service` | terminal cursors, chain heads, sequence gaps | Terminal fan-in. Append-only, idempotent, and scales on a completely different axis from anything a human touches. |

### Why the core is one service and not seven

A point of sale has one invariant that dominates every other design consideration: **a
sale, its tenders and its ledger postings must all be true together, or none of them
must be.** A sale recorded without its payment is theft. A payment recorded without its
posting is an unbalanced book. A ledger posting without its sale is fabrication.

Splitting sales, payments and ledger across service boundaries means that invariant can
no longer be a database transaction. It becomes a saga — a sequence of local commits
with compensating actions — and for a few hundred milliseconds the system is in a state
where the money is real and the book does not balance. That window is defensible when
the alternative is a distributed lock across a slow third party. It is not defensible
when the alternative is `BEGIN … COMMIT` on one Postgres, which is what this actually
is.

So catalog, inventory, sales, payments, ledger, fiscal and hospitality are **modules
inside one deployable**, with the same discipline microservices would impose:

- each module owns its own schema, and no module reads another's tables;
- modules talk through published interfaces, never through each other's repositories;
- cross-module reads go through a port, so extracting a module later is a deployment
  change and not a rewrite;
- the module boundaries are enforced at build time, not by convention.

The result is that the boundaries are real — the same boundaries a reviewer would draw —
without paying network latency, partial failure and eventual consistency for the
privilege of drawing them in YAML.

### Why these three *are* extracted

Each has a property the core does not:

**`api-gateway`** terminates mTLS and is the only component addressable from outside.
Its blast radius and its scaling shape are both different, and it holds no data at all.

**`identity-service`** is the trust root. It holds terminal public keys and staff
credentials, and it is the one component whose compromise is unrecoverable. It gets its
own database, its own credentials, and a deployment that can be locked down harder than
anything serving a checkout.

**`sync-service`** absorbs terminal fan-in. Every till in every shop uploads to it, on a
schedule nobody controls, in bursts when connectivity returns to a whole town at once.
It is append-only and idempotent, so it scales horizontally without coordination — and
because it verifies signatures and chains before anything reaches the core, a
malformed or hostile upload never touches the transactional store.

### What would justify extracting more later

Not "the diagram would look better". Concretely:

- **`fiscal-service`**, once a second jurisdiction is supported and it needs its own
  retry, rate limiting and outage behaviour against a tax authority that is down.
- **`reporting-service`**, once year-to-date queries measurably slow a checkout — at
  which point it becomes a CQRS read side fed by events, not a split of the write path.
- **`catalog-service`**, if a tenant's catalogue grows large enough that its cache
  behaviour genuinely conflicts with the transactional store.

Each of those is a threshold, not a preference. Until one is crossed, the module stays
in the core.

### Why retail and hospitality are one system

A supermarket sale and a hotel bill are the same object observed over different
durations. Both are **tabs**: an identified container that accretes charges and closes
against tenders. A checkout is a tab that opens and closes in one act. A hotel folio is
a tab open for four days that accretes a restaurant charge on night two.

So the core models a tab, and hospitality adds table/room association, transfer and
split — rather than forking a second product that reimplements pricing, tax and payment.

## 4. Money

Money is `BIGINT` **minor units** with an explicit ISO-4217 currency. Never floating
point. No exceptions.

Every sale posts to a **double-entry ledger**. Debits equal credits per transaction, and
the invariant is asserted in the database, not merely in code:

```
sum(debit_minor) - sum(credit_minor) = 0   -- per transaction, enforced by trigger
```

Postings are **append-only**: no `UPDATE`, no `DELETE`, revoked by contra-entry. A refund
is not a deleted sale; it is a new transaction that reverses one, and both remain visible.

Idempotency: every externally triggered mutation carries a client-supplied key, stored
with a uniqueness constraint. A retried M-Pesa callback finds the key and returns the
original result rather than posting twice.

---

## 5. Security posture

Defence in depth, on the assumption that any single layer will eventually fail.

**Tenant isolation is enforced below the application.** Every tenant-scoped table carries
`tenant_id` and has PostgreSQL **row-level security** enabled. The application connects as
a role that cannot bypass RLS; migrations run as a different, owning role. A forgotten
`WHERE tenant_id = ?` in a query therefore returns nothing rather than another shop's
takings. The application-layer filter is the first line; RLS is the line that holds when
the first one is wrong.

**The gateway fails closed.** If Redis is unreachable the gateway cannot check token
revocation, so it rejects rather than admits. An outage degrades availability, never
authorisation.

**Secrets have no fallback defaults.** No `getOrDefault("dev-secret")`. A service with an
unset signing key refuses to start, because a defaulted key that silently works in
production is worse than a service that visibly does not.

**Cashiers are individually identified.** PIN plus terminal binding, short-lived session,
no shared logins — otherwise "who voided this" has no answer, and voids are where theft
lives. Voids, refunds, price overrides and no-sale drawer opens require elevated
authorisation, carry a mandatory reason, and are written to an append-only audit log.

**Terminals are credentials, not addresses.** mTLS plus per-sale Ed25519 signatures, as in
§2.1. Knowing the endpoint gets an attacker nothing.

**PII is minimised.** A sale needs a buyer tax PIN only when the buyer wants to claim
input VAT. Customer identifiers are stored encrypted at field level, and the fiscal
adapter transmits the minimum the regime requires.

---

## 6. Scale

Design targets, with the reasoning rather than round numbers:

- **Write path.** A 40-lane hypermarket at peak is ~40 sales/minute/lane. The write-hot
  path is `sales-service`; it is partitioned by `tenant_id` and its tables are range
  partitioned monthly, because sales and postings are the two tables that grow without
  bound.
- **Read path.** Catalog is read-thousands-to-one against writes, so it is cached at the
  terminal and fronted by Redis. Reporting is a separate read model fed by events, so an
  owner running a year-to-date report cannot slow a checkout.
- **Terminal fan-in.** Sync is append-only and idempotent, so it scales horizontally;
  ingest is the only thing `sync-service` does, and it is deliberately the simplest
  service in the system.
- **Connection pressure.** PgBouncer in front of every database, because thousands of
  service instances against a few Postgres primaries exhausts connections long before it
  exhausts CPU.

---

## 7. What is deliberately excluded

- **Distributed transactions across services.** There is only one transactional service,
  so the question does not arise inside the core. Where the core must coordinate with an
  outside party — a payment rail, a tax authority — it uses an outbox and idempotent
  retry, not a two-phase commit.
- **Consensus in the checkout path.** See §2.5.
- **A generic plugin system.** Verticals are modules compiled against the core, so a
  hospitality bug cannot take down a supermarket.
- **Blockchain anything.** The hash chain in §2.3 provides tamper evidence. Distributed
  consensus over it would add latency to solve a problem no participant has.

---

## 8. Build status (2026-10-02)

What exists, stated plainly, because this document describes a target and the target is
larger than the code.

| Piece | State |
| --- | --- |
| `platform` (money, journal, fiscal lease, identity, staff policy, sale canonical form and checks, request signatures) | Built, 112 tests (adds the fixed-window limiter). |
| `service-kit` (two-role database wiring with tenant-bound RLS, terminal request authentication, operator tokens) | Built. Shared by `sync-service` and `core-service`. 4 unit tests (terminal rate limit); the rest is exercised through theirs. |
| `identity-service` (tenants, terminals, enrolment, staff PIN sign-in, provisioning, internal terminal lookup, RLS) | Built, runs from an empty volume. 31 tests against a live PostgreSQL. |
| `sync-service` (terminal fan-in, §2.2 and §2.3 on the server) | **Built 2026-10-02.** Verifies every uploaded entry, keeps the append-only second copy, raises exceptions. 9 tests against a live PostgreSQL. |
| `core-service` (ledger, fiscal leases, ingested sales; §2.4 and §4) | **Built 2026-10-02**, scoped to what the till needs: the double-entry ledger, fiscal number leasing and the posting of verified sales. Catalog, inventory, tabs and hospitality are **not** built. 18 tests against a live PostgreSQL, including a 40-round race of a sale against a lease return. |
| `apps/terminal` (the till, a browser PWA) | Built; uploads its journal and leases fiscal numbers. 85 vitest tests. |
| Rate limiting | **Built in each service, not in a gateway.** A per-source-address fixed window (per process, so N replicas allow N times the cap): enrolment 20/min, staff sign-in 120/min, `/v1/terminal/**` 1200/min, applied before authentication so a refused flood never costs a signature check or a database call. Keyed on the socket address, never `X-Forwarded-For`. 429 with `Retry-After`. Tunable with `MARA_RATELIMIT_*`. |
| `api-gateway` | **Not built, and not needed by the till yet.** See "What is deliberately not built". |
| `apps/platform` (back office) | **Not built.** An empty shell. Back-office reads are operator-token endpoints (`/v1/admin/*`). |

### The terminal, and what it does and does not prove

`apps/terminal` implements §2.1 to §2.4 from the terminal's side of the line:

- **Identity (§2.1).** Enrolment posts to the real `POST /v1/enrolment` through a
  same-origin route handler. The Ed25519 key pair is generated in the browser with
  WebCrypto, the private key non-extractable and persisted as a `CryptoKey` in
  IndexedDB. Every journal entry's chain digest is signed with it, and so is every request
  the till makes to a server.
- **Ordering and tamper evidence (§2.2, §2.3).** A local journal in IndexedDB: strictly
  monotonic sequence from 1, each entry chained onto the last with the platform's
  `ChainDigest` encoding, appended in a single transaction together with the head record,
  the fiscal lease and the closing of the tab. The **Verify** screen reproduces
  `JournalVerifier` / `ChainVerdict` semantics over the whole chain in pages and reports
  findings (intact, broken-at, gap-at), never one boolean.
- **Money (§4).** `Money.allocate` and basis-point `percentage` ported with BigInt.
- **Fiscal (§2.4).** The till leases a block of fiscal numbers from `core-service`, draws
  from it offline, renews at 20% remaining, installs the new lease before handing the old
  tail back (a sale never waits on the network), and falls back to `FISCAL_PENDING` only
  when it has never been online or the lease is exhausted or expired. Nothing invents a
  number.
- **Sync.** A background agent uploads the journal (on load, on reconnect, every 30
  seconds). Its cursor moves only to what the server confirms it holds verified, never past
  what was sent. None of this is on the selling path: every failure is recorded and retried.

Compatibility with the Java code is not asserted, it is tested, in both directions.
Java to TypeScript: the TypeScript digests, allocations, roundings, verifier verdicts and
lease draws are compared with vectors produced by running the platform's own classes
(`apps/terminal/vectors/Vectors.java`, regenerated with `vectors/generate.sh`).
TypeScript to Java: `apps/terminal/test/ts-journal-fixture.test.ts` has the terminal's own
code enrol a key, sell four sales (v1 and v2 bodies, quotes, backslashes, tabs, newlines, a
control character, accented and CJK text and an emoji in item names) and write the signed
journal, plus a signed request, to `test/fixtures/ts-journal.json`; the platform's
`SaleEntryVerifierTest` recomputes every body digest and chain digest in Java and verifies
every Ed25519 signature. The sale body encoding is the terminal's contract
(`SaleCanonical` is its Java reproduction, including `JSON.stringify`'s string escaping).

### The server side, as built

**`sync-service` (own database `mara_sync`).** `POST /v1/terminal/sync/journal` takes up
to 500 entries. Three layers, none trusting a digest the terminal supplied:

1. *Per entry:* the body digest is recomputed from the sale, the chain digest from that,
   and the signature is verified against the key identity-service holds for that terminal.
   Failures (`BAD_BODY_DIGEST`, `BAD_DIGEST`, `BAD_SIGNATURE`, `MALFORMED`, `FOREIGN_ENTRY`)
   raise an exception row and the entry is not stored.
2. *Per chain:* the platform's `JournalVerifier` against the stored head: a missing
   sequence is a `GAP` (reconciliation is held open at the last verified entry and the
   response says where), a sequence already held with different content is a
   `FORKED_SEQUENCE` (the first copy stands), plus broken links, a backwards clock and a
   restarted genesis. A gap closes by itself when the missing entries arrive.
3. *Per sale:* the arithmetic the till claims (line net, per-line half-up tax, total,
   tenders settle the total) is rechecked. A sale that fails only this is stored and flagged
   `SALE_INCONSISTENT`, because the till is authoritative about what happened at its counter.

Entries live in `journal_entry`, append-only (a trigger refuses UPDATE, DELETE and TRUNCATE
even for the owning role). The head row is locked per terminal for the whole upload, so a
retry racing its original is serialised; retries are idempotent.

**`core-service` (own database `mara_core`).** Pulls verified entries from `sync-service`
per terminal, in order, with its own cursor, and posts each atomically: the `sale` row, its
ledger transaction and the cursor advance commit together. A pull rather than a push, so
the till's upload never waits on the core.

- *Ledger.* Accounts `CASH`, `MOBILE_MONEY`, `SALES`, `VAT_PAYABLE`, `SUSPENSE`. A sale
  debits each tender account the amount applied and credits `SALES` the net and
  `VAT_PAYABLE` the tax. Postings are BIGINT minor units, append-only (triggers), one side
  each and never negative. **Debits equal credits per transaction is a deferred constraint
  trigger in the database**, not only a check in code; so is "at least two postings".
  A sale whose tenders do not equal its net plus tax is posted anyway with the difference
  in `SUSPENSE` and a `SALE_UNBALANCED` exception, so the books still balance and the
  discrepancy is visible. A terminal sequence is posted at most once (a cursor and a unique
  index both say so).
- *Fiscal leases (§2.4).* One counter per tenant, advanced under a row lock, so blocks
  cannot overlap; a trigger asserts disjointness independently. A terminal may hold two live
  leases at most. Unused numbers are voided on return (`fiscal_void`), never recycled; a
  return is refused if sales already ingested used numbers in the range. When a sale
  arrives, a fiscal number counts only if it lies in a lease issued to that terminal, is not
  voided and is not already held by another sale; otherwise the sale is still posted and a
  `FISCAL_OUT_OF_LEASE` or `FISCAL_DUPLICATE` exception is raised.

**Authentication.** Terminal requests carry `X-Mara-Terminal`, `X-Mara-Timestamp` and an
Ed25519 signature over `mara.request.v1|terminal|epoch|METHOD|path?query|sha256(body)`,
verified against the key and status identity-service holds (cached 30 s, so a suspended
terminal stops being believed within the window; an unreachable identity-service refuses,
never admits). The tenant comes from identity-service's record of the terminal, never from
the request. Every failure answers the same 401. Service-to-service calls use
`MARA_INTERNAL_TOKEN` and the operator's reads use `MARA_ADMIN_TOKEN` plus `X-Mara-Tenant`;
neither has a default, a service refuses to start without a 24-character value, and the two
are different credentials on purpose.

**Tenant isolation** is enforced in each database exactly as in identity-service: two
roles, `FORCE ROW LEVEL SECURITY`, the tenant bound when the transaction begins. Both new
services have a test that reads another tenant's books and gets nothing, and one that writes
to another tenant's rows as the application role and is refused by the database.

### What has been verified, and how

- 103 platform, 27 identity, 9 sync and 17 core Java tests pass with 0 failures and 0 skipped
  (the database-backed ones run against a real PostgreSQL 16; without one they skip, they do
  not pass). 85 terminal vitest tests pass; 5 more drive a live stack and skip without one.
- On 2026-10-02 the whole stack was built into images and brought up from a fresh volume with
  `docker compose up`; the till's own libraries then enrolled against the real identity-service,
  signed staff in, sold, leased fiscal numbers from core-service and uploaded through the real
  proxy routes. Read back through the operator endpoints: sync-service held the till's chain
  with the same head digest, no exceptions, the first numbered sale holds fiscal number 1, and
  the trial balance's debits equalled its credits (4 sales of KES 185.60: cash 556.80, mobile
  money 185.60, sales 640.00, VAT 102.40).
- **Not run live:** killing core-service mid-stream and watching it catch up (the poller's
  idempotency and per-terminal cursors are tested, the outage is not); a browser walk of the
  Journal page's server-copy card (typechecked and built, not looked at); load.

### What is deliberately not built

- **`api-gateway`.** The till talks to `identity-service`, `sync-service` and `core-service`
  through its own same-origin proxy routes, each mapped to one fixed upstream path and
  forwarding the signed request unchanged. Nothing the till does needs a gateway yet; what a
  gateway adds is mTLS termination, rate limiting and *staff* tokens for a back-office UI,
  and there is no back-office UI to issue them to.
- **Catalog, inventory, tabs, hospitality in `core-service`.** The till keeps its own
  catalogue and tabs; stock is not tracked anywhere (so §2.5's oversell exception does not
  exist yet).
- **A back office.** Reads are operator-token JSON endpoints. No per-user server login.
- **Monthly range partitioning** of `journal_entry`, `sale` and `posting` (§6). The tables
  are keyed so partitioning is a migration, not a redesign; it is not done.
- **Resolving a held gap.** A real deleted sale leaves its terminal's later sales held
  behind the gap on the server (they remain safe, signed, on the till). There is no operator
  workflow yet to acknowledge a loss and release them.
- **Exhaustive failure injection under load.** Concurrency is tested (20 simultaneous lease
  requests, 8 simultaneous posts of one sale), not benchmarked.

**Honest limits of the till.** Clearing the site's data destroys any sale not yet uploaded.
The clock is the device's; a backwards clock is clamped, not refused, so the till can
always sell. Until the server has the tail, a person with full control of the browser
profile can still rewrite the not-yet-uploaded tail and its head record together; once
uploaded, the server's copy makes that detectable.
