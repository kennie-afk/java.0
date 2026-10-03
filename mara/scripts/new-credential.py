#!/usr/bin/env python3
"""Mints a Mara credential: mop_<16 hex key id>.<43 url-safe base64 characters> (256 bits of secret).

    python3 scripts/new-credential.py                 # print one credential
    python3 scripts/new-credential.py --env           # print the lines .env needs, all minted fresh

The secret is shown here and nowhere else: identity-service keeps only a hash. Put each value in
the environment of the one place that needs it (see .env.example) and nowhere else.
"""
import base64
import secrets
import sys


def mint() -> str:
    return "mop_" + secrets.token_hex(8) + "." + base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip("=")


if __name__ == "__main__":
    if "--env" in sys.argv:
        print("# service credentials: required, one per service, never shared between them")
        print(f"MARA_SVC_SYNC_CREDENTIAL={mint()}")
        print(f"MARA_SVC_CORE_CREDENTIAL={mint()}")
        print("# optional, 24 h: lets you create the first platform operator; remove it afterwards")
        print(f"MARA_BOOTSTRAP_CREDENTIAL={mint()}")
    else:
        print(mint())
