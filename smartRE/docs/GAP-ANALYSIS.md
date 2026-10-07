# SmartRE — end-to-end gap analysis

Compiled 2026-09-07 against the running system. Every figure here came from executing
something — test runners, live HTTP calls, `docker ps`, the database — not from reading
code and inferring. Where something is unverified, it says so.

---

## 1. Verdict

The system **works**. All 9 services build, all 10 modules pass, and a full journey —
register → list a property → add units → house tenants → raise a repair → drive it
through a state machine — runs end to end through the gateway against real Postgres and
Kafka. That was demonstrated, not assumed.

It is **not production ready**, for a small number of specific reasons set out below.
None of them is architectural; all are finishable.

---

## 0. Update, 2026-10-06

A second pass, each item checked in code before it was changed. What follows in sections 1-8 is the
2026-09-07 text; where it is stale the entries below win.

**Closed**

- **Unbounded lists.** Units on a property, a unit's lease history, an invoice's payments, a seller's
  own ownership filings and the raw M-Pesa callbacks are now bounded pages (default 200, max 200;
  filings default 100). The body is still a plain array so existing callers keep working; the total and
  whether more follow travel in `X-Total-Count` / `X-Has-More`, which the gateway now exposes to the
  browser. The admin ownership queue caps its page at 500 and whitelists its sort columns (it used to
  accept any size and any property name). The raw-callbacks route paged only its "latest" form to one
  page of at most 500; both forms now page. Units on a property are filtered to the landlord in SQL;
  they were loaded for every landlord and filtered in Java.
- **Backend routes with no UI.** `PUT /api/properties/{id}/publish` (a Publish button beside "Managed
  only" properties in the portfolio), `PUT /api/viewings/{id}/no-show` ("Report no-show" on a confirmed
  viewing whose time has passed, buyer or seller) and `POST /api/verification/ownership/{id}/documents/bulk`
  (a "we will sort it" folder upload on the ownership page that reports what was filed, what needs the
  seller, and what is still missing) are in `lib/api.ts` and on screen. Not a defect: there is no
  `/api/documents` list endpoint (only upload and file download), so there was nothing to call. An admin
  cannot report a no-show from the UI because no admin viewings list exists; the endpoint accepts it.
