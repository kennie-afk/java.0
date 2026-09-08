"""Who may do what, per service.

This is the single source of truth for authorisation. `gen_layers.py` turns it
into `@PreAuthorize` annotations on every controller, and `gen_web.py` turns the
same table into the navigation the web application shows. Keeping one table
means the menu can never offer something the service will refuse.

Levels, in increasing order:
  NONE   cannot see it at all
  OWN    may read and act on their own records only (enforced in the service,
         not by an annotation - listed here so the UI knows to show the screen)
  READ   may list and fetch
  WRITE  may also create and update
  FULL   may also delete

Roles:
  ADMIN        platform administrator for the organisation
  FARMER       farm owner; runs the business, hires, sells
  MANAGER      farm or field manager; directs day-to-day work, cannot change pay
  AGRONOMIST   advisory specialist; crops, disease, weather, scouting
  STOREKEEPER  warehouse, stock, dispatch and transport
  FINANCE      payments, ledger, payouts; the only role that moves money
  BUYER        marketplace counterparty; buys produce
  WORKER       field labour; sees their own tasks and nothing else
"""

NONE, OWN, READ, WRITE, FULL = "NONE", "OWN", "READ", "WRITE", "FULL"

ROLES = [
    "ADMIN", "FARMER", "MANAGER", "AGRONOMIST",
    "STOREKEEPER", "FINANCE", "BUYER", "WORKER",
]

ROLE_LABELS = {
    "ADMIN": "Administrator",
    "FARMER": "Farmer",
    "MANAGER": "Farm manager",
    "AGRONOMIST": "Agronomist",
    "STOREKEEPER": "Storekeeper",
    "FINANCE": "Finance",
    "BUYER": "Buyer",
    "WORKER": "Worker",
}

# service slug -> role -> level. Anything unlisted is NONE.
#
# The shape of this table is the product decision, not an implementation detail:
#   - FARMER owns the business: farms, hiring, wage rates, what gets sold.
#   - MANAGER directs the work but cannot change what anyone is paid.
#   - FINANCE is the only role that can move money; FARMER sees it and cannot.
#   - WORKER sees their own tasks and attendance, nothing else.
#   - BUYER sees the market side and their own orders, never the farm's
#     workforce, costs or fraud cases.
MATRIX = {
    # --- growing ---------------------------------------------------------
    "farm":             {"FARMER": FULL,  "MANAGER": WRITE, "AGRONOMIST": READ},
    "season":           {"FARMER": FULL,  "MANAGER": WRITE, "AGRONOMIST": WRITE},
    "agronomy":         {"FARMER": WRITE, "MANAGER": WRITE, "AGRONOMIST": FULL},
    "weather":          {"FARMER": READ,  "MANAGER": READ,  "AGRONOMIST": WRITE},

    # --- devices ---------------------------------------------------------
    "device-registry":  {"FARMER": READ,  "MANAGER": READ},
    "telemetry-ingest": {"FARMER": READ,  "MANAGER": READ,  "AGRONOMIST": READ},
    "automation":       {"FARMER": WRITE, "MANAGER": WRITE, "AGRONOMIST": READ},

    # --- people ----------------------------------------------------------
    # FARMER hires and sets pay; MANAGER runs the day but cannot touch wages.
    "workforce":        {"FARMER": FULL,  "MANAGER": READ,  "FINANCE": READ},
    "attendance":       {"FARMER": READ,  "MANAGER": WRITE, "FINANCE": READ},
    "task":             {"FARMER": WRITE, "MANAGER": FULL,  "WORKER": OWN},
    "fraud":            {"FARMER": READ,  "MANAGER": READ,  "FINANCE": READ},

    # --- trade -----------------------------------------------------------
    "catalog":          {"FARMER": READ,  "MANAGER": READ,  "AGRONOMIST": READ,
                         "STOREKEEPER": READ, "BUYER": READ},
    "marketplace":      {"FARMER": FULL,  "MANAGER": READ,  "BUYER": FULL},
    "pricing":          {"FARMER": READ,  "MANAGER": READ,  "BUYER": READ},
    "order":            {"FARMER": WRITE, "STOREKEEPER": READ, "FINANCE": READ,
                         "BUYER": FULL},
    "inventory":        {"FARMER": READ,  "MANAGER": READ,  "STOREKEEPER": FULL},
    "logistics":        {"FARMER": READ,  "MANAGER": READ,  "STOREKEEPER": WRITE,
                         "BUYER": READ},

    # --- money -----------------------------------------------------------
    # FARMER can see every figure and move none of it.
    "payment":          {"FARMER": READ,  "FINANCE": FULL,  "BUYER": READ},
    "ledger":           {"FARMER": READ,  "FINANCE": FULL},
    "payout":           {"FARMER": READ,  "FINANCE": FULL},

    # --- platform --------------------------------------------------------
    "traceability":     {"FARMER": READ,  "MANAGER": READ,  "AGRONOMIST": READ,
                         "STOREKEEPER": WRITE, "BUYER": READ},
    "notification":     {"FARMER": READ},
    "media":            {"FARMER": WRITE, "MANAGER": WRITE, "AGRONOMIST": WRITE,
                         "STOREKEEPER": WRITE},
    "search":           {"FARMER": READ,  "MANAGER": READ,  "AGRONOMIST": READ,
                         "STOREKEEPER": READ, "FINANCE": READ, "BUYER": READ},
    "analytics":        {"FARMER": READ,  "MANAGER": READ,  "AGRONOMIST": READ,
                         "FINANCE": READ},
    # The audit log is evidence. Nobody edits it, and the roles it watches
    # cannot read it either.
    "audit":            {"FARMER": READ,  "FINANCE": READ},
    "identity":         {"FARMER": WRITE},
}

