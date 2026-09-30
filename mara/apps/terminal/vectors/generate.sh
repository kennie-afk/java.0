#!/usr/bin/env bash
# Regenerates test/fixtures/java-vectors.json by running the real platform classes.
# Compiles platform from source inside a JDK container (no JDK is needed on the host),
# then runs Vectors.java against those classes.
#
#   vectors/generate.sh [ts-signature.json]
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
mara="$(cd "$here/../../.." && pwd)"
mkdir -p "$here/classes" "$here/../test/fixtures"
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$mara/platform/src/main/java:/src:ro" \
  -v "$here:/work" \
  -v "$here/../test/fixtures:/out" \
  -w /work maven:3.9-eclipse-temurin-21 \
  bash -c 'javac -d /work/classes $(find /src -name "*.java") \
    && java -cp /work/classes /work/Vectors.java /out/java-vectors.json "$@"' _ "$@"
