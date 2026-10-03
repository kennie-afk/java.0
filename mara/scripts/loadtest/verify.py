#!/usr/bin/env python3
"""Reads the databases after a load run and checks the only things that matter for money: nothing lost, nothing
duplicated, the books balance. Usage: verify.py /tmp/mara-load.json [--wait 180]

It talks to Postgres through `docker compose exec` as the owner (which bypasses row-level security, so it sees every
tenant). Exit status 1 when any check fails.
"""
import json
import os
import subprocess
import sys
import time
import urllib.request

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
COMPOSE = ["docker", "compose", "-f", "docker-compose.yml", "-f", "docker-compose.loadtest.yml", "--env-file", ".env"]
results = json.load(open(sys.argv[1]))
wait = int(sys.argv[sys.argv.index("--wait") + 1]) if "--wait" in sys.argv else 180
terminals = results["terminals"]
ids = [t["id"] for t in terminals]
expected_sales = sum(t["sales"] for t in terminals)
expected_minor = sum(int(t["totalMinor"]) for t in terminals)
failures = []


def psql(db, sql):
    out = subprocess.run(COMPOSE + ["exec", "-T", "postgres", "psql", "-U", "mara_owner", "-d", db, "-At", "-F", "|", "-c", sql],
                         cwd=ROOT, capture_output=True, text=True)
    if out.returncode != 0:
        raise SystemExit(f"psql {db} failed: {out.stderr}")
    return [line.split("|") for line in out.stdout.strip().splitlines() if line]


def check(ok, what, detail=""):
    print(("PASS " if ok else "FAIL ") + what + (f"  ({detail})" if detail else ""))
    if not ok:
        failures.append(what)


in_list = "(" + ",".join(f"'{i}'" for i in ids) + ")"

# --- sync-service: every entry arrived once, the chain heads match what the tills hold
rows = {r[0]: r for r in psql("mara_sync", f"SELECT terminal_id, last_sequence, encode(head_digest,'hex') FROM chain_head WHERE terminal_id IN {in_list}")}
check(len(rows) == len(terminals), "every terminal has a chain head on the server", f"{len(rows)}/{len(terminals)}")
bad_head = [t["id"] for t in terminals if t["id"] not in rows or int(rows[t["id"]][1]) != t["confirmed"] or rows[t["id"]][2] != t["headDigest"]]
check(not bad_head, "server chain head == the till's head (sequence and digest) for every terminal", f"{len(bad_head)} differ")
n = int(psql("mara_sync", f"SELECT count(*) FROM journal_entry WHERE terminal_id IN {in_list}")[0][0])
check(n == expected_sales, "journal entries on the server == sales made", f"{n} vs {expected_sales}")
d = int(psql("mara_sync", f"SELECT count(*) - count(DISTINCT (terminal_id, sequence)) FROM journal_entry WHERE terminal_id IN {in_list}")[0][0])
check(d == 0, "no duplicated journal entry")
gaps = int(psql("mara_sync", f"SELECT count(*) FROM (SELECT terminal_id, count(*) c, max(sequence) m FROM journal_entry WHERE terminal_id IN {in_list} GROUP BY 1) x WHERE c <> m")[0][0])
check(gaps == 0, "no gap in any terminal's sequence")
exc = psql("mara_sync", f"SELECT kind, count(*) FROM sync_exception WHERE terminal_id IN {in_list} GROUP BY 1")
check(not exc, "no sync exceptions raised", str(exc))

# --- core-service: wait for the poller to post everything, then check the ledger
def core_sales():
    return int(psql("mara_core", f"SELECT count(*) FROM sale WHERE terminal_id IN {in_list}")[0][0])

deadline = time.time() + wait
last = -1
while time.time() < deadline:
    got = core_sales()
    if got == expected_sales:
        break
    if got != last:
        print(f"  core has posted {got}/{expected_sales} ...")
        last = got
    time.sleep(5)
got = core_sales()
check(got == expected_sales, "sales posted in the ledger service == sales made", f"{got} vs {expected_sales}")
t = int(psql("mara_core", f"SELECT count(*) FROM ledger_txn WHERE source_terminal IN {in_list}")[0][0])
check(t == expected_sales, "one ledger transaction per sale", f"{t}")
dup = int(psql("mara_core", f"SELECT count(*) - count(DISTINCT (terminal_id, sequence)) FROM sale WHERE terminal_id IN {in_list}")[0][0])
check(dup == 0, "no sale posted twice")
unbal = int(psql("mara_core", f"SELECT count(*) FROM (SELECT p.txn_id FROM posting p JOIN ledger_txn l ON l.id = p.txn_id AND l.tenant_id = p.tenant_id WHERE l.source_terminal IN {in_list} GROUP BY p.txn_id HAVING sum(p.debit_minor) <> sum(p.credit_minor)) x")[0][0])
check(unbal == 0, "every transaction's debits equal its credits")
total = int(psql("mara_core", f"SELECT coalesce(sum(total_minor),0) FROM sale WHERE terminal_id IN {in_list}")[0][0])
check(total == expected_minor, "money in the ledger service == money the tills took", f"{total} vs {expected_minor}")
ex = psql("mara_core", f"SELECT kind, count(*) FROM core_exception WHERE terminal_id IN {in_list} GROUP BY 1")
check(not ex, "no ledger exceptions raised", str(ex))

print()
print(f"{len(terminals)} terminals, {expected_sales} sales, {expected_minor / 100:.2f} KES: " + ("ALL CHECKS PASSED" if not failures else f"{len(failures)} CHECK(S) FAILED"))
sys.exit(1 if failures else 0)
