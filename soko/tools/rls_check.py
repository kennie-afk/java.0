"""Proves the database itself enforces tenant isolation, independent of the application's own
"and tenant_id = ?" filters. Connects as soko_app, the role the application runs as, and tries
to read and write across tenants with raw SQL. Reads SOKO_DB_APP_PASSWORD and SOKO_SYSTEM_KEY
from ./.env. Needs the compose stack up:  python3 tools/rls_check.py
"""
import pathlib, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
env = dict(l.split("=", 1) for l in (ROOT / ".env").read_text().splitlines() if "=" in l and not l.startswith("#"))
APP_PW, SYS_KEY = env["SOKO_DB_APP_PASSWORD"], env["SOKO_SYSTEM_KEY"]
DB, OWNER = env.get("POSTGRES_DB", "soko"), env.get("POSTGRES_USER", "soko")


def psql(sql, user="soko_app", password=APP_PW):
    out = subprocess.run(
        ["docker", "compose", "exec", "-T", "-e", f"PGPASSWORD={password}", "postgres",
         "psql", "-U", user, "-d", DB, "-At", "-v", "ON_ERROR_STOP=1", "-c", sql],
        cwd=ROOT, capture_output=True, text=True)
    return out.returncode, (out.stdout + out.stderr).strip()


failed = 0


def check(label, ok):
    global failed
    print(f"  {'PASS' if ok else 'FAIL'} - {label}")
    failed += 0 if ok else 1


rc, out = psql("select rolsuper, rolbypassrls from pg_roles where rolname = current_user")
check("the application role is not a superuser and cannot bypass RLS", out == "f|f")

rc, tenants = psql("select string_agg(id::text, ',' order by name) from tenants", OWNER, env["POSTGRES_PASSWORD"])
A, B = tenants.split(",")[:2]

TABLES = ["users", "suppliers", "products", "offers", "customers", "orders", "order_lines", "otp_codes",
          "mpesa_payments", "wastage_records", "subscriptions", "platform_commissions", "invoices", "platform_ledger"]
for t in TABLES:
    rc, out = psql(f"select count(*) from {t}")
    check(f"no tenant bound: {t} shows nothing", out == "0")
rc, out = psql("select count(*) from tenants")
check("no tenant bound: tenants shows nothing", out == "0")

print("--- bound to tenant A ---")
rc, out = psql(f"begin; select set_config('soko.tenant_id','{A}',true); select count(*) from orders; "
               f"select count(*) from orders where tenant_id <> '{A}'; select count(*) from tenants; commit")
lines = [l for l in out.splitlines() if l and l not in ("BEGIN", "COMMIT") and not l.startswith(A)]
check("A sees its own orders", int(lines[0]) > 0)
check("A sees none of B's orders", lines[1] == "0")
check("A sees only its own tenant row", lines[2] == "1")

rc, out = psql(f"begin; select set_config('soko.tenant_id','{A}',true); "
               f"insert into customers (tenant_id, name, phone, county) values ('{B}', 'planted', '+254700000000', 'Nairobi'); commit")
check("A cannot write a row into B's tenant", rc != 0 and "row-level security" in out)
rc, out = psql(f"begin; select set_config('soko.tenant_id','{A}',true); "
               f"with u as (update orders set status = 'CANCELLED' where tenant_id = '{B}' returning 1) select count(*) from u; commit")
check("A cannot update B's orders (0 rows)", rc == 0 and out.splitlines()[-2] == "0")
rc, out = psql(f"begin; select set_config('soko.tenant_id','{A}',true); "
               f"with d as (delete from products where tenant_id = '{B}' returning 1) select count(*) from d; commit")
check("A cannot delete B's products (0 rows)", rc == 0 and out.splitlines()[-2] == "0")

print("--- the system key ---")
rc, out = psql("begin; select set_config('soko.system_key','not-the-key',true); select count(*) from orders; commit")
check("a wrong system key shows nothing", "\n0\n" in out + "\n")
rc, out = psql(f"begin; select set_config('soko.system_key','{SYS_KEY}',true); select count(*) > 0 from orders; commit")
check("the right system key reads across tenants", "t" in out.splitlines())
rc, out = psql("select count(*) from soko_system_key")
check("the application role cannot read the key table", rc != 0 and "permission denied" in out)
rc, out = psql("select soko_system()")
check("without a key soko_system() is false", out == "f")

print()
print("ALL RLS CHECKS PASSED" if not failed else f"{failed} CHECK(S) FAILED")
sys.exit(1 if failed else 0)
