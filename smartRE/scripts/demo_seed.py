#!/usr/bin/env python3
"""
Seeds a demo-sized SmartRE through the public API, so every screen has something real on it.

Everything goes through the gateway exactly as a browser would: register, log in, upload,
verify, list, let, invoice, pay. The only things done in SQL are the two that no API allows
on purpose (promoting an account to ADMIN, and reading nothing else). If this script runs to
the end, the platform works end to end; if it fails, it fails where a real user would.

What it builds (all names, numbers and plots are invented):
  * an admin, four landlords (one with a 42-unit portfolio), four sellers, four buyers and six
    tenants with their own logins;
  * 20+ properties with generated photographs, taken through the real identity and ownership
    verification pipeline to ACTIVE - plus a few left mid-pipeline, rejected or suspended so
    the admin queues are not empty;
  * units, tenants and leases in every state; six months of rent invoices issued by the real
    invoicing job, then paid, part-paid, overdue or written off; maintenance tickets;
  * viewings in every state, buyer deposits into escrow, escrow releases and a refund through
    the mock M-Pesa, reviews, fraud reports and notifications.

Idempotent: a second run finds what exists and fills in only what is missing.

Environment: GATEWAY (default http://localhost:8480), SEED_PASSWORD, COMPOSE_CMD (how to reach
the user database for the one SQL step), SEED_ONLY=step,step to run a subset while developing.
"""
from __future__ import annotations

import datetime as dt
import io
import json
import os
import random
import shlex
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import zlib
import struct

GATEWAY = os.environ.get("GATEWAY", "http://localhost:8480").rstrip("/")
PASSWORD = os.environ.get("SEED_PASSWORD", "SmartRE-Demo-2026!")
COMPOSE = os.environ.get(
    "COMPOSE_CMD",
    "docker compose --env-file .env.demo -f docker-compose.yml -f docker-compose.demo.yml",
)
ONLY = {s for s in os.environ.get("SEED_ONLY", "").split(",") if s}
TODAY = dt.date.today()
RNG = random.Random(20260930)  # fixed seed: the demo looks the same every time

warnings: list[str] = []


def say(msg: str) -> None:
    print(f"\n\033[1m{msg}\033[0m", flush=True)


def ok(msg: str) -> None:
    print(f"  \033[32m✓\033[0m {msg}", flush=True)


def warn(msg: str) -> None:
    warnings.append(msg)
    print(f"  \033[33m!\033[0m {msg}", flush=True)


def want(step: str) -> bool:
    return not ONLY or step in ONLY


# ----------------------------------------------------------------------------- HTTP ---------

class Response:
    def __init__(self, status: int, body):
        self.status, self.body = status, body

    @property
    def ok(self) -> bool:
        return 200 <= self.status < 300

    def __getitem__(self, key):
        return self.body[key] if isinstance(self.body, dict) else None

    def error(self) -> str:
        if isinstance(self.body, dict):
            return str(self.body.get("error") or self.body.get("message") or self.body)
        return str(self.body)[:200]


