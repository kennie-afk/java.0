# SmartSeason test accounts

Nine accounts, one per role plus a second worker. Created through the platform's
own team endpoint, so the passwords are hashed exactly as a real sign-up would
hash them.

**Every account below shares one password:**

```
a-strong-demo-passphrase
```

| Email | Role | What they can reach |
|---|---|---|
| `demo@smartseason.local` | ADMIN | Everything: all 27 services, full read and write. |
| `farmer@smartseason.local` | FARMER | Owns the business. Reads all 27, writes 10. Sees money, cannot move it. |
| `manager@smartseason.local` | MANAGER | Runs the day. Reads 20, writes 7. **Cannot change wage rates.** |
| `agronomist@smartseason.local` | AGRONOMIST | Crops and disease. Reads 11, writes 4. No workforce, no money. |
| `store@smartseason.local` | STOREKEEPER | Stock and dispatch. Reads 7, writes 4. |
| `finance@smartseason.local` | FINANCE | The only role that can move money. Reads 10, writes 3. |
| `buyer@smartseason.local` | BUYER | Market side only. Reads 8, writes 2. Never sees workforce or fraud. |
| `amina@smartseason.local` | WORKER | Her own tasks and nothing else. Reads 1 service, writes none. |
| `joseph@smartseason.local` | WORKER | A second worker, so "you cannot touch another worker's task" is demonstrable. |

## The roles are the point

Signing in as each account changes what the platform *is*, not just what the menu
shows. The same table drives both the generated `@PreAuthorize` annotations and
the navigation, so the menu can never offer something a service will refuse
(`tools/rbac.py`).

| Role | Reads | Writes | Deletes |
|---|---|---|---|
| ADMIN | 27 | 27 | 27 |
| FARMER | 27 | 10 | 4 |
| MANAGER | 20 | 7 | 1 |
| AGRONOMIST | 11 | 4 | 1 |
| STOREKEEPER | 7 | 4 | 1 |
| FINANCE | 10 | 3 | 3 |
| BUYER | 8 | 2 | 2 |
| WORKER | 1 | 0 | 0 |

Three separations worth trying deliberately:

- **A manager cannot change pay.** Sign in as `manager@`, open Workforce → wage
  rates. It is readable and the create button is gone; the API returns 403 to a
  direct call.
- **A farmer can read the ledger and not post to it.** `farmer@` sees every
  figure in Money; only `finance@` can write there.
- **A worker sees only their own work.** `amina@` gets Overview, My work and one
  service. Signing in as `joseph@` and trying Amina's task returns **404**, not
  403 — confirming an assignment exists is itself information a worker should not
  have.

Screens that are not one service's CRUD:

| Screen | Roles |
|---|---|
| Crop advisor | ADMIN, FARMER, MANAGER, AGRONOMIST |
| Report an observation | ADMIN, FARMER, MANAGER, AGRONOMIST, WORKER |
| My work | ADMIN, MANAGER, WORKER |
| Live work board | ADMIN, FARMER, MANAGER |
| Team | ADMIN, FARMER (view) · ADMIN only (change roles) |

## Signing in

Ports are **remapped** — web on 13000, gateway on 18080, not 3000/8080:

- Web: <http://localhost:13000>
- API gateway: <http://localhost:18080>

The sign-in page has a **"Sign in as"** picker listing all nine accounts, so you
can move between roles in one click. It is enabled by `QUICK_SIGN_IN=true` with
`QUICK_SIGN_IN_PASSWORD` in `.env`, and it authenticates **without a password**
against a hard-coded allowlist. Both default to off. It belongs on a build
machine and nowhere real people sign in.

Roles are managed in the product at **`/team`** — an admin adds people (name,
email, phone, temporary password, role checkboxes) and changes roles inline. That
is the path to use; the picker is a convenience on top of it.

## Amina is linked to a worker record

`amina@smartseason.local` is joined to her `workers` row through
`workers.user_id`, and her assignments carry `task_assignments.worker_user_id`.
Without those links "My work" is empty and the live board shows "Unassigned" —
a worker account with no worker record is just an account. When you add a worker,
set the **Farm** and the **person** on the worker record, both of which are
pickers rather than UUID boxes.

## Running it

```bash
cd ~/Software_dev/java.0/smartSeason

# All 27 services sit behind compose profiles, so a bare `up -d` starts only
# postgres, redis, redpanda, pgbouncer, api-gateway and web.
docker compose --profile all up -d

# Seed a coherent dataset across every service. Safe to re-run: each record is
# looked up by a natural key first.
GATEWAY=http://localhost:18080 python3 tools/seed_demo.py
```

Two things that will bite otherwise:

- **After recreating backends, restart the gateway.** Its circuit breakers stay
  open and every call returns 503 `upstream-unavailable` even though the service
  is healthy. `docker compose restart api-gateway` clears it. A 503 from the
  gateway while the service answers on its own port is this, not an outage.
- **Confirm containers are on the current image.** `up -d` without
  `--force-recreate` will happily leave a container on a stale image; compare
  `docker inspect -f '{{.Image}}'` against `docker images -q`.

**Only run one platform at a time.** SmartSeason is 33 containers and SmartRE is
29; together they do not fit in 11Gi. Stop one before starting the other.

## Provenance

The nine accounts were created and each one signed in successfully during the
session of 2026-09-08, and the role matrix above was verified against the live
API — a probe hit every service as all eight roles and matched the table
exactly. They were **not** re-checked at the moment this file was written, because
SmartRE was running and the two platforms cannot share this machine. If a login
fails, the accounts still exist in `identity_db`; check `/team` or reset through
`POST /api/identity/v1/auth/forgot-password`.

## Do not reuse this password anywhere real

`.local` is a reserved TLD, so these addresses cannot receive mail and are
useless off this laptop. The password pattern should still not appear on anything
reachable.
