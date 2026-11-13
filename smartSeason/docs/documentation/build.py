#!/usr/bin/env python3
"""Builds SmartSeason-Documentation.html from the service catalogue and README, then prints it to PDF.

    python3 docs/documentation/build.py

The service list, ports, databases and aggregates are read from tools/catalogue.py and the README tables,
so they cannot drift from the code. Everything else is written text in this file.
"""
import html
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import catalogue  # noqa: E402

E = html.escape


def readme_rows():
    """service -> (port, db, responsibility) from the README service tables."""
    rows = {}
    for line in open(os.path.join(ROOT, "README.md"), encoding="utf-8"):
        m = re.match(r"\|\s*`([a-z-]+-service)`\s*\|\s*(\d+)\s*\|\s*`([a-z_]+)`\s*\|\s*(.+?)\s*\|\s*$", line)
        if m:
            rows[m.group(1)] = (m.group(2), m.group(3), m.group(4))
    return rows


README = readme_rows()
SERVICES = {s["name"]: s for s in catalogue.SERVICES}
N_SERVICES = len(catalogue.SERVICES)
N_AGG = sum(len(s["entities"]) for s in catalogue.SERVICES)

# ---------------------------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------------------------


def p(t, cls=""):
    return f'<p class="{cls}">{t}</p>' if cls else f"<p>{t}</p>"


def ul(items):
    return "<ul>" + "".join(f"<li>{i}</li>" for i in items) + "</ul>"


def ol(items):
    return "<ol>" + "".join(f"<li>{i}</li>" for i in items) + "</ol>"


def table(head, rows, cls=None, long=False):
    cls = cls or [""] * len(head)
    th = "".join(f'<th class="{c}">{h}</th>' for h, c in zip(head, cls))
    body = "".join("<tr>" + "".join(f'<td class="{c}">{v}</td>' for v, c in zip(r, cls)) + "</tr>" for r in rows)
    kind = "long" if long else ""
    return f'<table class="{kind}"><thead><tr>{th}</tr></thead><tbody>{body}</tbody></table>'


def box(title, inner, warn=False):
    return f'<div class="box{" warn" if warn else ""}"><p class="bt">{title}</p>{inner}</div>'


def fig(img, caption):
    return f'<figure><img src="img/{img}" alt=""><figcaption>{caption}</figcaption></figure>'


def h2(t, first=False, page=False):
    c = ("first " if first else "") + ("pb" if page else "")
    return f'<h2 class="{c.strip()}">{t}</h2>' if c.strip() else f"<h2>{t}</h2>"


# ---------------------------------------------------------------------------------------------
# service detail: what each one does beyond generated CRUD, written by hand
# ---------------------------------------------------------------------------------------------
DEPTH = {
    "identity-service": "Registration and login, JWT minting, BCrypt at cost 12, refresh-token rotation (reuse revokes the whole family), lockout after five failures, login errors that do not reveal whether an account exists, and the team endpoint that creates one account per role.",
    "farm-service": "The dairy module: cows, milk yields, milk deliveries, health events and breeding events, plus a summary endpoint (litres, rejection rate, weighted fat, delivered value, cows under medicine withdrawal, expected calvings). Rules the database enforces itself: one tag per farm, one reading per cow per day and session, rejected litres never above delivered.",
    "agronomy-service": "The crop advisor: diagnose from a description and up to four photographs, forecast a season, outline the weather. It uses a vision model when an API key is set and a built-in rules engine otherwise (nine pest and disease signatures, five crop profiles), and every answer states which of the two produced it.",
    "automation-service": "Rule evaluation with cooldowns and safety interlocks: a manual override always wins, two actuators that must not run together are mutually exclusive, and every command has a maximum runtime.",
    "attendance-service": "Geofence enforcement: a clock event with mocked GPS or a position outside the fence is rejected, a weak biometric match is flagged, and every accepted event is published for the fraud engine to read.",
    "task-service": "Start and stop with the time taken on the server, never from the worker's device. Checklists, photo and GPS evidence, and a worker can see only the tasks assigned to their own account. Each start and stop is written to the audit chain.",
    "fraud-service": "A rules engine with five detectors (ghost worker, proxy clock-in, impossible travel, piece-rate inflation, hours inflation). Confidences combine probabilistically, so two independent signals of 0.5 score 75, not 100. A confidence of 0.60 opens a case for human review, and 0.75 also asks for the payout to be held. Nothing suspends a person automatically.",
    "order-service": "The fulfilment saga: a sequence of steps that, when one fails, compensates the earlier ones in reverse order, and carries on compensating even if a compensation step itself fails.",
    "inventory-service": "Reservations that hold stock without deducting it, oversell prevention, and an expiry sweep that releases holds nobody completed.",
    "payment-service": "M-Pesa STK push, B2C and the callback endpoint Safaricom posts to. Phone numbers and whole-shilling amounts are validated, callbacks are idempotent (the checkout request id is the key, so a second delivery changes nothing), and a shared secret guards the callback address. A mock gateway runs in the demo; the live gateway needs Safaricom credentials and a public callback address.",
    "ledger-service": "Double-entry posting where every entry must balance, replayed requests do not post twice, closed accounts and currency mismatches are refused, and balances are reported on the account's normal side.",
    "payout-service": "An eligibility policy that holds a payout when there is an open fraud case, an open dispute, a sanction, missing KYC, or the amount is above a ceiling.",
    "notification-service": "English and Swahili templates with locale fallback, quiet hours, and suppression of any message whose variables were left unfilled.",
    "audit-service": "A tamper-evident log: each record's hash covers its own content and the previous record's hash. The service assigns the sequence and both hashes itself, never the caller, and a verify endpoint names the first broken record.",
}
CRUD_ONLY = [n for n in SERVICES if n not in DEPTH]
NOT_IN_DEMO = {"device-registry-service", "telemetry-ingest-service", "automation-service", "notification-service", "search-service", "analytics-service"}

GROUPS = [
    ("identity", "Identity and access"),
    ("farm", "Farm and agronomy"),
    ("iot", "Automation and devices"),
    ("workforce", "Workforce and integrity"),
    ("market", "Commerce"),
    ("money", "Money"),
    ("platform", "Platform"),
]


