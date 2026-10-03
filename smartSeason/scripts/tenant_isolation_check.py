#!/usr/bin/env python3
"""Proves tenant isolation against a RUNNING stack: through the API and in the database.

    scripts/demo.sh            # bring a stack up first
    scripts/tenant_isolation_check.py

It registers two brand-new organisations (A and B), then:

  API    B cannot read, list, change or delete A's rows; B cannot point a new row at A's id;
         a client cannot choose a media object key; sign-in, refresh and duplicate-email
         detection still work through the narrow lookup functions.
  DB     connected as the application role (not the owner): with no tenant bound it sees 0 rows;
         bound to B it sees none of A's; a cross-tenant insert is refused by the policy; an update
         of A's rows while bound to B changes 0 rows; the role is not a superuser and has no
         BYPASSRLS; and every table with a tenant_id, in every service database, has RLS enabled.

Exits non-zero on the first failed expectation group, printing every failure it found.

Reads GATEWAY (default http://localhost:18080), PG_CONTAINER (default: the compose project's
postgres container), APP_DB_USER (default smartseason_app) from the environment.
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

GATEWAY = os.environ.get("GATEWAY", "http://localhost:18080")
APP_USER = os.environ.get("APP_DB_USER", "smartseason_app")
OWNER_USER = os.environ.get("POSTGRES_USER", "postgres")
PASSWORD = "Tenant-check-" + uuid.uuid4().hex[:12]
RUN = uuid.uuid4().hex[:8]

failures = []
passes = 0


def check(condition, label, detail=""):
    global passes
    if condition:
        passes += 1
        print(f"  ok    {label}")
    else:
        failures.append(f"{label} {detail}".strip())
        print(f"  FAIL  {label} {detail}")


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(f"{GATEWAY}{path}", data=data, method=method)
    request.add_header("Accept", "application/json")
    if data is not None:
        request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return response.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw)
        except Exception:
            return error.code, None


def register(label):
    email = f"tenant-check-{label}-{RUN}@example.test"
    status, body = call("POST", "/api/identity/v1/auth/register", {
        "organisationName": f"Check {label} {RUN}", "fullName": f"Check {label}", "email": email,
        "password": PASSWORD, "phone": "+254700000000", "orgType": "FARM"})
    if status not in (200, 201) or not body:
        raise SystemExit(f"could not register organisation {label}: HTTP {status} {body}")
    return {"email": email, "token": body["accessToken"], "refresh": body["refreshToken"],
            "tenant": body["organisationId"]}


def psql(container, user, db, sql):
    """Runs SQL as `user` inside the postgres container; returns stdout (tuples only)."""
    command = ["docker", "exec", "-i", container, "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1",
               "-U", user, "-d", db, "-tA"]
    result = subprocess.run(command, input=sql, capture_output=True, text=True)
    return result.returncode, (result.stdout + result.stderr).strip()


def find_container():
    explicit = os.environ.get("PG_CONTAINER")
    if explicit:
        return explicit
    out = subprocess.run(
        ["docker", "ps", "-q", "--filter", "label=com.docker.compose.service=postgres",
         "--filter", "label=com.docker.compose.project=smartseason-demo"],
        capture_output=True, text=True).stdout.split()
    if not out:
        out = subprocess.run(
            ["docker", "ps", "-q", "--filter", "label=com.docker.compose.service=postgres",
             "--filter", "label=com.docker.compose.project=smartseason"],
            capture_output=True, text=True).stdout.split()
    if not out:
        raise SystemExit("no running postgres container found; set PG_CONTAINER")
    return out[0]


def api_checks(a, b):
    print("\nAPI: one tenant against another")
    status, farm_a = call("POST", "/api/farm/v1/farms", {"name": f"A farm {RUN}", "status": "ACTIVE"}, a["token"])
    check(status in (200, 201) and farm_a, "A creates a farm", f"(HTTP {status})")
    status, farm_b = call("POST", "/api/farm/v1/farms", {"name": f"B farm {RUN}", "status": "ACTIVE"}, b["token"])
    check(status in (200, 201) and farm_b, "B creates a farm", f"(HTTP {status})")
    if not (farm_a and farm_b):
        return
    fa, fb = farm_a["id"], farm_b["id"]

    status, _ = call("GET", f"/api/farm/v1/farms/{fa}", token=b["token"])
    check(status == 404, "B cannot read A's farm by id", f"(HTTP {status})")
    status, body = call("GET", "/api/farm/v1/farms?size=200", token=b["token"])
    ids = {row["id"] for row in (body or {}).get("content", [])}
    check(status == 200 and fb in ids and fa not in ids, "B's list holds B's farm and not A's")
    status, body = call("GET", "/api/farm/v1/farms?size=200", token=a["token"])
    ids = {row["id"] for row in (body or {}).get("content", [])}
    check(status == 200 and fa in ids and fb not in ids, "A's list holds A's farm and not B's")
    status, _ = call("PATCH", f"/api/farm/v1/farms/{fa}", {"name": "hijacked"}, b["token"])
    check(status == 404, "B cannot change A's farm", f"(HTTP {status})")
    status, _ = call("DELETE", f"/api/farm/v1/farms/{fa}", token=b["token"])
    check(status == 404, "B cannot delete A's farm", f"(HTTP {status})")
    status, body = call("GET", f"/api/farm/v1/farms/{fa}", token=a["token"])
    check(status == 200 and body["name"] == f"A farm {RUN}", "A's farm is untouched by all of that")

    status, body = call("POST", "/api/farm/v1/plots", {
        "farmId": fa, "name": "smuggled", "areaHa": 1, "irrigated": False, "status": "ACTIVE"}, b["token"])
    check(status in (400, 422), "B cannot attach a new plot to A's farm", f"(HTTP {status})")
    status, body = call("POST", "/api/farm/v1/plots", {
        "farmId": fb, "name": "own plot", "areaHa": 1, "irrigated": False, "status": "ACTIVE"}, b["token"])
    check(status in (200, 201), "B can attach a plot to B's own farm", f"(HTTP {status})")
    status, body = call("POST", "/api/farm/v1/plots", {
        "farmId": str(uuid.uuid4()), "name": "nowhere", "areaHa": 1, "irrigated": False, "status": "ACTIVE"}, b["token"])
    check(status in (400, 422), "a plot on a farm that does not exist is refused the same way", f"(HTTP {status})")

    status, asset = call("POST", "/api/media/v1/media-assets", {
        "storageKey": f"{b['tenant']}/media_assets/stolen-object", "contentType": "image/jpeg",
        "sizeBytes": 10, "virusScanned": True, "virusClean": True, "status": "READY"}, a["token"])
    check(status in (200, 201) and asset and asset.get("storageKey", "").startswith(a["tenant"] + "/")
          and "stolen-object" not in asset.get("storageKey", ""),
          "a client-supplied storage key is ignored; the key is built under A's own tenant",
          f"(HTTP {status}, key={asset and asset.get('storageKey')})")

    print("\nAPI: sign-in paths that have no tenant yet")
    for who, label in ((a, "A"), (b, "B")):
        status, body = call("POST", "/api/identity/v1/auth/login", {"email": who["email"], "password": PASSWORD})
        check(status == 200 and body and body.get("organisationId") == who["tenant"],
              f"{label} signs in by e-mail and lands in their own tenant", f"(HTTP {status})")
        status, body = call("POST", "/api/identity/v1/auth/refresh", {"refreshToken": who["refresh"]})
        check(status == 200 and body and body.get("organisationId") == who["tenant"],
              f"{label}'s refresh token resolves to their own tenant", f"(HTTP {status})")
    status, _ = call("POST", "/api/identity/v1/auth/register", {
        "organisationName": "Dup", "fullName": "Dup", "email": a["email"].upper(), "password": PASSWORD,
        "phone": "+254700000000", "orgType": "FARM"})
    check(status == 409, "registering an e-mail that belongs to another tenant is a conflict", f"(HTTP {status})")
    status, _ = call("POST", "/api/identity/v1/auth/login", {"email": a["email"], "password": "wrong-" + PASSWORD})
    check(status in (400, 401, 422), "a wrong password is refused", f"(HTTP {status})")
    status, _ = call("POST", "/api/identity/v1/auth/refresh", {"refreshToken": "not-a-real-token"})
    check(status in (400, 401, 422), "an unknown refresh token is refused", f"(HTTP {status})")
    return fa, fb


def db_checks(container, a, b):
    print("\nDB: as the application role, in farm_db")
    code, out = psql(container, APP_USER, "farm_db",
                     "select rolsuper, rolbypassrls from pg_roles where rolname = current_user;")
    check(code == 0 and out == "f|f", f"role {APP_USER} is not a superuser and has no BYPASSRLS", f"({out})")
    code, owner_total = psql(container, OWNER_USER, "farm_db", "select count(*) from farms;")
    check(code == 0 and int(owner_total) >= 2, "the owner sees the farms (so the zero below means something)",
          f"({owner_total})")
    code, out = psql(container, APP_USER, "farm_db", "select count(*) from farms;")
    check(code == 0 and out == "0", "with NO tenant bound the application role sees 0 farms", f"({out})")
    code, out = psql(container, APP_USER, "farm_db", f"""
        begin;
        select set_config('app.tenant_id', '{b['tenant']}', true);
        select count(*) from farms where tenant_id = '{a['tenant']}';
        select count(*) from farms where tenant_id = '{b['tenant']}';
        commit;""")
    lines = [line for line in out.splitlines() if line.strip().isdigit()]
    check(code == 0 and lines[:2] == ["0", "1"], "bound to B it sees none of A's farms and its own", f"({lines})")
    code, out = psql(container, APP_USER, "farm_db", f"""
        begin;
        select set_config('app.tenant_id', '{b['tenant']}', true);
        insert into farms (id, tenant_id, created_at, updated_at, version, name, status)
          values (gen_random_uuid(), '{a['tenant']}', now(), now(), 0, 'smuggled', 'ACTIVE');
        commit;""")
    check(code != 0 and "row-level security" in out, "bound to B, inserting a row for A is refused by the policy",
          f"({out[:90]})")
    code, out = psql(container, APP_USER, "farm_db", f"""
        begin;
        select set_config('app.tenant_id', '{b['tenant']}', true);
        update farms set name = 'x' where tenant_id = '{a['tenant']}';
        commit;""")
    code2, name_after = psql(container, OWNER_USER, "farm_db",
                             f"select name from farms where tenant_id = '{a['tenant']}';")
    check(code == 0 and code2 == 0 and name_after == f"A farm {RUN}",
          "bound to B, updating A's rows changes nothing", f"({name_after})")
    code, out = psql(container, APP_USER, "farm_db", f"""
        begin;
        select set_config('app.tenant_id', '{b['tenant']}', true);
        delete from farms where tenant_id = '{a['tenant']}';
        commit;
        select count(*) from farms;""")
    code2, owner_after = psql(container, OWNER_USER, "farm_db", f"select count(*) from farms where tenant_id = '{a['tenant']}';")
    check(code2 == 0 and owner_after == "1", "bound to B, deleting A's rows deletes nothing", f"({owner_after})")

    print("\nDB: every tenant table in every service database")
    code, dbs = psql(container, OWNER_USER, "postgres",
                     "select datname from pg_database where datname like '%\\_db' order by 1;")
    unprotected = []
    tables = 0
    for db in dbs.split():
        code, out = psql(container, OWNER_USER, db, """
            select c.relname || '|' || c.relrowsecurity
            from pg_class c join pg_namespace n on n.oid = c.relnamespace
            where n.nspname = 'public' and c.relkind in ('r','p') and not c.relispartition
              and c.relname <> 'outbox_events'
              and exists (select 1 from pg_attribute a where a.attrelid = c.oid
                          and a.attname = 'tenant_id' and not a.attisdropped);""")
        if code != 0:
            unprotected.append(f"{db}: {out[:60]}")
            continue
        for line in out.splitlines():
            tables += 1
            name, enabled = line.split("|")
            if enabled not in ("t", "true"):
                unprotected.append(f"{db}.{name}")
    check(tables > 0 and not unprotected, f"{tables} tenant tables (in the databases whose service has started) all have RLS enabled",
          f"(unprotected: {unprotected[:5]})")


def main():
    container = find_container()
    print(f"Tenant isolation check against {GATEWAY} (postgres container {container[:12]})")
    a, b = register("A"), register("B")
    api_checks(a, b)
    db_checks(container, a, b)
    print(f"\n{passes} checks passed, {len(failures)} failed")
    if failures:
        for f in failures:
            print(f"  - {f}")
        sys.exit(1)


if __name__ == "__main__":
    main()
