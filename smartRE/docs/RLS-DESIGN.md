# Database-level isolation for SmartRE: what it would take (OPEN, not built)

Status: **open.** SmartRE scopes data to its owner in application code only. Nothing in the
database stops a query that forgets its predicate. This note records what a backstop would
involve so the decision can be made with the cost in view. It is not a commitment and nothing
here has been implemented or tried.

## Where the risk is

The services that hold one party's records and serve them to that party are
`property-management-service` (units, tenants, leases, invoices, maintenance: all keyed by
`landlord_id`, plus tenant-side reads keyed by the tenant's linked `user_id`),
`verification-service` (filings keyed through the seller's identity verification),
`payment-service` (payments by buyer or seller), `viewing-service` (buyer or seller),
`review-service` (reviewer) and `notification-service` (recipient). Each enforces ownership in
Java (`owned()`, `requireOwned()`, repository finders that take the caller's id). Those checks
are consistent today. They are also the only line of defence: one new finder without the
predicate is a cross-owner read, and no test fails when it is written.

## Why this is not the same job as Soko's

Soko has one table family with one tenant column and one application role. SmartRE is eight
services, each with its own database, and the rule is not "tenant equals X":

- an invoice is visible to its landlord **and** to the tenant it is addressed to;
- a payment is visible to its buyer, its seller, and admins;
- an admin sees across owners, and a handful of internal service-to-service endpoints do too;
- Kafka consumers write rows with no HTTP caller at all.

So the policy is per table and has more than one branch. A single `tenant_id = current_setting(...)`
policy would be wrong for most of them.

## The shape of it, per service

1. **A session variable carries the caller.** The service sets `smartre.user_id` (and
   `smartre.role`) with `set_config(..., true)` when a transaction begins, from the identity the
   JWT filter already resolved. The same hook Soko uses: wrap the `DataSource` so the setting is
   applied at `setAutoCommit(false)`, since a setting applied outside a transaction is dropped.
   Hibernate and Spring Data run declared query methods outside any transaction unless the
   repository interface is `@Transactional`; Soko found this out the hard way (every read returned
   nothing until its repositories were annotated), and the same audit is needed here first.
2. **A restricted runtime role.** The service connects as a role that owns nothing and cannot
   bypass row-level security; Flyway keeps migrating as the owner. Today services connect as the
   owner (`postgres`), so row-level security would be silently skipped.
3. **A migration per service** that enables and forces RLS on each owner-scoped table with a
   policy such as `landlord_id = smartre_user() OR tenant_user_id = smartre_user() OR smartre_is_admin()`,
   a new `V<n>` file in that service (never an edit of an applied one; `scripts/check-migrations-frozen.py`
   must be updated for the new file).
4. **A named escape hatch** for the paths with no caller: Kafka consumers, the rent-invoice job and
   the outbox sweeper, run in an explicit system scope, as Soko's `asSystem` does, with the key held
   outside the database.
5. **Tests that cross the boundary.** For each service, a real-Postgres test that connects as the
   restricted role, seeds two owners, and asserts the second sees nothing through both raw SQL and
   the HTTP API. The existing mock-based tests cannot show any of this.

## Cost and order

Roughly: the base DataSource wrapper and role handling once and copied (they are small), then a
policy migration and a two-owner test per service. `property-management-service` first (most rows,
clearest owner, money), then `payment-service`, then verification. The first step is the
`@Transactional` audit and the restricted role, because without them the policies change nothing
or, worse, hide every row.

## What would change for operators

A second database credential per service (a runtime role beside the owner), a system key per
service, and a failure mode that is silent unless tested: a read outside a transaction returns an
empty list rather than an error. That last point is the argument for doing the audit and the
tests together, not the policies alone.

## Not covered here

Card payments are a separate open item and not part of this note: they need a chosen provider,
a merchant account, keys, a webhook and a stated PCI position, none of which exist (see
`GAP-ANALYSIS.md`).
