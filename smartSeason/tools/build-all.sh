#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p .build-logs
pass=0; fail=0; failed=()
for dir in services/*/ apps/*/; do
  [ -f "$dir/pom.xml" ] || continue
  name="$(basename "$dir")"
  printf '%-30s ' "$name"
  if docker run --rm --memory=1500m -v "$ROOT":/app -v "$ROOT/.m2-cache":/root/.m2 -w "/app/$dir" \
       maven:3.9-eclipse-temurin-21-alpine mvn -B test > ".build-logs/$name.log" 2>&1; then
    tests=$(grep -oE "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+" ".build-logs/$name.log" | tail -1)
    echo "PASS  $tests"; pass=$((pass+1))
  else
    echo "FAIL"; fail=$((fail+1)); failed+=("$name")
  fi
done
echo
echo "=============================="
echo "passed: $pass   failed: $fail"
[ ${#failed[@]} -gt 0 ] && printf 'failed: %s\n' "${failed[*]}"
echo "=============================="