def service_cards():
    out = []
    for key, title in GROUPS:
        out.append(f"<h3>{title}</h3>")
        for s in [x for x in catalogue.SERVICES if x["group"] == key]:
            name = s["name"]
            port, db, resp = README.get(name, (str(s["port"]), s["db"], s["desc"]))
            aggs = ", ".join(e[0] for e in s["entities"])
            depth = DEPTH.get(name)
            badge = '<span class="pill no">not in the demo</span>' if name in NOT_IN_DEMO else ""
            lab = "Beyond generated CRUD" if depth else "Implementation"
            body = depth or "Generated layer only: schema, tenant-scoped repositories, validated DTOs, REST endpoints, role checks and tests. No business behaviour beyond create, read, update and delete yet."
            out.append(
                f'<div class="card"><div><span class="name">{name}</span><span class="meta">port {port} &middot; database {db} &middot; {len(s["entities"])} aggregates</span>{badge}</div>'
                f"<p>{resp}</p>"
                f'<p><span class="lab">Aggregates</span>{E(aggs)}</p>'
                f'<p><span class="lab">{lab}</span>{body}</p></div>'
            )
    return "".join(out)


# ---------------------------------------------------------------------------------------------
# document
# ---------------------------------------------------------------------------------------------
parts = []
A = parts.append