# Screens that are not one service's CRUD.
FEATURES = {
    # Diagnosis and spray advice stay with whoever is accountable for the
    # decision. A worker photographs the plant and files a scouting report
    # instead - see "report-observation" below.
    "advisor":           ["ADMIN", "FARMER", "MANAGER", "AGRONOMIST"],
    "report-observation": ["ADMIN", "FARMER", "MANAGER", "AGRONOMIST", "WORKER"],
    "my-work":           ["ADMIN", "MANAGER", "WORKER"],
    "live-board":        ["ADMIN", "FARMER", "MANAGER"],
    "account":           ROLES,
    # Managing people and what they may do. A farmer can see who is on the
    # team; only an administrator can change anyone's roles.
    "team":              ["ADMIN", "FARMER"],
    "team-manage":       ["ADMIN"],
}

_ORDER = {NONE: 0, OWN: 1, READ: 2, WRITE: 3, FULL: 4}

# Where a role holds OWN the annotation lets the call through and the service
# must narrow the rows. Anything here that is not yet narrowed is a known gap,
# not a finished control.
# Only entries listed here are actually narrowed. A role must not be given OWN
# on a service that is missing from this map: the annotation would pass and the
# generated list endpoint would return every row in the tenant.
OWN_SCOPED = {
    ("task", "WORKER"): "task assignments belonging to the caller, via /api/task/v1/my-work",
}

# Wanted, but not granted until the same narrowing exists for them:
#   attendance/WORKER  - clock events and shifts for the caller only
#   agronomy/WORKER    - scouting reports the caller filed
#   media/WORKER       - media the caller uploaded
# Each needs the owning entity to carry the caller's user id, as
# TaskAssignment.workerUserId does, plus a scoped endpoint.


def level(service_slug, role):
    """The level `role` holds on `service_slug`. ADMIN always holds FULL."""
    if role == "ADMIN":
        return FULL
    return MATRIX.get(service_slug, {}).get(role, NONE)


def roles_at_least(service_slug, minimum):
    """Roles holding at least `minimum` on a service, for a hasAnyRole clause."""
    threshold = _ORDER[minimum]
    return [role for role in ROLES if _ORDER[level(service_slug, role)] >= threshold]


def _clause(roles):
    if not roles:
        # No role qualifies, so nothing but a denial would be honest.
        return "denyAll()"
    quoted = ", ".join(f"'{role}'" for role in roles)
    return f"hasAnyRole({quoted})"


def read_expression(service_slug):
    """Reads are restricted too: an unrestricted list endpoint leaks wages,
    payouts and fraud cases to anyone holding a token for the tenant.

    OWN counts as read here, because the annotation is coarse - it can say
    "this role may call the endpoint" but not "only their own rows". The
    row-level narrowing is the service's job; OWN_SCOPED lists what still
    needs it.
    """
    return _clause(roles_at_least(service_slug, OWN))


def write_expression(service_slug):
    return _clause(roles_at_least(service_slug, WRITE))


def delete_expression(service_slug):
    return _clause(roles_at_least(service_slug, FULL))


def can_use(feature, role):
    return role in FEATURES.get(feature, [])
