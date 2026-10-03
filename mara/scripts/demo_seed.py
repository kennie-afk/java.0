#!/usr/bin/env python3
"""Creates (once) a demo shop in a running Mara stack and prints a fresh terminal enrolment code.

    python3 scripts/demo_seed.py            # create the shop if needed, then mint a code
    python3 scripts/demo_seed.py code       # just mint another code (valid 15 minutes)

Reads MARA_BOOTSTRAP_CREDENTIAL and MARA_IDENTITY_PORT from the environment or ./.env. It uses the
bootstrap credential (which can only manage credentials) to mint a 12-hour platform operator
credential for itself, then uses only the provisioning API (/v1/admin/*), exactly what an
operator's console would call. It prints that operator credential at the end (it is the one the
live tests and any curl need). The bootstrap credential lasts 24 h from identity's start; mint a
fresh stack's with scripts/new-credential.py --env.

THE PINS BELOW ARE DEVELOPMENT VALUES for a throwaway demo. Never reuse them.
"""
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
STATE = ROOT / ".demo-tenant.json"

STAFF = [
    # number, name, role, pin, branch key
    ("2001", "Wanjiru Kamau", "CASHIER", "4826", "main"),
    ("2002", "Otieno Odhiambo", "CASHIER", "6159", "main"),
    ("3001", "Amina Hassan", "SUPERVISOR", "5937", "main"),
]


def env(name, default=None):
    if name in os.environ:
        return os.environ[name]
    dotenv = ROOT / ".env"
    if dotenv.exists():
        for line in dotenv.read_text().splitlines():
            if line.startswith(name + "="):
                return line.split("=", 1)[1].strip()
    return default


BOOTSTRAP = env("MARA_BOOTSTRAP_CREDENTIAL")
BASE = f"http://localhost:{env('MARA_IDENTITY_PORT', '8081')}"
TOKEN = None   # the operator credential, minted from the bootstrap one in main()


def call(method, path, body=None, tenant=None, token=None):
    headers = {"Authorization": f"Bearer {token or TOKEN}", "Content-Type": "application/json"}
    if tenant:
        headers["X-Mara-Tenant"] = tenant
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw.decode()


def must(status_body, what):
    status, body = status_body
    if status >= 300:
        sys.exit(f"{what} failed ({status}): {body}")
    return body


def create_shop():
    t = must(call("POST", "/v1/admin/tenants", {
        "legalName": "Mama Njeri Mart Ltd", "tradingName": "Mama Njeri Mart", "countryCode": "KE",
        "currency": "KES", "taxIdentifier": "P000000000X", "licensedTerminals": 5,
        "branchName": "Kawangware", "timezone": "Africa/Nairobi",
        "ownerName": "Njeri Mwangi", "ownerStaffNumber": "1000", "ownerPin": "7391"}), "create tenant")
    tenant, main, owner = t["tenantId"], t["branchId"], t["ownerStaffId"]
    branches = {"main": main}
    for number, name, role, pin, branch in STAFF:
        must(call("POST", "/v1/admin/staff", {
            "branchId": branches[branch], "displayName": name, "role": role, "staffNumber": number, "pin": pin},
            tenant), f"create staff {number}")
    state = {"tenantId": tenant, "branchId": main, "ownerStaffId": owner}
    STATE.write_text(json.dumps(state, indent=2))
    return state


def shop_exists(state):
    # An unknown tenant also answers 200 (row-level security just shows it nothing), so a 200
    # alone proves nothing: a real tenant always has at least its first branch.
    status, body = call("GET", "/v1/admin/branches", tenant=state["tenantId"])
    return status == 200 and isinstance(body, list) and len(body) > 0


def main():
    global TOKEN
    if not BOOTSTRAP:
        sys.exit("MARA_BOOTSTRAP_CREDENTIAL is not set in the environment or ./.env "
                 "(mint one with scripts/new-credential.py --env, put it in .env, restart identity-service)")
    status, minted = call("POST", "/v1/admin/credentials", token=BOOTSTRAP, body={
        "label": "demo-seed operator", "expiresInHours": 12,
        "scopes": ["admin:read", "admin:write", "platform:tenants"]})
    if status != 201:
        sys.exit(f"could not mint an operator credential with the bootstrap credential: {status} {minted}")
    TOKEN = minted["credential"]
    state = json.loads(STATE.read_text()) if STATE.exists() else None
    only_code = len(sys.argv) > 1 and sys.argv[1] == "code"
    if state and not shop_exists(state):
        state = None  # stack was recreated with a fresh database
    if state is None:
        if only_code:
            sys.exit("no demo shop yet: run without arguments first")
        state = create_shop()
        created = True
    else:
        created = False
    issued = must(call("POST", "/v1/admin/enrolment-codes",
                       {"branchId": state["branchId"], "issuedBy": state["ownerStaffId"]}, state["tenantId"]),
                  "issue enrolment code")
    terminal = env("MARA_TERMINAL_PORT", "3100")
    print("=" * 66)
    print(" MARA DEMO  (development values only: never reuse these PINs)")
    print("=" * 66)
    print(f" Shop          Mama Njeri Mart, Kawangware   ({'created' if created else 'already existed'})")
    print(f" Tenant        {state['tenantId']}")
    print(f" Till          http://localhost:{terminal}")
    print(f" Enrol code    {issued['code']}     (single use, valid 15 minutes)")
    print()
    print(" Staff         number  PIN    role")
    print("               1000    7391   OWNER       Njeri Mwangi")
    for number, name, role, pin, _ in STAFF:
        print(f"               {number}    {pin}   {role:<11} {name}")
    print()
    print(" Then: Enrol > Catalogue > 'Load demo items' > Staff sign-in > Sale.")
    print(" Another code later:  python3 scripts/demo_seed.py code")
    print("=" * 66)


if __name__ == "__main__":
    main()