A(f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><title>SmartSeason: system documentation</title>
<link rel="stylesheet" href="style.css"></head><body>
<h1>SMARTSEASON</h1>
<p class="tag">Agricultural operations platform for Kenya &nbsp;&middot;&nbsp; System documentation, end to end</p>
<p class="contact">Prepared by Kennedy Mwanzia &nbsp;&middot;&nbsp; github.com/kennie-afk &nbsp;&middot;&nbsp; kennedy-portfolio-rouge.vercel.app &nbsp;&middot;&nbsp; 13 November 2026</p>
""")

A(h2("What SmartSeason is", first=True).replace("<h2", '<h2 style="margin-top:34pt"', 1))
A(p("""SmartSeason is software that helps a farm business in Kenya run itself and get paid. It puts everything that happens on a farm in one place: the land and the crops, the people hired to work, the produce harvested, the buyers who order it, the delivery, and the M-Pesa money that pays for it. Every action is recorded with who did it and when, and the records cannot be quietly changed.""", "lead"))

A("<h3>The problem it solves</h3>")
A(p("""A farm that hires labour and sells produce loses money and time in the same few places, and mostly cannot see where."""))
A(ul([
    "<b>Pay for work that was not done.</b> Workers who do not exist, hours that are padded, someone clocking in for a friend. The owner cannot be on every plot, so the loss goes unseen.",
    "<b>Records on paper or in memory.</b> Which crop is where, what fertiliser went on which plot, how much milk each cow gave. When something goes wrong nobody can say what happened.",
    "<b>Selling and getting paid is slow and uncertain.</b> Orders agreed by phone, produce delivered with no proof of weight, payment chased for weeks, disputes with no evidence either way.",
    "<b>No proof of where produce came from.</b> Buyers, especially exporters, increasingly want to know which farm, which batch and which certificate.",
]))

A("<h3>What it does about each one</h3>")
A(table(["On the farm", "What SmartSeason does"], [
    ["Land and crops", "Records farms, plots and the season for each crop, with the calendar of stages and a warning when a crop is running late. Gives advice on pests and diseases from a description or a photograph."],
    ["Workers and pay", "A worker is given a task on their phone and presses Start and Stop. The <b>time is recorded by the system, not the phone</b>, so it cannot be changed. Clock-in only works at the farm. Suspicious patterns are flagged for the owner to review, and payments can be held until they are cleared."],
    ["Produce and sales", "Records each harvest batch and its grade, lists produce for buyers, takes their orders, holds the stock for the order and tracks delivery to the door with proof of what was received."],
    ["Money", "Collects payment through M-Pesa, records every shilling in a proper double-entry ledger, and pays farmers and workers out, with checks that stop a payment going to the wrong place."],
    ["Dairy", "Records each cow's milk, what was delivered and what was rejected, and warns when milk is from a cow still under a medicine withdrawal and must not be sold."],
    ["Proof and trust", "Keeps a tamper-evident log of important actions, and traces a batch back to its farm and certificates with a QR code."],
], ["nw", ""], long=True))

A("<h3>Who uses it</h3>")
A(p("""Eight kinds of people, and each sees a different system. The <b>farm owner</b> sees everything and the money. A <b>manager</b> assigns and checks work but cannot change anyone's pay. A <b>worker</b> sees only their own tasks. An <b>agronomist</b> sees crops, a <b>storekeeper</b> sees stock, <b>finance</b> is the only role that can move money, and a <b>buyer</b> sees only the produce side. This separation is deliberate: it is what makes the fraud controls trustworthy."""))

A("<h3>A day on a farm</h3>")
A(p("""At seven a worker opens SmartSeason, sees <i>Top-dress nitrogen on Plot A1</i>, and presses Start. The owner, in town, sees on the live board that it started at 07:02. A photograph and the plot's location are attached when it is done, and the manager marks it verified. At the cooler, the dairy clerk records that 6 litres were rejected. When the maize is sold, the buyer's order reserves the stock, the truck is dispatched, the buyer's M-Pesa payment arrives, and the ledger shows the sale, the fee and what the farmer is owed. Nobody had to phone anybody, and every step has a time and a name against it."""))

A("<h3>Where it stands</h3>")
A(p("""It is a complete, working system in demonstration form, and it has not yet had a real customer. Live M-Pesa is not switched on, and some parts are built fully while others hold the data correctly but do not yet act on it. Section 10 lists exactly what is proven and what is not. Sections 11 and 12 explain how it can earn money and how to take it to market."""))

A("<h3>In technical terms</h3>")
A(p(f"""SmartSeason is built as {N_SERVICES} Spring Boot microservices ({N_AGG} persisted aggregates) behind one Spring Cloud Gateway, with a Next.js web application. Each service owns its own PostgreSQL database and talks to the others only through events on Kafka. Eight roles each see a different platform, and one role table decides both what the menu shows and what each service will accept. No organisation can see another's data, enforced in the code and again in the database.""", "keep"))

A("<h3>Contents</h3>")
toc = ["Status at a glance", "Who uses it", "How it works, end to end", "Architecture", "The services in detail", "The web application",
       "Security and integrity", "Running and operating it", "Scale and performance", "Proven and not yet proven", "How it makes money", "Going to market", "What to build next to earn", "Appendices"]
A('<div class="toc">' + "".join(f"<div><b>{i}</b>{t}</div>" for i, t in enumerate(toc, 1)) + "</div>")

# 1 ---------------------------------------------------------------------------------------------
A(h2("1. Status at a glance", page=True))
A(table(["Item", "Fact"], [
    ["Domain services", f"{N_SERVICES}, each with its own database, port and migrations"],
    ["Persisted aggregates", f"{N_AGG} (109 from the original catalogue plus the 5 of the dairy module)"],
    ["Edge", "One Spring Cloud Gateway: token check, rate limiting, circuit breakers"],
    ["Web application", "Next.js 16, React 19, TypeScript, Tailwind CSS 4; one codebase for every role"],
    ["Language and runtime", "Java 21, Spring Boot 3.5, PostgreSQL 16, Kafka (Redpanda locally), Redis, PgBouncer, MinIO"],
    ["Roles", "Eight: administrator, farmer (owner), manager, agronomist, storekeeper, finance, buyer, worker"],
    ["Services with business logic", f"{len(DEPTH)} of {N_SERVICES}; the other {len(CRUD_ONLY)} carry the generated layer only"],
    ["Demo stack", "27 containers (20 services plus database, pooler, cache, broker, object store, gateway, web), about 7.4 GiB; six services are left out and their menus hidden"],
    ["Tests", "559 passing, 0 failing, across all services and the gateway at the last full count (28 September 2026); later work added its own tests"],
    ["Customers", "None. Nothing in this document is a customer figure"],
], ["nw", ""]))
A(box("How to read this document", p("Statements about what the system does describe the code as it stands. Statements about money in sections 11 and 12 are proposals to be tested in a pilot, not results. Where something is untested, unbuilt or unconnected it is labelled as such rather than left implied.")))

# 2 ---------------------------------------------------------------------------------------------
A(h2("2. Who uses it"))
A(p("""The platform is not one screen with a few permissions. Signing in as a different role changes what the platform is. One table of roles generates both the access rules on every service and the navigation, so the menu cannot offer anything a service will refuse."""))
A(table(["Role", "Who they are", "Can read", "Can write", "Notable limits"], [
    ["Administrator", "Platform operator for the organisation", "All 27 areas", "All", "Everything"],
    ["Farmer (owner)", "Owns the business", "All 27", "10", "Sees every money figure, cannot post to the ledger"],
    ["Manager", "Runs the day", "20", "7", "Assigns work and runs attendance, <b>cannot change wage rates</b>"],
    ["Agronomist", "Crops and disease", "11", "4", "No workforce, no money"],
    ["Storekeeper", "Stock and dispatch", "7", "4", "Inventory and logistics only"],
    ["Finance", "Money", "10", "3", "<b>The only role that can move money</b>"],
    ["Buyer", "Market side", "8", "2", "Never sees workforce or fraud"],
    ["Worker", "Hired labour", "1", "0", "Sees only their own tasks; another worker's task returns not found, not forbidden"],
], ["nw", "", "n", "n", ""]))
A(p('<span class="small">The 27 areas are the 26 services plus the dairy module, which has its own row in the role table.</span>'))
A(p("""Three separations are worth showing to any buyer of the system. A manager can see wage rates but the create button is gone and a direct call is refused. A farmer can read the whole ledger and post nothing to it, which is what makes the fraud controls mean something. A worker who guesses another worker's task address gets <i>not found</i>, because confirming that an assignment exists is itself information they should not have."""))

# 3 ---------------------------------------------------------------------------------------------
A(h2("3. How it works, end to end", page=True))
A(p("""The best way to understand SmartSeason is to follow one season on one farm. The steps below name the service that does the work. Nothing in a step reaches into another service's database; where one step hands over to the next, it does so with an event or a call through the gateway."""))

A("<h3>A. Setting up the business</h3>")
A(ol([
    "<b>Register.</b> The owner registers an organisation. <span class='muted'>identity-service</span> creates it as the tenant root and mints a signed token. Every row anywhere in the platform from then on carries that tenant.",
    "<b>Add people.</b> The owner adds a manager, an agronomist, a storekeeper, finance staff, buyers and workers through the team screen. One account per person, one or more roles each.",
    "<b>Verify identity.</b> A KYC record can be attached to a person or the organisation, and a missing KYC is later one of the reasons a payout is held.",
]))
A("<h3>B. Land, season and advice</h3>")
A(ol([
    "<b>Farms and plots.</b> <span class='muted'>farm-service</span> holds farms, plots, boundaries, soil profiles and cooperative membership.",
    "<b>A season on a plot.</b> <span class='muted'>season-service</span> holds the crop cycle, its stage calendar (planting, knee height, tasseling, harvest) and the planting plan. The overview shows each season's progress against the expected harvest date and flags one that is overdue.",
    "<b>Weather and vigour.</b> <span class='muted'>weather-service</span> holds forecasts, frost and rain alerts per geographic cell and satellite vegetation readings (NDVI) per plot.",
    "<b>Scouting and advice.</b> Anyone, including a worker, files a scouting report with a photograph. The agronomist or owner can ask the crop advisor to diagnose from text and photographs. The advisor states whether a vision model or the built-in rules answered, and the rules path says plainly that it did not look at the photographs.",
]))
A("<h3>C. Hired labour, and proof that it was done</h3>")
A(ol([
    "<b>Workers and pay.</b> <span class='muted'>workforce-service</span> holds workers, contracts, wage rates, gangs and supervisors. The owner sets wage rates; a manager cannot.",
    "<b>Work is assigned.</b> <span class='muted'>task-service</span> holds work orders, assignments and checklists. The manager assigns a task to a worker.",
    "<b>The worker presses Start and Stop.</b> The time is taken on the server, because a device clock is the first thing anyone inflating hours would change. The worker's phone may only say that something happened, never when. Photo and GPS evidence are stored as claims alongside a flag for mocked locations.",
    "<b>The employer watches live.</b> The live board shows who started what, at what time, for how long, and flags a task running beyond its estimate.",
    "<b>Clock-in.</b> <span class='muted'>attendance-service</span> accepts a clock event only inside the geofence, rejects mocked GPS and flags a weak biometric match.",
    "<b>The fraud engine reads the event.</b> The clock event goes onto Kafka. <span class='muted'>fraud-service</span> scores it with five detectors, opens a case at confidence 0.60 and asks for a payout hold at 0.75. It stores the evidence and its reasoning with the case, because the decision affects someone's pay and a person reviews it.",
]))
A("<h3>D. Inputs and stock</h3>")
A(ol([
    "<b>Issue and apply.</b> The storekeeper issues fertiliser or seed to a plot; the farmer records what was applied. <span class='muted'>inventory-service</span> compares the two against the expected rate per hectare and records the variance.",
    "<b>Harvest into batches.</b> Produce is received into a warehouse as a batch, graded (assigned grade, defect and moisture percentages, rejected kilograms) and becomes stock.",
]))
A("<h3>E. Selling, delivering and getting paid</h3>")
A(ol([
    "<b>Catalogue and price.</b> <span class='muted'>catalog-service</span> holds products, variants and certifications. <span class='muted'>pricing-service</span> holds the market index and price quotes per commodity and region.",
    "<b>Order.</b> A buyer fills a cart and places an order. <span class='muted'>order-service</span> runs the fulfilment saga and compensates in reverse if a step fails. Returns and disputes live here.",
    "<b>Reserve.</b> <span class='muted'>inventory-service</span> holds stock for the order without deducting it, and releases it if the order lapses.",
    "<b>Deliver.</b> <span class='muted'>logistics-service</span> assigns a vehicle and driver, records the route, cold-chain readings and a breach flag, and captures proof of delivery with the weight received and any variance.",
    "<b>Collect.</b> <span class='muted'>payment-service</span> raises a payment intent and an M-Pesa STK push. Safaricom's callback is matched by checkout request id, so a repeat changes nothing.",
    "<b>Record.</b> <span class='muted'>ledger-service</span> posts balanced double-entry lines: cash, escrow payable, commission income. Postings are immutable.",
    "<b>Settle.</b> <span class='muted'>payout-service</span> pays the farmer and wages out, applying fees, and holds anything with an open fraud case, dispute, sanction, missing KYC or an amount over the ceiling.",
]))
A("<h3>F. Provenance and accountability</h3>")
A(ol([
    "<b>Trace.</b> <span class='muted'>traceability-service</span> links a batch back through its inputs to its plot and attaches certificate evidence, with a QR pass a buyer can scan.",
    "<b>Audit.</b> <span class='muted'>audit-service</span> records privileged actions in a hash chain. Edit any record and every later hash fails, which the verify endpoint reports by sequence number.",
]))
A("<h3>G. Dairy</h3>")
A(p("""A herd of cows is recorded with a tag each. Milk is recorded per cow per day and session, delivered to a buyer with a receipt, fat and solids readings and a price per litre the user enters. A medicine treatment records the last day the milk must be held back. The dairy summary then flags any milk that was recorded or delivered inside a withdrawal period and lists expected calvings. The system never guesses a price or a withdrawal period; both are whatever the user types from the medicine label or the vet."""))

A(fig("02-overview.jpg", "<b>Figure 1.</b> The administrator's overview: farms, workers, orders, fraud cases and users; the crops in the ground with progress against the expected harvest; and the work in progress with start times."))

# 4 ---------------------------------------------------------------------------------------------
A(h2("4. Architecture"))
A(p("""Every request enters through the gateway, which checks the token, rate limits the caller and wraps the route in a circuit breaker. Each service then checks the token again on its own, so a service reached directly is still closed."""))
A('<div class="diagram">'
  "                          browser\n"
  "                             |\n"
  "                  Next.js web application\n"
  "                             |\n"
  "              Spring Cloud Gateway  (token check, rate limit, breakers)\n"
  "                             |\n"
  "     +-----------+-----------+-----------+-----------+-----------+\n"
  "     |           |           |           |           |           |\n"
  "  identity     farm       workforce    commerce     money      platform   ... 26 services\n"
  "     |           |           |           |           |           |\n"
  "  one database per service (PostgreSQL 16, forward-only migrations, row-level security)\n"
  "                             |\n"
  "                           Kafka   (events keyed by aggregate id)</div>")
A("<h3>Request path</h3>")
A(ol([
    "The gateway matches <code>/api/&lt;service&gt;/**</code> to exactly one upstream. Route prefixes are derived from the service name, so two routes cannot collide.",
    "The token is verified at the edge and the subject and tenant are forwarded as headers.",
    "A circuit breaker wraps every route. A failing service returns a clean 503 problem document instead of hanging the caller.",
    "A Redis token bucket rate limits per signed-in user, falling back to the address for anonymous callers.",
    "The service verifies the token again, applies its role rule, and then does the work inside a transaction.",
]))
A("<h3>Tenancy</h3>")
A(p("""The organisation is the tenant. Every table carries <code>tenant_id</code> from its first migration. Repositories expose only tenant-qualified finders; there is no bare find-by-id for application code to reach for. A row that belongs to another tenant is indistinguishable from a row that does not exist, so a cross-tenant request returns not found, not forbidden. An unbound tenant throws instead of widening the query."""))
A(p("""A second layer sits in the database itself. Services connect as an unprivileged database role, and row-level security on the tables makes the database refuse a cross-tenant read even if application code forgot the tenant. The owner credentials are used only by the migration tool."""))
A("<h3>Persistence, events and errors</h3>")
A(ul([
    "<b>PostgreSQL 16 with Flyway.</b> Migrations only move forward and the schema is validated against the mapping at startup, so drift fails the start instead of corrupting data. A freeze list stops an applied migration from being edited.",
    "<b>Optimistic locking everywhere.</b> Two writers to the same record cannot silently overwrite each other; the second gets a 409 stale-write.",
    "<b>Connection pooling.</b> PgBouncer in transaction mode keeps three real database connections at idle for the whole platform.",
    "<b>Events on Kafka.</b> Each message carries an event id, type, version, tenant, aggregate id, time and producer, is keyed by aggregate id so one record's events stay in order, and travels on a versioned topic such as <code>ss.workforce.clock-event-created.v1</code>. A transactional outbox means an event is only published if the change that caused it committed.",
    "<b>Errors as RFC 7807.</b> Every failure is a problem document with a stable code. Clients branch on the code, never on the wording. Stack traces and driver messages are logged, never returned.",
]))
A("<h3>Code generation</h3>")
A(p(f"""<code>tools/catalogue.py</code> is the single source of truth for service names, ports, databases and aggregates. A generator emits the mechanical layer from it: entities, tenant-scoped repositories, DTOs, migrations, gateway routes, container definitions, monitoring targets, role annotations, Kubernetes manifests and the web application's screens for all {N_AGG} aggregates. Hand-written business logic lives in an overlay that regeneration never overwrites. The consequence is that the generated parts cannot disagree with each other, and that a new aggregate is one catalogue line, not a dozen files."""))

# 5 ---------------------------------------------------------------------------------------------
A(h2("5. The services in detail", page=True))
A(p(f"""There are {N_SERVICES} services in seven groups. {len(DEPTH)} carry business logic beyond the generated layer and are described in full. The other {len(CRUD_ONLY)} carry the generated layer only, which means a correct schema, tenant isolation, validation, role checks, REST endpoints and tests, but no behaviour yet beyond creating, reading, updating and deleting records. That is a deliberate order of work, not an omission by accident, and it is stated here so nobody sells them as more than they are."""))
A(service_cards())

# 6 ---------------------------------------------------------------------------------------------
A(h2("6. The web application"))
A(p("""One Next.js application serves every role. The navigation is generated from the same role table as the service access rules. Beyond the generated list-and-form screens for every aggregate, a small number of purpose-built screens carry the work people do every day."""))
A(table(["Screen", "For", "What it does"], [
    ["Overview", "Everyone", "Role-specific summary: counts, crops in the ground with progress, work in progress"],
    ["My work", "Workers", "Their own assigned tasks with Start and Stop and a live timer; the time is the server's"],
    ["Live work", "Owner, manager", "Every task in progress, who started it and when, elapsed time, over-estimate flags"],
    ["Dairy", "Owner, manager, agronomist", "Herd summary per farm, withdrawal alerts, expected calvings"],
    ["Team", "Owner", "Create accounts and assign roles"],
    ["Crop advisor", "Owner, manager, agronomist", "Describe or photograph a problem and get a diagnosis, labelled by what produced it"],
    ["Farm, Workforce, Market, Money, Platform, Identity", "By role", "Generated screens for every aggregate: search, list, create, edit, delete"],
], ["nw", "", ""]))
A(p("""The visual design is compact and quiet: a serif heading face over a clean text face, one-pixel borders, no shadows, colour-only hover states and a green accent. Dense tables are the point, because the people using it want to see many records at once."""))
A(fig("01-login.jpg", "<b>Figure 2.</b> Sign-in. On a demonstration stack a &ldquo;Sign in as&rdquo; picker lets a viewer enter as any role without typing a password. It is switched off outside demonstration builds."))
A(fig("15-my-work.jpg", "<b>Figure 3.</b> A worker sees Overview, My work and one service. The time shown against a task is recorded by the platform, not by the phone."))
A(fig("09-live.jpg", "<b>Figure 4.</b> The employer's live board: who started what and when, how long it has run, and which tasks have overrun their estimate."))
A(fig("07-dairy.jpg", "<b>Figure 5.</b> The dairy summary for a farm: a withdrawal alert at the top, litres recorded and delivered, rejection rate, weighted fat, delivered value and milk recorded inside a medicine withdrawal period."))
A(fig("11-advisor.jpg", "<b>Figure 6.</b> The crop advisor. The answer says whether it came from a vision model or from the built-in rules."))
A(fig("08-fraud-cases.jpg", "<b>Figure 7.</b> Fraud cases with severity, confidence and status. Each case carries its evidence and reasoning for the person who reviews it."))
A(fig("13-ledger.jpg", "<b>Figure 8.</b> The ledger's accounts: cash and bank, escrow payable and commission income, with normal balance and currency."))
A(fig("14-audit.jpg", "<b>Figure 9.</b> The audit log: sequence, acting role, action and resource. Each row's hash covers the one before it."))

# 7 ---------------------------------------------------------------------------------------------
A(h2("7. Security and integrity", page=True))
A(p("""Security here is a set of independent layers, so that one mistake in one layer does not become a breach."""))
A(table(["Layer", "What it does"], [
    ["Tokens", "Signed, short-lived access tokens; refresh tokens stored only as hashes, single use, rotated each time. Reusing a spent refresh token revokes the whole family, the standard response to a stolen token."],
    ["Passwords and login", "BCrypt at cost 12. An unknown account and a wrong password return the same message, so the login cannot be used to find out who has an account. Five failures lock the account."],
    ["Roles", "Eight roles from one table, generating the access annotation on every controller and the navigation. Reads are restricted as well as writes, so a token holder cannot read wage rates or payouts unless their role allows it."],
    ["Own-record rule", "A worker sees only their own assignments. Another worker's task returns not found."],
    ["Tenant isolation", "Tenant-qualified queries in code, row-level security in the database, an unprivileged database role, and a live check that proves two organisations cannot see each other and that switching the protection off makes checks fail."],
    ["Rate limiting", "Two layers: the gateway, and a per-service Redis bucket keyed by organisation and user, applied after sign-in. It fails open if Redis is down, because refusing everything would be the outage it exists to prevent."],
    ["Audit", "A hash-chained log of privileged actions. Verified against the live database: an untouched chain reports intact, and a direct edit of one row reports the exact sequence that broke."],
    ["Server time", "Task start and stop times are taken on the server. A device clock is never trusted for pay."],
    ["Errors", "Problem documents with stable codes. Nothing internal, such as a table name or a stack trace, is returned to a caller."],
], ["nw", ""]))
A(box("Open security items, stated plainly", ul([
    "Services currently share one signing secret. Anyone who held it could mint a token for any organisation. The fix is asymmetric keys with a published key set, so only the identity service can sign.",
    "References from one service to another (for example an order to a farm) are not validated across the service boundary.",
    "The outbox table is not yet under row-level security.",
    "Owner database credentials are present in every service's environment, although only the migration tool needs them.",
    "The AI advisor needs an API key to use its vision model. Without one it runs on its rules and says so.",
]), warn=True))

# 8 ---------------------------------------------------------------------------------------------
A(h2("8. Running and operating it"))
A(table(["Task", "Command"], [
    ["Start a demonstration stack with data", "<code>scripts/demo.sh</code>"],
    ["See what is running and its memory", "<code>scripts/demo.sh status</code>"],
    ["Open every screen as every role and list the empty ones", "<code>scripts/demo.sh check</code>"],
    ["Top up demonstration data (safe to repeat)", "<code>scripts/demo.sh seed</code>"],
    ["Stop, keeping the data", "<code>scripts/demo.sh down</code>"],
    ["Start from empty", "<code>scripts/demo.sh reset</code>"],
    ["Build and test every service", "<code>tools/build-all.sh</code>"],
    ["End-to-end smoke test through the gateway", "<code>tools/smoke-test.sh</code>"],
    ["Prove the attendance to fraud event path", "<code>tools/event-path-test.sh</code>"],
    ["Prove tenant isolation on a live stack", "<code>scripts/tenant_isolation_check.py</code>"],
], ["", ""]))
A(table(["Address (demonstration)", "What it is"], [
    ["http://localhost:13000", "The web application"],
    ["http://localhost:18080", "The gateway (API)"],
    ["http://localhost:{port}/swagger-ui.html", "Each service's interactive API documentation"],
], ["nw", ""]))
A(p("""The demonstration stack needs roughly 7.4 GiB of memory and about six minutes from empty to seeded. A full stack with all {0} services needs about 12 GiB. Run one platform at a time on a small machine. Container images are built one at a time because building them all together exhausts memory.""".format(N_SERVICES)))

# 9 ---------------------------------------------------------------------------------------------
A(h2("9. Scale and performance"))
A(p("""SmartSeason has been measured, not assumed. The honest result is that it cannot yet carry ten million users, and the work to close that gap has been done in an order and then run for real on a throwaway cluster."""))
A(table(["Concern", "What was done", "Evidence"], [
    ["Connection exhaustion", "PgBouncer in transaction mode", "3 real database connections at idle for the whole platform; throughput 62 to 119 requests per second at 32 concurrent callers on a laptop"],
    ["Abuse and overload", "Gateway and per-service rate limits", "80 rapid writes: 66 allowed, 14 refused with a retry hint; 45 failed logins: 31 through, 14 refused"],
    ["Slow list pages", "Counts moved off the page query and cached for 30 seconds; rows are never cached", "Removes a full index walk that ran only to print &ldquo;1&ndash;25 of N&rdquo;"],
    ["Deep paging", "Keyset (cursor) pagination replaces offset", "Test harness written and run; data volume too small to show the offset curve, so inconclusive"],
    ["Elasticity", "Autoscaler and disruption budget for every deployment", "Manifests applied to a real cluster; two real manifest bugs found and fixed there"],
    ["Read load", "Read-replica routing", "Built; off until a replica host is configured"],
    ["Audit write race", "Unique index plus per-tenant lock", "50 concurrent appends produced a contiguous chain that verifies"],
], ["nw", "", ""]))
A(box("What the load test did and did not show", p("""A seven-minute ramp to 250 virtual users ran on a real cluster with one replica per service and the laptop-sized connection pool still set. Most traffic was refused by circuit breakers (70%) or the rate limiter (24%), which is those protections working as designed, and there were no pod restarts across every service for the whole run. It is evidence of resilience under overload. It is not a throughput claim for production hardware, which needs the pool and replica counts set for that hardware and a longer, larger run.""")))

# 10 --------------------------------------------------------------------------------------------
A(h2("10. Proven and not yet proven"))
A("<h3>Proven</h3>")
A(ul([
    f"All {N_SERVICES} services build and start; 559 tests passed with none failing at the last full count.",
    "Every screen loads on the demonstration stack: 94 entity screens as the administrator, and the six purpose-built screens as each of the eight roles, with none broken.",
    "Tenant isolation holds on a live stack across two organisations, and the check fails when the database protection is removed.",
    "The attendance to Kafka to fraud path works end to end: a deliberately fraudulent clock event produced a critical case, a payout hold and a raised risk band.",
    "The audit chain verifies when untouched and names the first broken record when tampered with.",
    "The M-Pesa flow works end to end against the mock gateway, including a repeated callback that changes nothing.",
]))
A("<h3>Not yet proven or not built</h3>")
A(ul([
    "<b>Live M-Pesa.</b> It needs Safaricom credentials, a public callback address and the provider's approval. Nothing real has moved.",
    "<b>Any customer.</b> No farm, cooperative or buyer has used it. Usability with real, tired, outdoor users is untested.",
    "<b>Billing of its own.</b> There is no subscription, metering or invoicing module. The platform cannot yet charge anyone for using it. See section 13.",
    "<b>SMS, USSD and WhatsApp delivery.</b> The notification service has templates and rules, but a gateway to a carrier is not connected, and the service is not in the demonstration stack.",
    "<b>Offline use.</b> Farms often have poor signal. Offline clock-in and task capture with later sync is described in the design, not built to work in a field.",
    "<b>Devices and telemetry.</b> The device, telemetry and automation services are not in the demonstration and have not been run against real sensors.",
    "<b>Dairy payouts.</b> Paying members from delivered milk, quality-based pricing, collection routes, member statements and dairy-board reporting are not built.",
    f"<b>Business logic in {len(CRUD_ONLY)} services.</b> Season, weather, workforce, catalogue, pricing, logistics, traceability, media, search, analytics, device registry and telemetry hold data correctly but do not yet act on it.",
    "<b>Scale.</b> Not shown to carry ten million users. The remaining work is a longer load test on production-sized infrastructure.",
    "<b>Compliance.</b> No data-protection registration, impact assessment or payment-provider arrangement has been made. See section 12.",
]))

# 11 --------------------------------------------------------------------------------------------
A(h2("11. How it makes money", page=True))
A(p("""SmartSeason holds three things that people already pay to solve: the cost of labour that was paid for but not worked, the friction of selling produce and being paid for it, and the proof that produce is what it is claimed to be. The streams below attach a price to each. Every price and volume is a proposal to be tested in a pilot. None of it is a forecast and no figure comes from a customer.""", "lead"))

A("<h3>Who pays</h3>")
A(table(["Customer", "Their problem", "What SmartSeason gives them"], [
    ["Commercial and medium farms with hired labour", "Paying for ghost workers, inflated hours, proxy clock-ins; no record of who did what", "Geofenced clock-in, server-timed tasks, fraud cases with evidence, payouts held on a flag"],
    ["Dairy cooperatives and societies", "Milk rejected at the cooler, disputes over litres and price, medicine residues in milk", "Per-cow records, delivery receipts, withdrawal alerts, rejection rate"],
    ["Produce buyers, aggregators and processors", "Uncertain quality and supply, slow reconciliation, payment risk", "Catalogue, grading, orders with escrow, proof of delivery, traceability"],
    ["Exporters and certification-dependent buyers", "Proving origin and certificates batch by batch", "Lineage graph, certificate evidence, QR pass"],
    ["Input suppliers and agrovets", "No view of what was bought, applied and with what result", "Input issue and consumption records, advisor reach"],
    ["Lenders, SACCOs and insurers (later)", "No reliable record of a farm's activity to lend or insure against", "Consented, structured activity and payment history"],
], ["", "", ""]))

A("<h3>Revenue streams</h3>")
A(table(["#", "Stream", "Basis (proposed)", "Built on", "Ready to charge?"], [
    ["1", "<b>Platform subscription</b> for farms with hired labour", "A monthly fee per farm plus a small fee per active worker", "Workforce, attendance, task, fraud", "Product yes, billing no"],
    ["2", "<b>Wage and settlement disbursement</b>", "A fee per payout, set below the cost of the cash-in-hand alternative", "Payout, payment, ledger", "Mock only; needs live M-Pesa and a licensed provider"],
    ["3", "<b>Marketplace commission</b> on orders", "A percentage of order value, taken in the ledger as commission income", "Order, payment, ledger, logistics", "Flow works on the mock; needs live M-Pesa"],
    ["4", "<b>Dairy cooperative module</b>", "A fee per active member per month, or per litre handled", "Farm (dairy), payout", "Records yes; member payouts not built"],
    ["5", "<b>Traceability and compliance packs</b>", "A fee per batch traced, or a flat annual pack for an exporter", "Traceability, media, audit", "Data model yes; buyer-facing view thin"],
    ["6", "<b>Crop advisor</b>", "Included in a plan, or metered per diagnosis for outside users", "Agronomy intelligence", "Works on rules; vision needs a key and its running cost"],
    ["7", "<b>Embedded finance referrals</b> (later)", "A referral fee on loans or cover originated from consented activity records", "Ledger, payment, analytics", "Not built; needs consent, data-protection and partner work"],
], ["n", "", "", "", ""]))

A(box("Why stream 1 comes first", p("""Wage fraud is the clearest pain with the shortest path to a yes. The owner can see the loss, the product directly shrinks it, and none of it depends on live M-Pesa, a marketplace of strangers or any licence. The first revenue should be a subscription from farms that pay people to work. Streams 2 and 3 follow once money is moving through a licensed provider, because that is where the volume and the risk both sit.""")))

A("<h3>An illustration, with every input an assumption</h3>")
A(p("""The table shows how the streams add up, so the pilot knows which numbers to measure. Every input is a stand-in. Replace each one with what the pilot actually shows before using any total."""))
A(table(["Stream", "Assumed inputs", "Monthly result"], [
    ["Subscription", "100 farms &times; (KES 1,500 per farm + 12 workers &times; KES 50)", "KES 210,000"],
    ["Disbursement", "1,200 workers &times; 4 payouts a month &times; KES 15 per payout", "KES 72,000"],
    ["Marketplace", "KES 5,000,000 of orders a month &times; 1.5%", "KES 75,000"],
    ["Dairy module", "400 members &times; KES 40 per month", "KES 16,000"],
    ["<b>Illustrative total</b>", "", "<b>KES 373,000</b>"],
], ["", "", "n"]))
A(p('<span class="small">This is arithmetic on assumed inputs, not a forecast. Costs are not shown: mobile-money charges on payouts, text messages, hosting for a stack that needs about 7.4 GiB even at demonstration size, the vision model, support and sales. A pilot must measure each before any margin is claimed.</span>'))

A("<h3>Pricing principles</h3>")
A(ul([
    "<b>Charge for the loss prevented, not for the software.</b> Price a farm's subscription against what it currently loses to unworked pay, so the owner can see it is cheaper than the problem.",
    "<b>Never hold the money.</b> Collect and pay out through a licensed provider; take a fee on the movement, not the balance.",
    "<b>Make the free tier a real trial.</b> A farm should see its first fraud flag or its first reconciled payout within the trial, because that is the moment it becomes a customer.",
    "<b>Keep the buyer side free at the start.</b> A marketplace needs buyers before sellers can be charged. Charge farmers first, on the products they already value.",
]))

# 12 --------------------------------------------------------------------------------------------
A(h2("12. Going to market"))
A("<h3>Positioning</h3>")
A(box("The one-sentence offer", p("""<b>Know who worked, what was done, and what was paid, on every farm task, and get paid for your produce through M-Pesa with a record nobody can quietly change.</b>""")))
A(p("""The first message is about labour cost and trust, because that is felt immediately. Produce, delivery and traceability are the second conversation, once a farm already trusts the system with its people."""))

A("<h3>Messages by audience</h3>")
A(table(["Audience", "Lead with", "Proof to show"], [
    ["Farm owner", "&ldquo;You pay for hours nobody worked. See which ones.&rdquo;", "The live board and a fraud case with its evidence; the role switch showing a manager cannot change pay"],
    ["Farm manager", "&ldquo;Assign, watch and verify work from your phone.&rdquo;", "A task started and stopped with the time recorded for them"],
    ["Worker", "&ldquo;Your hours are recorded fairly, and so is your pay.&rdquo;", "My work: simple Start and Stop"],
    ["Cooperative or dairy society", "&ldquo;Fewer rejected litres and no disputes over what was delivered.&rdquo;", "The dairy summary with withdrawal alerts"],
    ["Buyer or aggregator", "&ldquo;Know what you are buying before it arrives.&rdquo;", "Grading results, proof of delivery, QR trace"],
], ["nw", "", ""]))

A("<h3>Phases</h3>")
A(table(["Phase", "Goal", "What happens"], [
    ["0. Readiness", "Be safe to put in front of a customer", "Register for data protection, agree a payment arrangement with a licensed provider, set a data-sharing and terms text, switch live M-Pesa on in a test environment, add billing, and set the production keys"],
    ["1. Pilot", "Three to five farms in one area (the demonstration data is already Njoro and Nakuru)", "Free for a fixed period. Install, train workers, run for a full season. Measure: workers clocking in weekly, fraud cases raised and resolved, hours disputed, time saved on payroll"],
    ["2. First revenue", "Convert pilot farms to paid", "Subscription priced against the loss the pilot revealed. Reference customers and written results"],
    ["3. Money in motion", "Open disbursement and marketplace", "Live M-Pesa through the licensed provider, wage payouts with holds, first orders between pilot farms and a buyer"],
    ["4. Cooperatives and exporters", "Dairy module and traceability packs", "Member payouts for dairy, certificate packs for exporters"],
    ["5. Finance", "Referral revenue", "Only with consent, data-protection review and a lending or insurance partner"],
], ["nw", "", ""]))

A("<h3>Channels</h3>")
A(ul([
    "<b>Direct, farm by farm.</b> The first five customers come from visits, not advertising. The live demonstration with the role switch is the sales tool: sign in as a worker, then a manager, then the owner, in under ten minutes.",
    "<b>Cooperatives and societies.</b> One conversation reaches hundreds of farmers. Start with the dairy and produce societies around the pilot area.",
    "<b>Agrovets and input suppliers.</b> They already have the farmer's trust and weekly contact. Offer a referral fee for each farm they bring who stays subscribed.",
    "<b>County programmes and development partners.</b> Digitisation and food-security projects can fund onboarding groups of farms. This is slower, and suits phase 2 onward.",
    "<b>Mobile-money and agribusiness partners.</b> A relationship with a payment provider is needed anyway, and the same relationship can bring introductions.",
    "<b>Word of mouth and proof.</b> Written before-and-after numbers from pilot farms, with their consent, are the strongest asset.",
]))

A("<h3>What to measure</h3>")
A(table(["Measure", "Why it matters"], [
    ["Farms activated (first task started)", "Shows the product is actually used, not just signed up for"],
    ["Workers clocking in each week", "The core behaviour the whole fraud value depends on"],
    ["Fraud cases raised and resolved, and hours disputed", "The loss the product reveals, which prices the subscription"],
    ["Share of trial farms that convert to paid", "The only honest test of the price"],
    ["Value moving through the ledger and fees earned", "Opens the case for streams 2 and 3"],
    ["Support requests per farm", "Sets how many farms one person can look after"],
], ["", ""]))

A("<h3>Before charging, and before handling money</h3>")
A(box("To be confirmed with a lawyer before launch", ul([
    "Registration with the Office of the Data Protection Commissioner and a data-protection impact assessment. The platform holds worker identity, location, biometrics scores and pay.",
    "Whether holding or moving customer funds needs a payment-service licence, or whether a partnership with an already-licensed provider keeps the platform outside it. The safe design is to never hold the money.",
    "Safaricom's approval and onboarding for live collection and disbursement.",
    "Consent text for workers, because clock-in location and biometric scores are personal data, and for any use of activity data in finance.",
    "Terms of service, a privacy notice, and a clear statement that fraud flags are for human review and are not findings of guilt.",
]), warn=True))

# 13 --------------------------------------------------------------------------------------------
A(h2("13. What to build next to earn"))
A(p("""In the order that gets to first revenue fastest. Each item is something the platform cannot yet do and a customer would need."""))
A(table(["Priority", "Build", "Why", "Unlocks"], [
    ["1", "Billing: plans, metering of farms and active workers, invoices, and payment of the platform's own fees", "There is no way to charge anyone today", "Stream 1"],
    ["2", "Offline clock-in and task capture with safe later sync", "Farms have poor signal; without it workers cannot use it on the day", "Every farm"],
    ["3", "SMS and WhatsApp delivery through a carrier, and add the notification service to the demonstration", "Workers and owners need reminders and alerts in English and Swahili", "Adoption"],
    ["4", "Live M-Pesa in a test environment, then production", "Collection and payouts are the money in motion", "Streams 2 and 3"],
    ["5", "Asymmetric signing keys with a published key set", "Closes the largest open security item before real customers", "Trust"],
    ["6", "Business behaviour in workforce and season services (wage calculation from attendance, stage alerts)", "Turns recorded data into pay and prompts", "Retention"],
    ["7", "Dairy member payouts, quality-based pricing, member statements", "Completes the cooperative module", "Stream 4"],
    ["8", "Buyer-facing trace page from the QR pass", "Makes the exporter pack visible to the person who pays for it", "Stream 5"],
    ["9", "Longer load test on production-sized infrastructure", "Replaces &ldquo;should scale&rdquo; with a measured number", "Larger customers"],
], ["n", "", "", ""]))

# appendices ------------------------------------------------------------------------------------
A(h2("Appendix A. Service index", page=True))
rows = []
for s in catalogue.SERVICES:
    port, db, _ = README.get(s["name"], (str(s["port"]), s["db"], ""))
    rows.append([s["name"], port, db, str(len(s["entities"])), "Logic" if s["name"] in DEPTH else "Generated", "No" if s["name"] in NOT_IN_DEMO else "Yes"])
A(table(["Service", "Port", "Database", "Aggregates", "Depth", "In the demo"], rows, ["", "nw", "", "n", "", ""], long=True))

A(h2("Appendix B. Demonstration accounts"))
A(p("""Created through the platform's own team endpoint, so passwords are hashed exactly as a real sign-up would hash them. On a demonstration stack the sign-in page also offers a &ldquo;Sign in as&rdquo; picker. The password is generated for each demonstration stack and is not printed here."""))
A(table(["Email", "Role", "Person"], [
    ["demo@smartseason.local", "Administrator", "Demo Administrator"],
    ["farmer@smartseason.local", "Farmer (owner)", "Wanjiku Kamau"],
    ["manager@smartseason.local", "Manager", "Brian Otieno"],
    ["agronomist@smartseason.local", "Agronomist", "Dr. Faith Chebet"],
    ["store@smartseason.local", "Storekeeper", "Samuel Mutua"],
    ["finance@smartseason.local", "Finance", "Esther Njeri"],
    ["buyer@smartseason.local", "Buyer", "Hassan Abdi"],
    ["amina@smartseason.local", "Worker", "Amina Wanjiru"],
    ["joseph@smartseason.local", "Worker", "Joseph Kiptoo"],
], ["", "", ""]))

A(h2("Appendix C. Glossary"))
A(table(["Term", "Meaning"], [
    ["Tenant", "One organisation. All its data is invisible to every other organisation."],
    ["Aggregate", "A persisted record type, such as a farm, a work order or a payment intent."],
    ["Saga", "A multi-step business process that undoes completed steps, in reverse, if a later step fails."],
    ["Outbox", "A table written in the same transaction as a change, so an event is published only if the change committed."],
    ["Row-level security", "A database rule that hides rows belonging to another tenant even from a query that forgot to filter."],
    ["Hash chain", "A log in which each record's fingerprint includes the previous record's, so editing one breaks all later ones."],
    ["Keyset pagination", "Paging by &ldquo;records after this one&rdquo; rather than &ldquo;skip N records&rdquo;; its cost does not grow with depth."],
    ["STK push", "The M-Pesa prompt that appears on a customer's phone asking them to approve a payment."],
    ["B2C", "Business to customer: money sent from the business to a phone, used for wages and settlements."],
    ["Withdrawal period", "The days after a medicine treatment during which a cow's milk must not be sold."],
    ["NDVI", "A satellite measure of how green and vigorous a crop is."],
    ["KYC", "Know your customer: identity checks on a person or organisation."],
], ["nw", ""]))

A("</body></html>")

out_html = os.path.join(HERE, "SmartSeason-Documentation.html")
open(out_html, "w", encoding="utf-8").write("\n".join(parts))
print("wrote", out_html)

out_pdf = os.path.join(HERE, "SmartSeason-Documentation.pdf")
subprocess.run(["google-chrome", "--headless", "--disable-gpu", "--no-sandbox", f"--print-to-pdf={out_pdf}", "--no-pdf-header-footer", f"file://{out_html}"],
               check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print("wrote", out_pdf)
