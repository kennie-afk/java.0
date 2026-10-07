#!/usr/bin/env bash
# Smoke check for a running console (default http://localhost:3600). Playwright is not installed in this repository, so
# this is a plain HTTP check, not a browser test: it proves the pages are served and that the session endpoint refuses
# an unauthenticated caller. It does not click anything. Usage: scripts/smoke.sh [base-url]
set -euo pipefail
base="${1:-http://localhost:3600}"
fail=0
check() { # path expected-status
  code=$(curl -s -o /dev/null -w '%{http_code}' "$base$1" || echo 000)
  if [ "$code" = "$2" ]; then echo "ok   $1 -> $code"; else echo "FAIL $1 -> $code (wanted $2)"; fail=1; fi
}
check /login 200
check /setup 200
check /api/session 401
exit $fail
