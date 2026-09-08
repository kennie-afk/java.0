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