def request(method: str, path: str, body=None, token: str | None = None, *, raw: bytes | None = None,
            content_type: str = "application/json", retries: int = 8) -> Response:
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    headers = {"Content-Type": content_type} if data is not None else {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    for attempt in range(retries):
        req = urllib.request.Request(GATEWAY + path, method=method, data=data, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=60) as f:
                payload = f.read()
                return Response(f.status, json.loads(payload) if payload else None)
        except urllib.error.HTTPError as e:
            payload = e.read()
            if e.code in (429, 502, 503, 504) and attempt < retries - 1:
                wait = float(e.headers.get("Retry-After") or 1.2)
                time.sleep(min(max(wait, 1.0), 8.0))
                continue
            try:
                return Response(e.code, json.loads(payload))
            except Exception:
                return Response(e.code, payload[:300].decode("utf-8", "replace"))
        except (urllib.error.URLError, ConnectionError, TimeoutError):
            if attempt < retries - 1:
                time.sleep(2)
                continue
            raise
    return Response(599, "gave up")


def multipart(path: str, token: str, field: str, filename: str, content: bytes, fields: dict) -> Response:
    boundary = "----smartre" + uuid.uuid4().hex
    out = io.BytesIO()
    for k, v in fields.items():
        out.write(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    out.write(
        f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
        f"Content-Type: image/jpeg\r\n\r\n".encode()
    )
    out.write(content)
    out.write(f"\r\n--{boundary}--\r\n".encode())
    return request("POST", path, token=token, raw=out.getvalue(),
                   content_type=f"multipart/form-data; boundary={boundary}")


# --------------------------------------------------------------------------- pictures -------

try:  # Pillow gives nicer pictures; without it a plain PNG gradient is written instead.
    from PIL import Image, ImageDraw, ImageFont  # type: ignore
    HAVE_PIL = True
except Exception:  # pragma: no cover
    HAVE_PIL = False

PALETTES = [
    ((242, 201, 76), (201, 162, 39)), ((122, 162, 189), (52, 92, 122)), ((196, 140, 108), (142, 84, 58)),
    ((150, 190, 150), (74, 120, 84)), ((188, 160, 196), (112, 84, 130)), ((224, 160, 160), (166, 88, 88)),
    ((170, 200, 210), (90, 130, 146)), ((214, 190, 140), (150, 122, 70)),
]


def _plain_png(w: int, h: int, top, bottom) -> bytes:
    rows = []
    for y in range(h):
        t = y / max(h - 1, 1)
        px = bytes(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) * w
        rows.append(b"\x00" + px)
    raw = zlib.compress(b"".join(rows), 6)

    def chunk(tag: bytes, payload: bytes) -> bytes:
        body = tag + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", raw) + chunk(b"IEND", b"")


def picture(label: str, n: int, kind: str = "house") -> tuple[bytes, str]:
    """A simple, honest illustration (not a photograph), unique per label so duplicate-document
    detection never mistakes two of them for the same file."""
    top, bottom = PALETTES[(hash_int(label) + n) % len(PALETTES)]
    if not HAVE_PIL:
        return _plain_png(600, 400, top, bottom), "png"
    w, h = 1200, 800
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    rng = random.Random(hash_int(label) * 31 + n)
    d.rectangle([0, int(h * 0.78), w, h], fill=(86, 120, 72))
    if kind == "land":
        for i in range(9):
            x = rng.randint(40, w - 40)
            d.ellipse([x - 60, 430 + rng.randint(-20, 20), x + 60, 560], fill=(56, 96, 52))
            d.rectangle([x - 8, 540, x + 8, 640], fill=(92, 64, 44))
    else:
        floors = rng.randint(2, 6) if kind != "house" else 2
        bw = rng.randint(520, 760)
        bx = (w - bw) // 2
        body_h = floors * 92
        top_y = int(h * 0.78) - body_h
        shade = (236 - rng.randint(0, 50), 226 - rng.randint(0, 50), 210 - rng.randint(0, 60))
        d.rectangle([bx, top_y, bx + bw, int(h * 0.78)], fill=shade, outline=(90, 80, 70), width=3)
        if kind == "house":
            d.polygon([(bx - 30, top_y), (bx + bw // 2, top_y - 130), (bx + bw + 30, top_y)], fill=(140, 70, 54))
        for f in range(floors):
            for c in range(max(3, bw // 120)):
                x0 = bx + 34 + c * (bw - 68) // max(3, bw // 120)
                y0 = top_y + 18 + f * 92
                d.rectangle([x0, y0, x0 + 52, y0 + 56], fill=(120 + rng.randint(0, 50), 170, 210),
                            outline=(70, 70, 80), width=2)
        d.rectangle([bx + bw // 2 - 30, int(h * 0.78) - 70, bx + bw // 2 + 30, int(h * 0.78)], fill=(96, 60, 40))
    d.rectangle([24, 24, w - 24, 92], fill=(0, 0, 0, 0))
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 34)
    except Exception:
        font = ImageFont.load_default()
    d.text((40, 36), label, fill=(255, 255, 255), font=font)
    d.text((40, h - 52), f"SmartRE demo illustration {n + 1}", fill=(255, 255, 255), font=font)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=78)
    return buf.getvalue(), "jpg"


def hash_int(text: str) -> int:
    return sum((i + 1) * ord(c) for i, c in enumerate(text)) % 10_007


def upload(token: str, category: str, label: str, n: int = 0, kind: str = "house") -> str | None:
    content, ext = picture(f"{label}", n, kind)
    # A tiny random tag makes every upload byte-unique even for identical labels.
    content += b"\x00" + uuid.uuid4().bytes if ext == "jpg" else b""
    r = multipart("/api/documents/upload", token, "file", f"{uuid.uuid4().hex[:8]}.{ext}", content, {"category": category})
    if not r.ok:
        warn(f"upload {category}/{label} failed: {r.error()}")
        return None
    return r["url"]


# --------------------------------------------------------------------------- accounts -------

tokens: dict[str, str] = {}
user_ids: dict[str, str] = {}
user_roles: dict[str, str] = {}
_last_auth = 0.0


def _pace() -> None:
    """The gateway allows one sign-in per second (burst 5). Stay just under it."""
    global _last_auth
    wait = 1.15 - (time.time() - _last_auth)
    if wait > 0:
        time.sleep(wait)
    _last_auth = time.time()


def account(email: str, name: str, role: str, phone: str) -> str | None:
    if email in tokens:
        return tokens[email]
    _pace()
    request("POST", "/api/auth/register",
            {"fullName": name, "email": email, "password": PASSWORD, "phone": phone, "role": role})
    _pace()
    r = request("POST", "/api/auth/login", {"email": email, "password": PASSWORD})
    if not r.ok:
        warn(f"could not sign in {email}: {r.error()}")
        return None
    tokens[email] = r["token"]
    user_ids[email] = r["userId"]
    user_roles[email] = r["role"]
    return tokens[email]


def relogin(email: str) -> None:
    """A role changed in the database only shows in a fresh token."""
    _pace()
    r = request("POST", "/api/auth/login", {"email": email, "password": PASSWORD})
    if r.ok:
        tokens[email] = r["token"]
        user_roles[email] = r["role"]


def sql(database: str, statement: str, service: str = "user-db") -> bool:
    cmd = COMPOSE.split() + ["exec", "-T", service, "psql", "-U", "postgres", "-d", database, "-c", statement]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    except Exception as e:  # pragma: no cover
        warn(f"sql step failed to start: {e}")
        return False
    if out.returncode != 0:
        warn(f"sql failed ({statement[:60]}): {out.stderr.strip()[:160]}")
        return False
    return True


def wait_gateway() -> None:
    say("Waiting for the gateway")
    for i in range(1, 91):
        try:
            with urllib.request.urlopen(GATEWAY + "/actuator/health", timeout=5) as f:
                if b'"UP"' in f.read():
                    ok(f"gateway UP after ~{i * 5}s")
                    return
        except Exception:
            pass
        time.sleep(5)
    sys.exit("gateway never became healthy")


PEOPLE = {
    # email: (name, role, phone)
    "demo.admin@smartre.test": ("Amani Mwangi", "ADMIN", "+254700100001"),
    "demo.landlord@smartre.test": ("Grace Njeri", "LANDLORD", "+254700100002"),
    "wanjiru.properties@smartre.test": ("Wanjiru Properties Ltd", "LANDLORD", "+254700100003"),
    "otieno.estates@smartre.test": ("Otieno Estates", "LANDLORD", "+254700100004"),
    "demo.seller@smartre.test": ("Kamau Realtors", "SELLER", "+254700100005"),
    "faith.achieng@smartre.test": ("Faith Achieng", "SELLER", "+254700100006"),
    "peter.mutua@smartre.test": ("Peter Mutua", "SELLER", "+254700100007"),
    "lakeside.lands@smartre.test": ("Lakeside Lands Co-op", "SELLER", "+254700100008"),
    "demo.buyer@smartre.test": ("Peter Otieno", "BUYER", "+254711200001"),
    "lucy.wambui@smartre.test": ("Lucy Wambui", "BUYER", "+254711200002"),
    "brian.kiptoo@smartre.test": ("Brian Kiptoo", "BUYER", "+254711200003"),
    "noor.abdi@smartre.test": ("Noor Abdi", "BUYER", "+254711200004"),
    # tenants with their own logins (the picker on the login page knows david.kimani)
    "david.kimani@example.co.ke": ("David Kimani", "TENANT", "+254733444555"),
    "mary.wanjiku@example.co.ke": ("Mary Wanjiku", "TENANT", "+254722111222"),
    "aisha.hassan@example.co.ke": ("Aisha Hassan", "TENANT", "+254745666777"),
    "joseph.mwangi@example.co.ke": ("Joseph Mwangi", "TENANT", "+254712300001"),
    "esther.nyambura@example.co.ke": ("Esther Nyambura", "TENANT", "+254712300002"),
    "samuel.oduor@example.co.ke": ("Samuel Oduor", "TENANT", "+254712300003"),
}
# Kept from the original seed so the old README and scripts still work.
LEGACY = {"landlord@smartre.demo": ("Grace Njeri (legacy)", "LANDLORD", "+254700000001"),
          "buyer@smartre.demo": ("Peter Otieno (legacy)", "BUYER", "+254700000002")}


def step_accounts() -> None:
    say("Accounts")
    for email, (name, role, phone) in {**PEOPLE, **LEGACY}.items():
        # Anyone can register as BUYER/SELLER/LANDLORD/TENANT; ADMIN cannot, on purpose.
        account(email, name, "BUYER" if role == "ADMIN" else role, phone)
        if email in tokens:
            ok(f"{role:<8} {email}")
    admin = "demo.admin@smartre.test"
    if user_roles.get(admin) != "ADMIN":
        if sql("user_db", f"UPDATE users SET role='ADMIN' WHERE email='{admin}'"):
            relogin(admin)
            ok("promoted demo.admin@smartre.test to ADMIN (the one step no API offers)")
    for email, (name, role, phone) in PEOPLE.items():
        if email not in tokens or role in ("ADMIN",):
            continue
        if user_roles.get(email) != role:
            sql("user_db", f"UPDATE users SET role='{role}' WHERE email='{email}'")
            relogin(email)


def tok(email: str) -> str:
    if email not in tokens:
        name, role, phone = {**PEOPLE, **LEGACY}[email]
        account(email, name, "BUYER" if role == "ADMIN" else role, phone)
    return tokens[email]


def profile(email: str, **fields) -> None:
    r = request("PUT", "/api/users/me", fields, tok(email))
    if not r.ok:
        warn(f"profile update for {email}: {r.error()}")


# ------------------------------------------------------------------------ verification -------

IDENTITY_DOCS = ["NATIONAL_ID_FRONT", "NATIONAL_ID_BACK", "KRA_PIN_CERTIFICATE", "SELFIE_WITH_ID"]
OWNERSHIP_DOCS = ["CONSENT_TO_TRANSFER", "LAND_RENT_CLEARANCE", "LAND_SEARCH_CERTIFICATE",
                  "RATES_CLEARANCE_CERTIFICATE", "SURVEY_MAP", "TITLE_DEED", "TRANSFER_FORM_RL1"]
LEGAL_FLAGS = {k: True for k in (
    "lcAdvocateStampPresent", "lcAdvocateSignaturePresent", "lcCommissionerOathsPresent", "lcOfficialSealPresent",
    "lcOwnerSignaturePresent", "lcWitnessSignaturesPresent", "lcDatePresent", "lcParcelNumberMatches",
    "lcOriginalDocumentConfirmed", "humanLegalApproved")}


def identity(email: str, outcome: str) -> None:
    """outcome: APPROVED | PENDING (submitted, waiting in the queue) | REJECTED | DRAFT"""
    t = tok(email)
    me = request("GET", "/api/verification/identity/me", None, t)
    if me.ok and isinstance(me.body, dict) and me["status"] in ("APPROVED", "HUMAN_REVIEW", "REJECTED"):
        ok(f"identity {email}: already {me['status']}")
        return
    started = request("POST", "/api/verification/identity/start", {}, t)
    if not started.ok and not me.ok:
        warn(f"identity start {email}: {started.error()}")
        return
    if outcome == "DRAFT":
        ok(f"identity {email}: started, left as a draft")
        return
    for cat in IDENTITY_DOCS:
        url = upload(t, "identity", f"{cat} {email}", 0, "house")
        if url:
            r = request("POST", "/api/verification/identity/documents",
                        {"documentCategory": cat, "documentUrl": url, "fileSizeBytes": 40000, "mimeType": "image/jpeg"}, t)
            if not r.ok and r.status != 409:
                warn(f"identity doc {cat} for {email}: {r.error()}")
    sub = request("POST", "/api/verification/identity/submit", {}, t)
    if not sub.ok:
        warn(f"identity submit {email}: {sub.error()}")
        return
    if outcome == "PENDING":
        ok(f"identity {email}: submitted, waiting for an admin")
        return
    vid = sub["id"]
    decision = "APPROVED" if outcome == "APPROVED" else "REJECTED"
    body = {"decision": decision, "notes": "Demo review"}
    if decision == "REJECTED":
        body["notes"] = "The photograph on the ID does not match the selfie. Please resubmit clear images."
    r = request("PUT", f"/api/verification/identity/admin/{vid}/review", body, tok("demo.admin@smartre.test"))
    (ok if r.ok else warn)(f"identity {email}: {decision}" if r.ok else f"identity review {email}: {r.error()}")


def ownership(email: str, property_id: str, title: str, county: str, outcome: str, ptype: str = "FREEHOLD") -> None:
    """outcome: APPROVED | PENDING (submitted, in the admin queue) | STARTED"""
    t = tok(email)
    mine = [v for v in page(request("GET", "/api/verification/ownership/me", None, t)) if v.get("propertyId") == property_id]
    existing = mine[0] if mine else None
    if existing and existing["status"] in ("APPROVED", "HUMAN_REVIEW", "LEGAL_REVIEW", "MINISTRY_LANDS_CHECK", "REJECTED"):
        return
    if existing and existing["status"] == "DRAFT":
        started = Response(200, existing)  # an earlier run got this far; carry on from the draft
    else:
        # Derived from the title, not a random stream, so a re-run asks for the same parcel.
        h = hash_int(title)
        parcel = f"{county.upper()}/BLOCK {h % 40 + 1}/{h % 900 + 100}"
        started = request("POST", "/api/verification/ownership/start",
                          {"propertyId": property_id, "propertyType": ptype, "county": county, "parcelNumber": parcel,
                           "titleDeedNumber": f"IR {100000 + h * 37}", "lrNumber": f"LR {1000 + h}/{h % 99 + 1}"}, t)
    if not started.ok:
        warn(f"ownership start {title}: {started.error()}")
        return
    if outcome == "STARTED":
        return
    oid = started["id"]
    # What is mandatory depends on the kind of title (agricultural land also needs Land Control
    # Board consent), so ask the pipeline rather than hard-coding a list.
    have = {d["documentCategory"] for d in (started.body.get("documents") or [])}
    needed = [d["documentCategory"] for d in (started.body.get("allRequiredDocuments") or []) if d.get("isMandatory")]
    for cat in needed or OWNERSHIP_DOCS:
        if cat in have:
            continue
        url = upload(t, "ownership", f"{cat} {title}", 0, "house")
        if url:
            r = request("POST", f"/api/verification/ownership/{oid}/documents",
                        {"documentCategory": cat, "documentUrl": url, "fileSizeBytes": 40000, "mimeType": "image/jpeg"}, t)
            if not r.ok and r.status != 409:
                warn(f"ownership doc {cat} for {title}: {r.error()}")
    sub = request("POST", f"/api/verification/ownership/{oid}/submit", {}, t)
    if not sub.ok:
        warn(f"ownership submit {title}: {sub.error()}")
        return
    if outcome == "PENDING":
        return
    a = tok("demo.admin@smartre.test")
    request("PUT", f"/api/verification/ownership/admin/{oid}/ministry-check?ministryConfirmed=true&notes=Confirmed+with+the+Ministry+of+Lands", None, a)
    request("PUT", f"/api/verification/ownership/admin/{oid}/encumbrance-check?encumbranceClear=true&notes=No+encumbrances+on+the+search", None, a)
    current = request("GET", f"/api/verification/ownership/property/{property_id}", None, t)
    for d in (current.body or {}).get("documents", []):
        request("PUT", f"/api/verification/ownership/admin/{oid}/legal-check",
                {"documentId": d["id"], **LEGAL_FLAGS, "humanReviewNotes": "Checked against the register"}, a)
    r = request("PUT", f"/api/verification/ownership/admin/{oid}/final-decision",
                {"decision": "APPROVED", "notes": "Demo review", "ministryLandsConfirmed": True, "encumbranceClear": True}, a)
    if not r.ok:
        warn(f"ownership decision {title}: {r.error()}")


# ------------------------------------------------------------------------ properties --------

# (title, propertyType, listingType, county, subCounty, city, price, beds, baths, area, year, kind)
SELLER_LISTINGS = {
    "demo.seller@smartre.test": [
        ("Four-bedroom maisonette, Karen", "HOUSE", "SALE", "Nairobi", "Lang'ata", "Nairobi", 38500000, 4, 4, 310, 2016, "house"),
        ("Modern 3BR apartment, Kilimani", "APARTMENT", "SALE", "Nairobi", "Kilimani", "Nairobi", 14800000, 3, 3, 152, 2021, "tower"),
        ("Quarter-acre plot, Runda Mhasibu", "LAND", "SALE", "Nairobi", "Kiambaa", "Nairobi", 21000000, None, None, 1012, None, "land"),
        ("Ruiru 3BR bungalow with SQ", "HOUSE", "SALE", "Kiambu", "Ruiru", "Ruiru", 9800000, 3, 2, 180, 2019, "house"),
        ("Commercial block, Thika Road", "COMMERCIAL", "SALE", "Kiambu", "Juja", "Thika", 64000000, None, 6, 780, 2014, "tower"),
        ("Executive townhouse, Lavington", "TOWNHOUSE", "SALE", "Nairobi", "Dagoretti", "Nairobi", 27500000, 4, 4, 265, 2020, "house"),
    ],
    "faith.achieng@smartre.test": [
        ("Nyali beachfront apartment", "APARTMENT", "SALE", "Mombasa", "Nyali", "Mombasa", 16900000, 3, 3, 160, 2018, "tower"),
        ("Milimani 2BR flat, Kisumu", "APARTMENT", "SALE", "Kisumu", "Kisumu Central", "Kisumu", 7400000, 2, 2, 98, 2017, "tower"),
        ("Half-acre plot, Nakuru Milimani", "LAND", "SALE", "Nakuru", "Nakuru East", "Nakuru", 8200000, None, None, 2023, None, "land"),
        ("Diani villa near the beach", "HOUSE", "SALE", "Kwale", "Msambweni", "Diani", 31000000, 5, 5, 420, 2015, "house"),
    ],
}
SELLER_PENDING = {  # left in the verification queue on purpose
    "lakeside.lands@smartre.test": [
        ("Lakeview acre, Naivasha", "LAND", "SALE", "Nakuru", "Naivasha", "Naivasha", 12500000, None, None, 4047, None, "land"),
        ("Kitengela 4BR family house", "HOUSE", "SALE", "Kajiado", "Kitengela", "Kitengela", 11200000, 4, 3, 220, 2018, "house"),
    ],
}

properties: dict[str, dict] = {}  # title -> {id, sellerEmail, sellerId, price}


def my_properties(email: str) -> dict[str, dict]:
    return {p["title"]: p for p in fetch_all("/api/properties/my", tok(email))}


def create_property(email: str, row, manage_only: bool = False) -> dict | None:
    title, ptype, ltype, county, sub, city, price, beds, baths, area, year, kind = row
    have = my_properties(email)
    if title in have:
        properties[title] = {**have[title], "sellerEmail": email}
        return properties[title]
    t = tok(email)
    urls = [] if manage_only else [u for u in (upload(t, "property_image", title, n, kind) for n in range(3)) if u]
    body = {"title": title, "description": describe(title, ptype, county, sub, beds),
            "propertyType": ptype, "listingType": ltype, "county": county, "subCounty": sub, "city": city,
            "locationDescription": f"{sub}, {county}. Close to main road and shopping.",
            "latitude": round(-1.29 + RNG.uniform(-0.25, 0.1), 5), "longitude": round(36.82 + RNG.uniform(-0.3, 0.3), 5),
            "price": price, "imageUrls": urls, "manageOnly": manage_only}
    for k, v in (("bedrooms", beds), ("bathrooms", baths), ("areaSqm", area), ("yearBuilt", year)):
        if v is not None:
            body[k] = v
    r = request("POST", "/api/properties", body, t)
    if not r.ok:
        warn(f"property '{title}': {r.error()}")
        return None
    properties[title] = {**r.body, "sellerEmail": email}
    return properties[title]


def describe(title: str, ptype: str, county: str, sub: str, beds) -> str:
    feel = {"HOUSE": "a well-kept family home", "APARTMENT": "a bright, secure apartment",
            "LAND": "a level, titled plot with road access", "COMMERCIAL": "a high-footfall commercial property",
            "TOWNHOUSE": "a gated townhouse with its own parking"}.get(ptype, "a well-located property")
    return (f"{title} is {feel} in {sub}, {county}. Verified title, clear land-rent and rates, "
            f"and a seller whose identity SmartRE has checked. Viewings by appointment; deposits are held in M-Pesa escrow "
            f"until the transfer documents are signed.")


def step_listings() -> None:
    say("Verification and listings")
    identity("demo.seller@smartre.test", "APPROVED")
    identity("faith.achieng@smartre.test", "APPROVED")
    identity("lakeside.lands@smartre.test", "APPROVED")
    identity("peter.mutua@smartre.test", "REJECTED")
    identity("demo.landlord@smartre.test", "APPROVED")
    identity("wanjiru.properties@smartre.test", "APPROVED")
    identity("otieno.estates@smartre.test", "PENDING")
    for email, rows in SELLER_LISTINGS.items():
        for row in rows:
            p = create_property(email, row)
            if p:
                ownership(email, p["id"], row[0], row[3], "APPROVED", "AGRICULTURAL" if row[1] == "LAND" else "FREEHOLD")
                ok(f"listed  {row[0]}")
    for email, rows in SELLER_PENDING.items():
        for row in rows:
            p = create_property(email, row)
            if p:
                ownership(email, p["id"], row[0], row[3], "PENDING", "AGRICULTURAL" if row[1] == "LAND" else "FREEHOLD")
                ok(f"queued  {row[0]}  (waiting for an admin)")
    p = create_property("peter.mutua@smartre.test", ("Machakos 3BR bungalow", "HOUSE", "SALE", "Machakos", "Athi River", "Athi River", 7800000, 3, 2, 170, 2017, "house"))
    if p:
        ok("draft   Machakos 3BR bungalow  (seller identity was rejected)")
    # A suspension, so the admin listings screen shows more than one state.
    sus = properties.get("Milimani 2BR flat, Kisumu")
    if sus and sus.get("status") != "SUSPENDED":
        r = request("PUT", f"/api/properties/admin/{sus['id']}/suspend", {"reason": "Seller reported a duplicate listing; under review"}, tok("demo.admin@smartre.test"))
        if not r.ok:
            r = request("PUT", f"/api/properties/admin/{sus['id']}/suspend?reason=Duplicate+listing+under+review", None, tok("demo.admin@smartre.test"))
        (ok if r.ok else warn)("suspended Milimani 2BR flat, Kisumu" if r.ok else f"suspend: {r.error()}")
    time.sleep(3)  # let the verification events reach property-service


# ------------------------------------------------------------------------------ rentals -----

# (property title, landlord email, type, listed?, [(unit label, beds, baths, rent)])
def block(prefix: str, floors: int, per_floor: int, rent_by_beds):
    out = []
    for f in range(1, floors + 1):
        for u in range(1, per_floor + 1):
            beds = [1, 2, 2, 3][(f + u) % 4]
            out.append((f"{prefix}{f}{chr(64 + u)}", beds, 1 if beds == 1 else 2, rent_by_beds[beds]))
    return out


RENTALS = [
    ("Riverside Court, Westlands", "demo.landlord@smartre.test", "APARTMENT", True, "Nairobi", "Westlands",
     block("R", 4, 2, {1: 55000, 2: 85000, 3: 120000}), 98000),
    ("Kilimani Heights", "demo.landlord@smartre.test", "APARTMENT", True, "Nairobi", "Kilimani",
     block("K", 4, 3, {1: 48000, 2: 72000, 3: 105000}), 72000),
    ("Westlands Towers", "wanjiru.properties@smartre.test", "APARTMENT", False, "Nairobi", "Westlands",
     block("W", 6, 3, {1: 60000, 2: 92000, 3: 135000}), 92000),
    ("Ruaka Gardens", "wanjiru.properties@smartre.test", "APARTMENT", True, "Kiambu", "Ruaka",
     block("G", 7, 2, {1: 28000, 2: 42000, 3: 58000}), 42000),
    ("Syokimau Villas", "wanjiru.properties@smartre.test", "HOUSE", False, "Machakos", "Syokimau",
     [(f"V{i}", 3 if i % 3 else 4, 3, 65000 if i % 3 else 82000) for i in range(1, 11)], 65000),
    ("Otieno Court, Kisumu", "otieno.estates@smartre.test", "APARTMENT", False, "Kisumu", "Milimani",
     [("O1", 2, 1, 32000), ("O2", 2, 1, 32000), ("O3", 3, 2, 45000)], 32000),
]

units: dict[str, dict] = {}  # "property/label" -> unit response


def step_units() -> None:
    say("Rental properties and units")
    for title, email, ptype, listed, county, sub, unit_rows, headline in RENTALS:
        row = (title, ptype, "RENT", county, sub, county, headline, 2, 2, 90, 2019, "tower")
        if listed:
            p = create_property(email, row, manage_only=False)
            if p:
                ownership(email, p["id"], title, county, "APPROVED")
        else:
            p = create_property(email, row, manage_only=True)
        if not p:
            continue
        t = tok(email)
        existing = {u["label"]: u for u in fetch_all(f"/api/units/property/{p['id']}", t)}
        created = 0
        for label, beds, baths, rent in unit_rows:
            if label in existing:
                units[f"{title}/{label}"] = existing[label]
                continue
            r = request("POST", "/api/units", {"propertyId": p["id"], "label": label, "unitType": "APARTMENT" if ptype != "HOUSE" else "HOUSE",
                                               "bedrooms": beds, "bathrooms": baths, "sizeSqm": 38.0 + beds * 26,
                                               "rentAmount": rent, "depositAmount": rent * 2}, t)
            if r.ok:
                units[f"{title}/{label}"] = r.body
                created += 1
            else:
                warn(f"unit {title}/{label}: {r.error()}")
        ok(f"{title}: {len(unit_rows)} units ({created} new)")


FIRST = ["Wanjiku", "Kamau", "Achieng", "Otieno", "Njeri", "Mutua", "Akinyi", "Kiprop", "Chebet", "Mwangi", "Atieno", "Kariuki",
         "Wairimu", "Omondi", "Nyokabi", "Barasa", "Muthoni", "Odhiambo", "Wangari", "Kipchoge", "Naliaka", "Juma", "Amina", "Hassan",
         "Zawadi", "Ochieng", "Makena", "Kibet", "Adhiambo", "Gathoni", "Mumbi", "Wekesa"]
LAST = ["Kimani", "Njoroge", "Otieno", "Mwangi", "Wanyama", "Cheruiyot", "Karanja", "Omar", "Macharia", "Onyango", "Kiplagat",
        "Nduta", "Mbugua", "Owino", "Lagat", "Waweru", "Sang", "Maina", "Ndegwa", "Ali"]

tenants: dict[str, dict] = {}  # full name -> tenant record

NAMED_TENANTS = {
    "David Kimani": ("david.kimani@example.co.ke", "+254733444555", "31882094"),
    "Mary Wanjiku": ("mary.wanjiku@example.co.ke", "+254722111222", "28394011"),
    "Aisha Hassan": ("aisha.hassan@example.co.ke", "+254745666777", "29471553"),
    "Joseph Mwangi": ("joseph.mwangi@example.co.ke", "+254712300001", "30011221"),
    "Esther Nyambura": ("esther.nyambura@example.co.ke", "+254712300002", "30011222"),
    "Samuel Oduor": ("samuel.oduor@example.co.ke", "+254712300003", "30011223"),
}


def tenant_record(landlord: str, name: str, email: str, phone: str, nid: str) -> dict | None:
    t = tok(landlord)
    key = f"{landlord}|{name}"
    if key in tenants:
        return tenants[key]
    for rec in fetch_all("/api/tenants/my", t):
        tenants[f"{landlord}|{rec['fullName']}"] = rec
    if key in tenants:
        return tenants[key]
    r = request("POST", "/api/tenants", {"fullName": name, "phone": phone, "nationalId": nid, "email": email,
                                         "emergencyName": "Next of kin", "emergencyPhone": "+254711000000"}, t)
    if not r.ok:
        warn(f"tenant {name}: {r.error()}")
        return None
    tenants[key] = r.body
    return r.body


leases: dict[str, dict] = {}  # "property/label" -> lease


def month_start(months_ago: int) -> dt.date:
    y, m = TODAY.year, TODAY.month - months_ago
    while m <= 0:
        y, m = y - 1, m + 12
    return dt.date(y, m, 1)


def step_leases() -> None:
    say("Tenants and leases")
    rng = random.Random(7)
    plan = []  # (property, label, landlord, tenant name, email, phone, nid, startMonthsAgo, state)
    named = list(NAMED_TENANTS.items())
    # Named tenants get specific homes so the tenant logins land on a populated tenancy.
    fixed = {
        "Riverside Court, Westlands/R1A": ("David Kimani", 6), "Riverside Court, Westlands/R1B": ("Mary Wanjiku", 8),
        "Kilimani Heights/K1A": ("Aisha Hassan", 5), "Westlands Towers/W1A": ("Joseph Mwangi", 6),
        "Ruaka Gardens/G1A": ("Esther Nyambura", 6), "Syokimau Villas/V1": ("Samuel Oduor", 7),
    }
    landlord_of = {t: e for t, e, *_ in RENTALS}
    used_names = set(fixed[k][0] for k in fixed)
    for key, unit in units.items():
        title, label = key.split("/", 1)
        landlord = landlord_of[title]
        if key in fixed:
            name, months = fixed[key]
            email, phone, nid = NAMED_TENANTS[name]
            plan.append((key, landlord, name, email, phone, nid, months, "ACTIVE"))
            continue
        # ~85% occupied; the rest stay vacant so occupancy is a real figure.
        if rng.random() > 0.85:
            continue
        while True:
            name = f"{rng.choice(FIRST)} {rng.choice(LAST)}"
            if name not in used_names:
                used_names.add(name)
                break
        email = name.lower().replace(" ", ".") + "@example.co.ke"
        phone = f"+2547{rng.randint(10000000, 99999999)}"
        nid = str(rng.randint(20000000, 39999999))
        months = rng.choice([3, 4, 5, 6, 6, 6, 7, 8, 10])
        state = rng.choices(["ACTIVE", "ACTIVE", "ACTIVE", "DRAFT"], weights=[90, 0, 0, 0])[0]
        plan.append((key, landlord, name, email, phone, nid, months, state))
    made = 0
    for key, landlord, name, email, phone, nid, months, state in plan:
        title, label = key.split("/", 1)
        unit = units[key]
        rec = tenant_record(landlord, name, email, phone, nid)
        if not rec:
            continue
        t = tok(landlord)
        existing = fetch_all(f"/api/units/{unit['id']}/leases", t)
        if existing:
            leases[key] = existing[0]
            continue
        start = month_start(months)
        rent = float(unit["rentAmount"])
        r = request("POST", "/api/leases", {"unitId": unit["id"], "tenantId": rec["id"], "startDate": start.isoformat(),
                                            "rentAmount": rent, "depositAmount": rent * 2, "managementFeePct": 8.0,
                                            "billingDay": 1, "paymentFrequency": "MONTHLY", "noticePeriodDays": 60}, t)
        if not r.ok:
            warn(f"lease {key}: {r.error()}")
            continue
        a = request("PUT", f"/api/leases/{r['id']}/activate", None, t)
        leases[key] = a.body if a.ok else r.body
        made += 1
    ok(f"{len(plan)} tenancies planned, {made} created")

    # Give the tenants who have logins their accounts (landlord links them by e-mail).
    for name, (email, phone, nid) in NAMED_TENANTS.items():
        for landlord in {e for _, e, *_ in RENTALS}:
            rec = tenants.get(f"{landlord}|{name}")
            if rec and not rec.get("userId") and email in tokens:
                r = request("PUT", f"/api/tenants/{rec['id']}/link-user", None, tok(landlord))
                (ok if r.ok else warn)(f"linked {name} to their login" if r.ok else f"link {name}: {r.error()}")


def fetch_all(path: str, token: str, max_pages: int = 200) -> list:
    """Every row of a paged endpoint. The services cap a page at 100, so ask for 100 and walk on."""
    out: list = []
    sep = "&" if "?" in path else "?"
    for n in range(max_pages):
        r = request("GET", f"{path}{sep}page={n}&size=100", None, token)
        if not r.ok:
            break
        b = r.body
        if isinstance(b, list):
            return b
        out.extend(b.get("content", []))
        if b.get("last", True) or not b.get("content"):
            break
    return out


def page(resp) -> list:
    if not resp.ok:
        return []
    b = resp.body
    return b.get("content", b) if isinstance(b, dict) else (b or [])


def step_invoices() -> None:
    say("Rent invoices (issued by the real invoicing job), payments and arrears")
    # The demo overlay runs the invoice job every 10 seconds; wait until every lease has its bills.
    deadline = time.time() + 150
    landlords = sorted({e for _, e, *_ in RENTALS})
    while time.time() < deadline:
        missing = 0
        for email in landlords:
            total = 0
            for l in fetch_all("/api/leases/my", tok(email)):
                inv = fetch_all(f"/api/leases/{l['id']}/invoices", tok(email))
                if not inv and l.get("status") == "ACTIVE":
                    missing += 1
                total += len(inv)
        if missing == 0:
            break
        time.sleep(6)
    rng = random.Random(11)
    methods = ["MPESA_PAYBILL", "MPESA_PAYBILL", "MPESA_PAYBILL", "BANK", "CASH"]
    paid = part = overdue = wrote = 0
    for email in landlords:
        t = tok(email)
        for l in fetch_all("/api/leases/my", t):
            invoices = sorted(fetch_all(f"/api/leases/{l['id']}/invoices", t), key=lambda i: i["periodStart"])
            # A tenant's payment habit is fixed per lease: good, slow or in arrears.
            habit = rng.choices(["good", "slow", "arrears"], weights=[68, 22, 10])[0]
            named = l.get("tenantName") in NAMED_TENANTS
            if named:
                habit = "good" if l.get("tenantName") != "Aisha Hassan" else "slow"
            for idx, inv in enumerate(invoices):
                if inv["status"] in ("PAID", "WRITTEN_OFF") or float(inv.get("amountPaid") or 0) > 0:
                    continue
                age = len(invoices) - idx  # 1 = newest
                amount = float(inv["amountDue"])
                if habit == "arrears" and age <= 3 and not named:
                    overdue += 1
                    continue
                if habit == "slow" and age == 1:
                    continue  # this month's still open
                if habit == "slow" and age == 2 and rng.random() < 0.6:
                    r = request("POST", f"/api/invoices/{inv['id']}/record-payment",
                                {"amount": round(amount * rng.choice([0.4, 0.5, 0.6]), 2), "method": "MPESA_PAYBILL",
                                 "mpesaReceipt": receipt_code(), "note": "Part payment, balance promised Friday"}, t)
                    part += 1 if r.ok else 0
                    continue
                if age == 1 and rng.random() < 0.55:
                    continue  # the current bill is often still open
                r = request("POST", f"/api/invoices/{inv['id']}/record-payment",
                            {"amount": amount, "method": (m := rng.choice(methods)),
                             "mpesaReceipt": receipt_code() if m == "MPESA_PAYBILL" else None,
                             "note": "Recorded by the landlord"}, t)
                paid += 1 if r.ok else 0
                if not r.ok and r.status not in (409,):
                    warn(f"record-payment {inv['invoiceNumber']}: {r.error()}")
            if habit == "arrears" and len(invoices) > 5 and not named:
                old = invoices[0]
                if old["status"] not in ("PAID", "WRITTEN_OFF"):
                    w = request("PUT", f"/api/invoices/{old['id']}/write-off", None, t)
                    wrote += 1 if w.ok else 0
    ok(f"{paid} invoices paid, {part} part-paid, ~{overdue} left to go overdue, {wrote} written off")


def receipt_code() -> str:
    return "S" + "".join(RNG.choice("ABCDEFGHJKLMNPQRSTUVWXYZ0123456789") for _ in range(9))


MAINTENANCE = [
    ("PLUMBING", "HIGH", "Kitchen tap leaking", "Steady drip from the mixer tap; the cabinet beneath is staining."),
    ("ELECTRICAL", "URGENT", "Sockets dead in second bedroom", "No power to any socket in the second bedroom since Tuesday. Lighting works."),
    ("GENERAL", "LOW", "Repaint balcony railing", "Rust showing along the balcony railing."),
    ("PLUMBING", "MEDIUM", "Low water pressure in shower", "Pressure drops to a trickle in the evenings."),
    ("SECURITY", "HIGH", "Gate lock jams", "The estate gate lock sticks and residents wait outside."),
    ("APPLIANCE", "LOW", "Extractor fan noisy", "Loud rattle when the kitchen fan runs."),
    ("ELECTRICAL", "MEDIUM", "Corridor light flickering", "Third-floor corridor light flickers constantly."),
    ("PLUMBING", "URGENT", "Burst pipe under sink", "Water under the sink, tap shut off at the mains."),
    ("GENERAL", "MEDIUM", "Window will not close", "Bedroom window frame has swollen and does not latch."),
    ("PEST", "LOW", "Cockroaches in the kitchen", "Needs fumigation, seen every night."),
]


def step_maintenance() -> None:
    say("Maintenance")
    rng = random.Random(3)
    made = 0
    for email in sorted({e for _, e, *_ in RENTALS}):
        t = tok(email)
        if page(request("GET", "/api/maintenance/my?size=5", None, t)):
            ok(f"{email}: maintenance already seeded")
            continue
        mine = [u for k, u in units.items() if k in leases and RENTAL_LANDLORD[k.split('/')[0]] == email]
        for n in range(min(len(MAINTENANCE), max(3, len(mine) // 4))):
            cat, prio, title, desc = MAINTENANCE[n % len(MAINTENANCE)]
            unit = rng.choice(mine)
            r = request("POST", "/api/maintenance", {"unitId": unit["id"], "category": cat, "priority": prio, "title": title, "description": desc}, t)
            if not r.ok:
                warn(f"maintenance {title}: {r.error()}")
                continue
            made += 1
            stage = n % 4
            if stage == 1:
                request("PUT", f"/api/maintenance/{r['id']}", {"status": "IN_PROGRESS", "assignedTo": "Kamau & Sons Plumbers"}, t)
            elif stage == 2:
                cost = rng.choice([1800, 3500, 6200, 9500])
                request("PUT", f"/api/maintenance/{r['id']}", {"status": "RESOLVED", "assignedTo": "Bright Electricals",
                                                              "resolutionNotes": "Fixed and tested with the tenant present.",
                                                              "cost": cost, "costBorneBy": "LANDLORD"}, t)
    # A ticket raised by a tenant, from their own login.
    for email in ("david.kimani@example.co.ke", "mary.wanjiku@example.co.ke"):
        if page(request("GET", "/api/maintenance/my-tenancy?size=5", None, tok(email))):
            continue
        r = request("POST", "/api/maintenance/my-tenancy", {"category": "PLUMBING", "priority": "MEDIUM", "title": "Shower drain slow",
                                                            "description": "Water pools around the shower drain and takes an hour to clear."}, tok(email))
        made += 1 if r.ok else 0
        if not r.ok:
            warn(f"tenant maintenance {email}: {r.error()}")
    ok(f"{made} maintenance tickets raised")


RENTAL_LANDLORD = {t: e for t, e, *_ in RENTALS}


# ----------------------------------------------------------------------- viewings & money ---

def active_listings() -> list[dict]:
    """Fresh from the API: what a buyer can actually see and act on right now."""
    out = []
    for email in SELLER_LISTINGS:
        for title, p in my_properties(email).items():
            if p.get("status") == "ACTIVE":
                out.append({**p, "sellerEmail": email})
    return out


def step_viewings() -> None:
    say("Viewings")
    buyers = ["demo.buyer@smartre.test", "lucy.wambui@smartre.test", "brian.kiptoo@smartre.test", "noor.abdi@smartre.test"]
    active = active_listings()
    made = 0
    rng = random.Random(5)
    for i, buyer in enumerate(buyers):
        t = tok(buyer)
        if page(request("GET", "/api/viewings/my/buyer?size=3", None, t)):
            ok(f"{buyer}: viewings already seeded")
            continue
        for j in range(3):
            prop = active[(i * 3 + j) % len(active)]
            when = (dt.datetime.now() + dt.timedelta(days=2 + j * 3 + i, hours=rng.randint(1, 5))).replace(minute=0, second=0, microsecond=0)
            phone = PEOPLE[buyer][2] if not (i == 3 and j == 2) else "+254711200100"  # ends 00: this customer declines the prompt
            r = request("POST", "/api/viewings", {"propertyId": prop["id"], "sellerId": prop["sellerId"], "scheduledAt": when.isoformat(),
                                                  "buyerPhone": phone, "notes": "Would like to see the compound and parking."}, t)
            if r.ok:
                made += 1
            else:
                warn(f"viewing {buyer} -> {prop['title']}: {r.error()}")
    ok(f"{made} viewings booked (fees settle through the mock M-Pesa)")
    time.sleep(14)  # mock STK settles on the next reconciliation tick, then Kafka tells viewing-service
    advanced = 0
    for email in SELLER_LISTINGS:
        st = tok(email)
        for v in fetch_all("/api/viewings/my/seller", st):
            if v["status"] == "REQUESTED":
                n = hash_int(v["id"]) % 4
                if n == 0:
                    continue  # leave a few waiting for the seller to answer
                r = request("PUT", f"/api/viewings/{v['id']}/confirm-seller", None, st)
                if r.ok:
                    advanced += 1
                    if n >= 2:
                        request("PUT", f"/api/viewings/{v['id']}/confirm-buyer", None, tok_for_user(v["buyerId"]))
    ok(f"{advanced} viewings confirmed by sellers")


def tok_for_user(user_id: str) -> str:
    for email, uid in user_ids.items():
        if uid == user_id:
            return tokens[email]
    return ""


PHONES = {e: v[2] for e, v in PEOPLE.items()}


def step_payments() -> None:
    say("Buyer deposits, escrow, releases and refunds (mock M-Pesa)")
    admin = tok("demo.admin@smartre.test")
    active = active_listings()
    if not active:
        warn("no active listings to take deposits on")
        return
    plans = [
        # The platform requires a deposit of 10-100% of the asking price.
        ("demo.buyer@smartre.test", 0, "DEPOSIT", 0.10, "release"),
        ("lucy.wambui@smartre.test", 1, "DEPOSIT", 0.12, "release"),
        ("brian.kiptoo@smartre.test", 2, "DEPOSIT", 0.10, "hold"),
        ("noor.abdi@smartre.test", 3, "FULL_PAYMENT", 1.0, "hold"),
        ("demo.buyer@smartre.test", 4, "DEPOSIT", 0.15, "refund"),
        ("lucy.wambui@smartre.test", 5, "DEPOSIT", 0.10, "hold"),
        ("brian.kiptoo@smartre.test", 6, "DEPOSIT", 0.20, "release"),
    ]
    payment_ids: list[tuple[str, dict, str, str]] = []
    for buyer, idx, ptype, pct, fate in plans:
        prop = active[idx % len(active)]
        amount = int(round(float(prop["price"]) * pct, -3))
        key = f"demo-{buyer.split('@')[0]}-{idx}-{ptype}"
        r = request("POST", "/api/payments/initiate", {"propertyId": prop["id"], "sellerId": prop["sellerId"], "amount": amount,
                                                       "phoneNumber": PHONES[buyer], "paymentType": ptype, "idempotencyKey": key}, tok(buyer))
        if r.ok:
            payment_ids.append((r["id"], prop, fate, buyer))
        else:
            warn(f"payment {buyer} -> {prop['title']}: {r.error()}")
    ok(f"{len(payment_ids)} payments initiated; waiting for the mock M-Pesa to settle them")
    time.sleep(10)
    for pid, prop, fate, buyer in payment_ids:
        s = request("GET", f"/api/payments/{pid}", None, tok(buyer))
        status = s["status"] if s.ok else "?"
        if status != "COMPLETED":
            warn(f"payment {pid} is {status}, not COMPLETED")
            continue
        if fate == "release":
            r = request("PUT", f"/api/revenue/payments/{pid}/release-escrow",
                        {"payoutMethod": "MPESA_B2C", "sellerPhone": PHONES.get(prop["sellerEmail"], "+254700100005"), "notes": "Transfer documents signed"}, admin)
            (ok if r.ok else warn)(f"escrow released for {prop['title']}" if r.ok else f"release {pid}: {r.error()}")
        elif fate == "refund":
            r = request("PUT", f"/api/revenue/payments/{pid}/refund", {"reason": "Buyer withdrew before signing; seller agreed"}, admin)
            (ok if r.ok else warn)(f"refunded deposit on {prop['title']}" if r.ok else f"refund {pid}: {r.error()}")
        else:
            ok(f"escrow held for {prop['title']}")
    time.sleep(5)


def step_reviews() -> None:
    say("Reviews")
    comments = [
        (5, "Smooth from viewing to transfer. Documents were exactly as described."),
        (4, "Honest seller and a fair price. Replies were quick."),
        (5, "The escrow made us comfortable to pay the deposit."),
        (3, "Good property, but the viewing started forty minutes late."),
        (4, "Clear title and helpful throughout."),
    ]
    made = 0
    for n, buyer in enumerate(["demo.buyer@smartre.test", "lucy.wambui@smartre.test", "brian.kiptoo@smartre.test"]):
        t = tok(buyer)
        if page(request("GET", "/api/reviews/my?size=2", None, t)):
            continue
        for p in page(request("GET", "/api/payments/my?size=20", None, t)):
            if p["status"] == "COMPLETED" and p.get("paymentType") in ("DEPOSIT", "FULL_PAYMENT"):
                rating, text = comments[(n + made) % len(comments)]
                r = request("POST", "/api/reviews", {"sellerId": p["sellerId"], "propertyId": p["propertyId"], "paymentId": p["id"],
                                                     "rating": rating, "comment": text}, t)
                if r.ok:
                    made += 1
                elif r.status not in (409,):
                    warn(f"review: {r.error()}")
    ok(f"{made} reviews written")


def step_reports() -> None:
    say("Reports, notifications and profiles")
    admin = tok("demo.admin@smartre.test")
    existing = sum(len(page(request("GET", f"/api/verification/reports/admin/queue?status={st}&size=50", None, admin)))
                   for st in ("OPEN", "RESOLVED", "DISMISSED"))
    if existing == 0:
        listings = {p["title"]: p for p in active_listings()}
        users_by_email = {e: user_ids.get(e) for e in ("faith.achieng@smartre.test",)}
        plan = [
            ("lucy.wambui@smartre.test", "LISTING", "Executive townhouse, Lavington", "FAKE_LISTING", "The photos look older than the year listed."),
            ("brian.kiptoo@smartre.test", "LISTING", "Commercial block, Thika Road", "OFF_PLATFORM_PAYMENT_REQUEST", "The agent asked me to send a deposit to a personal number instead of using escrow."),
            ("noor.abdi@smartre.test", "LISTING", "Ruiru 3BR bungalow with SQ", "DUPLICATE_LISTING", "The same house is advertised under another name on this site."),
            ("demo.buyer@smartre.test", "USER", "", "SCAM_AGENT", "Claims to be an agent for the owner but could not show authority."),
        ]
        for reporter, ttype, title, reason, details in plan:
            target = listings[title]["id"] if ttype == "LISTING" and title in listings else users_by_email.get("faith.achieng@smartre.test")
            if not target:
                continue
            r = request("POST", "/api/verification/reports", {"targetType": ttype, "targetId": target, "reason": reason, "details": details}, tok(reporter))
            (ok if r.ok else warn)(f"report: {reason}" if r.ok else f"report {reason}: {r.error()}")
        queue = page(request("GET", "/api/verification/reports/admin/queue?status=OPEN&size=50", None, admin))
        outcomes = [("RESOLVED", "Seller contacted; listing photos replaced."), ("DISMISSED", "Checked the register; the listings are different parcels.")]
        for rep, (decision, notes) in zip(queue, outcomes):
            request("PUT", f"/api/verification/reports/admin/{rep['id']}/resolve", {"decision": decision, "notes": notes}, admin)
        ok("one report resolved, one dismissed, the rest left open for the demo")
    for email, fields in {
        "demo.landlord@smartre.test": dict(accountType="INDIVIDUAL", kraPin="A012345678Z", paybillNumber="522522", preferredPayoutMethod="MPESA", payoutPhone="+254700100002"),
        "wanjiru.properties@smartre.test": dict(accountType="COMPANY", companyName="Wanjiru Properties Ltd", companyRegNumber="CPR/2015/204411", kraPin="P051234567K", preferredPayoutMethod="BANK", bankName="Equity Bank", bankBranch="Westlands", bankAccountName="Wanjiru Properties Ltd", bankAccountNumber="0140191234567"),
        "demo.seller@smartre.test": dict(kraPin="A098765432M", preferredPayoutMethod="MPESA", payoutPhone="+254700100005"),
    }.items():
        profile(email, **fields)
    ok("profiles, payout details and fraud reports in place")


def step_history() -> None:
    """Everything above happens in the last few minutes, so every chart would show a single
    spike. Spread the money over the past ~6 months so revenue and activity trends have a shape.
    No API lets anyone edit a timestamp (correctly), so this is a documented SQL step on demo
    data only: payments are re-dated in order, and each revenue row follows its payment."""
    say("Spreading payment history over six months (SQL, demo data only)")
    ok_a = sql("payment_db", """
        WITH ranked AS (
          SELECT id, row_number() OVER (ORDER BY created_at, id) AS rn, count(*) OVER () AS n FROM payments
        )
        UPDATE payments p SET
          created_at = now() - (((r.n - r.rn) * 170.0 / GREATEST(r.n, 1)) || ' days')::interval,
          updated_at = now() - (((r.n - r.rn) * 170.0 / GREATEST(r.n, 1)) || ' days')::interval + interval '3 minutes'
        FROM ranked r WHERE p.id = r.id""", "payment-db")
    ok_b = sql("payment_db", """
        UPDATE company_revenue c SET
          created_at = p.created_at + interval '2 minutes',
          updated_at = p.updated_at,
          seller_payout_at = CASE WHEN c.seller_payout_at IS NOT NULL THEN p.created_at + interval '2 days' END
        FROM payments p WHERE p.id = c.payment_id""", "payment-db")
    # Page views are counted once per visitor per window, so a seed cannot visit its way to a
    # believable figure; give each live listing a stable, varied count instead.
    ok_c = sql("property_db", "UPDATE properties SET view_count = 14 + (abs(hashtext(id::text)) % 190) WHERE status IN ('ACTIVE','SOLD')", "property-db")
    if ok_a and ok_b and ok_c:
        ok("payment, revenue and listing-view history set")


# ---------------------------------------------------------------------------------- main -----

STEPS = [
    ("accounts", step_accounts), ("listings", step_listings), ("units", step_units), ("leases", step_leases),
    ("invoices", step_invoices), ("maintenance", step_maintenance), ("viewings", step_viewings),
    ("payments", step_payments), ("reviews", step_reviews), ("reports", step_reports), ("history", step_history),
]


def hydrate() -> None:
    """When only some steps run, rebuild the lookups the later steps rely on from the API."""
    for email in PEOPLE:
        try:
            tok(email)
        except Exception:
            pass
    for email in ("demo.seller@smartre.test", "faith.achieng@smartre.test", "lakeside.lands@smartre.test", "peter.mutua@smartre.test"):
        for title, p in my_properties(email).items():
            properties[title] = {**p, "sellerEmail": email}
    for title, email, *_ in RENTALS:
        p = my_properties(email).get(title)
        if not p:
            continue
        properties[title] = {**p, "sellerEmail": email}
        for u in fetch_all(f"/api/units/property/{p['id']}", tok(email)):
            units[f"{title}/{u['label']}"] = u
        for l in fetch_all("/api/leases/my", tok(email)):
            for k, u in units.items():
                if u["id"] == l.get("unitId"):
                    leases[k] = l


def main() -> int:
    wait_gateway()
    started = time.time()
    for name, fn in STEPS:
        if want(name):
            if ONLY and name != STEPS[0][0]:
                hydrate()
            try:
                fn()
            except Exception as e:  # keep going: a demo with one thin screen beats no demo
                warn(f"step {name} stopped early: {type(e).__name__}: {e}")
    say("Done")
    print(f"  {len(tokens)} accounts, {len(properties)} properties, {len(units)} units, {len(leases)} leases, in {time.time() - started:0.0f}s")
    print("  Password for every demo account:", PASSWORD)
    if warnings:
        print(f"\n  {len(warnings)} warning(s):")
        for w in warnings[:40]:
            print("   -", w)
    return 0


if __name__ == "__main__":
    sys.exit(main())
