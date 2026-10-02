#!/usr/bin/env python3
"""Opens every web screen the demo offers, as each role, and reports what loads and what is empty.

    scripts/demo.sh check

Reads WEB_PORT and DEMO_PASSWORD from the environment (demo.sh exports them from .env.demo). A screen
counts as loaded when it answers 200 and says neither "not reachable" nor shows an error page; as
empty when it shows the "No ... yet" state.
"""
import json
import os
import sys
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "tools"))
from catalogue import SERVICES  # noqa: E402
from gen_support import parse_entity  # noqa: E402

WEB = f"http://localhost:{os.environ.get('WEB_PORT', '13000')}"
PASSWORD = os.environ.get("DEMO_PASSWORD", "")
ENABLED = [s for s in os.environ.get("DEMO_SERVICES", "").split(",") if s]
ROLES = {
    "demo@smartseason.local": "ADMIN", "farmer@smartseason.local": "FARMER",
    "manager@smartseason.local": "MANAGER", "agronomist@smartseason.local": "AGRONOMIST",
    "store@smartseason.local": "STOREKEEPER", "finance@smartseason.local": "FINANCE",
    "buyer@smartseason.local": "BUYER", "amina@smartseason.local": "WORKER",
}
FEATURES = ["/", "/my-work", "/live", "/advisor", "/team", "/account"]


def session(email):
    """Signs in through the web app and returns the Cookie header to send afterwards.

    Cookies are carried by hand: the app marks them Secure in production mode, and a cookie jar
    would (correctly) refuse to send those over plain http://localhost.
    """
    request = urllib.request.Request(
        f"{WEB}/api/auth/login", data=json.dumps({"email": email, "password": PASSWORD}).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(request, timeout=30) as response:
        cookies = [value.split(";")[0] for value in response.headers.get_all("Set-Cookie") or []]
    if not any(c.startswith("ss_token=") for c in cookies):
        raise SystemExit(f"sign-in as {email} set no session cookie")
    return "; ".join(cookies)


def fetch(cookie, path):
    request = urllib.request.Request(f"{WEB}{path}", headers={"Cookie": cookie})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            body = response.read().decode("utf8", "replace")
            # A signed-out request is redirected to the sign-in page, which is also a 200.
            return (401 if response.url.endswith("/login") else response.status), body
    except urllib.error.HTTPError as error:
        return error.code, ""


def main():
    if not PASSWORD:
        sys.exit("run this through scripts/demo.sh check")
    pages = [("/" + s["name"][:-8] + "/" + parse_entity(e)[1].replace("_", "-"))
             for s in SERVICES if not ENABLED or s["name"][:-8] in ENABLED for e in s["entities"]]
    admin = session("demo@smartseason.local")
    empty, broken = [], []
    for path in pages:
        status, body = fetch(admin, path)
        if status != 200 or "is not reachable" in body:
            broken.append((path, status))
        elif " yet.</" in body:
            empty.append(path)
    print(f"ADMIN: {len(pages)} entity screens, {len(pages) - len(empty) - len(broken)} with data, "
          f"{len(empty)} empty, {len(broken)} broken")
    for path in empty:
        print("  empty  ", path)
    for path, status in broken:
        print("  BROKEN ", path, status)

    bad = 0
    for email, role in ROLES.items():
        opener = session(email)
        seen = []
        for path in FEATURES:
            status, body = fetch(opener, path)
            seen.append(f"{path}={status}")
            if status not in (200, 404, 307, 308):
                bad += 1
        banner = "Demo mode" in fetch(opener, "/")[1]
        print(f"{role:12} {' '.join(seen)}  banner={'yes' if banner else 'NO'}")
    return 1 if broken or bad else 0


if __name__ == "__main__":
    sys.exit(main())
