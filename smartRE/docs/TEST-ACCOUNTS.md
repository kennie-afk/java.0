# SmartRE test accounts

Accounts for exercising the platform locally. Created through the real
registration endpoint, so the passwords are hashed the same way a genuine
sign-up would hash them.

**Every account below shares one password:**

```
SmartRE-Demo-2026!
```

Verified working on 2026-09-08 — each one was logged in through
`POST /api/auth/login` and returned `200` with the role shown.

| Email | Role | Who they are |
|---|---|---|
| `demo.admin@smartre.test` | ADMIN | Platform administrator. Sees every listing, user and payment. |
| `demo.seller@smartre.test` | SELLER | Lists property for sale. Owns listings, sees offers and viewings. |
| `demo.buyer@smartre.test` | BUYER | Buys property. Books viewings, makes offers, pays deposits. |
| `demo.landlord@smartre.test` | LANDLORD | Lets property. Owns units, tenancies, rent invoices. |
| `demo.tenant@smartre.test` | BUYER | A renting tenant — see the note on roles below. |

## There is no TENANT role

The platform has exactly four roles:

```java
public enum Role { BUYER, SELLER, LANDLORD, ADMIN }
```

A tenant is a **BUYER** account whose relationship is a tenancy rather than a
sale. `demo.tenant@smartre.test` is a BUYER, and is listed separately only
because "tenant" is how the person is spoken about, not how the system stores
them. If tenants ever need permissions a buyer should not have — seeing their own
lease but not the sale marketplace, say — that is a fifth role and a schema
change, not a labelling exercise.

## Administrators cannot self-register

`POST /api/auth/register` with `"role":"ADMIN"` returns **403**, which is
correct: an open endpoint that mints administrators is an open door.
`demo.admin@smartre.test` was registered as a BUYER and promoted with SQL. Do the
same for any future administrator, or add an invite endpoint that an existing
admin must authenticate against.

## Registration is rate limited

Creating several accounts in a row returns **429**. That is the platform's own
rate limiter working, not a fault. Space the calls out or wait about twenty
seconds between them.

## Running it

```bash
cd ~/Software_dev/java.0/smartRE

# Infrastructure first — the databases and poolers must be healthy before the
# services try to migrate against them.
docker compose up -d redis zookeeper kafka \
  user-db user-pgbouncer verification-db verification-pgbouncer \
  property-db property-pgbouncer viewing-db viewing-pgbouncer \
  payment-db payment-pgbouncer review-db review-pgbouncer \
  notification-db notification-pgbouncer pms-db pms-pgbouncer

# Then the services.
docker compose up -d user-service verification-service property-service \
  viewing-service payment-service review-service notification-service \
  property-management-service api-gateway web
```

- Web: <http://localhost:3000>
- API gateway: <http://localhost:8080>

The observability stack (prometheus, grafana, loki, promtail, alertmanager,
kafka-ui) is deliberately left out of the commands above. It is another six
containers, and this machine has 11Gi — with the full stack running there is
under 2Gi free, which is where background work starts getting killed. Add them
when you actually want to look at a dashboard.

**Only run one platform at a time.** SmartSeason is 33 containers and SmartRE is
29; together they do not fit. Stop one before starting the other.

## Pre-existing accounts

The database already held 33 accounts from earlier testing (2 ADMIN, 11 BUYER,
16 SELLER, 4 LANDLORD). Their passwords are bcrypt hashes and are not recoverable
— use the accounts in the table above, or reset one through
`POST /api/auth/forgot-password`.

## Do not ship this file's password anywhere real

These credentials are for a local machine. The accounts are `@smartre.test`,
which is a reserved TLD and cannot receive mail, so they are useless outside this
laptop — but the password pattern should not be reused on anything reachable.