- **Thin tests where trust lives.** property-service 49 to 78 (controller contract, who may see a hidden
  listing on the database and cached paths, publish rules, and a real-Postgres test of the native search
  query: filters, totals, ordering, paging through rows that tie on the sort key). review-service 24 to
  45 (hide and its audit, cache eviction and statistics; the routes behind the real security config, where
  a role header without the gateway's signature is not believed). The search test found that
  `bedrooms DESC` put listings with no bedroom count (land) first; fixed with `NULLS LAST`.
- **Providers that silently do nothing in production.** `ProductionGuard` in notification-service
  (SMS, mail), verification-service (Smile Identity, Ardhisasa, document analysis) and payment-service
  (mock mode, placeholder keys, sandbox shortcode and URLs, placeholder callback URL) refuses to start
  when `SMARTRE_ENVIRONMENT=production` and a provider is unset or a placeholder. A provider that is
  deliberately off must be acknowledged with `SMARTRE_ALLOW_UNCONFIGURED_PROVIDERS=true`, which is logged
  on every start. The k8s manifests could not supply the Smile and Ardhisasa keys at all; they now can.
  The environment defaults to development in every committed config, so nothing changes until it is set.
- **Committed Secret manifest.** `k8s/secret.yaml` (placeholder values shaped like real keys) is replaced
  by `k8s/secret.example.yaml` plus `scripts/k8s-secret.sh`, which generates random values for what the
  platform owns and takes provider credentials from the environment. The generated file is git-ignored,
  a `kustomization.yaml` keeps `kubectl apply` from applying the example over the real Secret, and
  `scripts/check-k8s-env.py` fails if the generator and the example list different keys.
- **`ddl-auto: update`** was in `application-local.yaml` of six services, not four (payment, property,
  review, user, verification, viewing). Each now says it is development-only and carries a
  `LocalProfileGuard` that refuses to start with the local profile when `SMARTRE_ENVIRONMENT=production`.
  The default profiles all use `validate`; a test asserts it.

**Still open**

- **Row-level security.** Not attempted. `docs/RLS-DESIGN.md` records what it would take and why it is
  not Soko's job copied over. Ownership checks remain application code only.
- **Card payments.** Unchanged: needs a provider chosen, a merchant account, keys, a webhook and a stated
  PCI position. Nothing here can supply those.
- **Object storage** for uploaded documents (section 8, item 9) is still the open decision.
- A stray empty directory `smartRE/smartRE/` (three empty subfolders, untracked, unreferenced) should be
  removed by hand; the removal was blocked in the environment that did this pass.

---

## 2. Test coverage — the largest single gap

Executed, not counted from `@Test` annotations:

| Service | Source files | Test files | Tests | Assessment |
| --- | ---: | ---: | ---: | --- |
| api-gateway | 7 | 4 | 20 | adequate |
| user-service | 52 | 6 | 77 | good |
| property-service | 39 | 4 | 78 | controller, visibility and Postgres search covered (2026-10-06) |
| property-management-service | 81 | 6 | 90 | good |
| verification-service | 85 | 8 | 87 | good |
| payment-service | 65 | 4 | 52 | thin for money |
| notification-service | 46 | 5 | 24 | thin |
| viewing-service | 30 | 1 | **18** | fixed 2026-09-07 |
| review-service | 23 | 3 | 45 | moderation and security covered (2026-10-06) |

**Resolved.** Both services had no test of any kind — 53 source files between them, on
the two paths where a buyer meets a stranger and where trust is published. They now carry
32 tests between them, written adversarially: refusing an unverified seller, refusing a
seller who does not own the property, translating a database double-booking into a
conflict rather than a 500, refusing completion before the appointment has happened, and
on the review side, refusing a payment that belongs to another buyer or another property.

`payment-service` at 52 tests over 65 files is the second concern, because it is the one
service where a defect costs money rather than time.

**Recommendation.** Bring viewing and review to parity before anything else. Nothing else
in this document is as likely to cause a production incident.

---

## 3. Security

### Sound

- **Every service authenticates independently.** All 9 carry `JwtAuthenticationFilter` —
  the gateway is a convenience, not the only lock. A service reached directly still
  refuses an unauthenticated call.
- **The gateway fails closed.** Redis unreachable means tokens cannot be checked for
  revocation, so requests are rejected rather than admitted.
- **Internal endpoints are secret-gated** and unreachable from outside the docker
  network. `resolve_enrolment`-style cross-boundary reads take a hash, never a plaintext,
  and return the minimum.
- **Payment callbacks are idempotent.** A replayed M-Pesa confirmation cannot post twice.
- **No TODO, FIXME or HACK anywhere in main source.** Unusual, and worth saying.

### Gaps

1. **Rate limiting exists only at the gateway.** Seven services have none of their own.
   Anything that reaches a service directly — a misconfigured ingress, a compromised
   sidecar, a future internal caller — is unthrottled. The gateway is a single point of
   both enforcement and failure.

2. ~~Placeholder secrets that start successfully.~~ **Withdrawn — this was wrong.**
   An initial grep over `*.y*ml` found `:placeholder` and `:postgres` defaults and
   reported them without checking which file each came from. On inspection:
   the third-party keys sit behind explicit `smile-identity-enabled: false` and
   `ardhisasa-enabled: false` flags, so a placeholder is only ever reached when the
   integration is deliberately off; and the `:postgres` database defaults exist only in
   `application-local.yaml`, the development profile. Production `application.yaml`
   already uses `${SPRING_DATASOURCE_PASSWORD}` with no fallback, and `JWT_SECRET`,
   `INTERNAL_SECRET` and `GATEWAY_SIGNING_SECRET` all use `:?`. **Secret handling is
   correct as it stands.**

3. **No row-level security.** Tenant isolation is enforced in application code only. A
   query that forgets its `landlordId` predicate returns another landlord's data. The
   ownership checks (`owned()`, `requireOwned()`) are consistent and correct today —
   they are simply the only line of defence.

---

## 4. Data and event integrity

- ~~**Outbox pattern exists in payment-service only.**~~ **Closed.** All four services
  now publish through a transactional outbox. The row is written inside the caller's
  transaction and the Kafka send is deferred to after commit, so a rollback takes the
  event with it and a failed send leaves a durable row for a sweeper to retry every 15s
  (alerting after 10 attempts). The three new tables carry an explicit `message_key`
  where the payment outbox did not, because their partition keys are not derivable from
  the payload alone — `RENT_OVERDUE` keys on invoice *and* day count, `MAINTENANCE_RESOLVED`
  on request *and* status, and ownership events key on property while identity events key
  on seller. A retry that recomputed the key would land in a different partition.
  Delivery is at-least-once, so consumers must tolerate a repeat.
- **Dead-letter handling is present in all 8 event-carrying services.** A poisoned
  message will not block a partition.
- **Nothing is hard-deleted** in maintenance or tenancy. Status transitions preserve
  history, which is correct for anything that may be disputed.

---

## 5. Functional gaps found by using the system

| Gap | Status |
| --- | --- |
| Landlords could not create a property — UI guarded on `SELLER_ROLES` while the backend allowed `LANDLORD` | **fixed** |
| A landlord was forced through a marketplace listing to reach management tools | **fixed** — `UNLISTED` status, `manageOnly`, and a `publish` transition |
| `linkUser` accepted an arbitrary user id, letting a landlord attach any account to a tenancy | **fixed** — resolved server-side from the recorded email |
| Repair cost recorded with no payer | **fixed** — `LANDLORD`/`TENANT`/`SHARED` with DB `CHECK` constraints |
| No search or filtering on units or repairs | **fixed** |
| Tenant detail, maintenance detail, per-property rollups not viewable | **fixed** |
| ~~Cost-bearer choice absent from the resolve modal~~ | **Wired.** The resolve modal asks who pays once a cost is entered, and only asks for a split figure when the answer is SHARED — the server derives it for the other two. The detail panel says who was charged, not just how much. |
| ~~Tenant account linking has no UI~~ | **Wired.** Link and detach sit in the tenant panel. The client sends no user id: the server resolves the account from the email already on the record, which is what stops a landlord attaching an account they merely know the id of. Where there is no email, the panel says so instead of offering a button that can only fail. |
| ~~No printable receipt~~ | **Built.** `RentReceipt` renders from server data only, lists only CONFIRMED payments (a pending STK push is not money received), and prints through the browser via a print stylesheet. Reachable from the landlord's invoice list and the tenant's own view. |
| **Card payments** | Still M-Pesa only, and this one is not a wiring job. It needs a provider chosen (Flutterwave, Paystack, Stripe), a merchant account, keys, a webhook route and a PCI position. Building it speculatively would mean guessing all five. |
| **Auto-collection** | invoices are raised automatically; the tenant still approves each payment |

---

## 6. Operational readiness

**Present:** 29 healthchecks, full observability (Prometheus, Grafana, Loki, Promtail,
Alertmanager), PgBouncer in front of every database, a backup script, and k6 load tests
for search and rate limiting.

**Missing:**

1. ~~No resource limits on any of 53 containers.~~ **Fixed 2026-09-07** — `mem_limit`
   and `mem_reservation` added to 34 services: 512m–1g for the Java services, 512m per
   database, 96m per PgBouncer, 1g for Kafka. Previously one leaking service could take
   the host, which is exactly what happened during development when a build was killed
   under memory pressure.
2. ~~**k8s manifests cover 7 of 9 services.**~~ **Closed.** All nine have manifests, and
   so does the front end (`k8s/web.yaml`), which had none at all. Wiring the manifests
   surfaced a larger problem: eight of the nine never set `INTERNAL_SECRET` or
   `GATEWAY_SIGNING_SECRET`, both of which the services read with no default, so they
   would have crash-looped on a real cluster. `scripts/check-k8s-env.py` now proves this
   class of gap cannot recur.
3. ~~**Flyway checksum drift.**~~ **Closed, and it was wider than four files.** Thirteen
   already-applied migrations across six services had their comments stripped — 64
   deleted lines, no SQL changed. The fix is to restore the files from version control,
   not to patch `flyway_schema_history` (an earlier repair went the wrong way and had to
   be reversed). `scripts/check-migrations-frozen.py` fails the build on any edit to a
   recorded migration; `scripts/flyway-checksums.py` compares files against live
   databases.
4. **Load tests cover 2 endpoints.** Property search and the rate limiter. Nothing
   exercises the rent billing job, the payment callback path, or document analysis.

---

## 7. External dependencies

Document intelligence calls **Gemini** (`gemini-3.6-flash`, verified live, HTTP 200).
There is no fallback: if Gemini is unreachable the code degrades to
"requires manual review", which is honest but means verification throughput drops to
whatever humans can process. Same shape of exposure for Smile Identity and Ardhisasa,
both currently on placeholder keys.

---

## 8. What "production ready" would require

In order of what would actually cause an incident:

1. ~~Tests for viewing-service and review-service.~~ **Done** — 32 tests added.
2. ~~Resource limits on every container.~~ **Done** — 34 services bounded.
3. ~~Remove placeholder secret defaults.~~ **Withdrawn** — the finding was mistaken; see §3.
4. ~~Outbox for the three services publishing in-band.~~ **Done** — see §4. 20 tests
   added across the three publishers, all asserting the transaction boundary rather than
   trusting the annotation.
5. ~~k8s manifests for the two missing services.~~ **Done** — plus the front end, plus
   every missing environment variable.
6. ~~Freeze applied migrations and add a CI check that they never change.~~ **Done** —
   `scripts/check-migrations-frozen.py`, tamper-tested.
7. ~~Per-service rate limiting, so the gateway is not the only throttle.~~ **Done.** All
   seven services now carry a Redis-backed token bucket shared across their replicas,
   keyed by authenticated user and falling back to client address. Separate buckets for
   reads and writes, so exhausting one does not block the other. It fails open when Redis
   is unreachable — a cache outage taking the service down would be the worse failure —
   and logs when it does. `k8s/network-policy.yaml` was added alongside it, because the
   limiter's use of X-Forwarded-For is only safe while nothing outside the cluster can
   reach a pod directly, and nothing was enforcing that.
8. ~~Finish the four wired-but-unreachable features.~~ **Three of four done** — see §5.
   Card payments remain, and deliberately: that is a new external integration, not a
   missing button.
9. ~~Document storage loses uploads on Kubernetes.~~ **Made safe, not yet resolved.**
   `k8s/user-service.yaml` now ships a PersistentVolumeClaim, is pinned to one replica
   with the reason stated in the manifest, and uses the Recreate strategy so a rollout
   cannot deadlock on a single-writer volume. The startup warning was raised from info to
   warn and now says plainly what breaks. So the default deploy is correct rather than
   quietly lossy — but it cannot scale horizontally until somebody supplies real S3
   credentials. **That decision is still open.**

Nothing on this list now loses data by default. What remains is a choice about object
storage, and a choice about a card provider.
