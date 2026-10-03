#!/usr/bin/env bash
# Prints a Secret manifest with freshly generated random values to stdout. Nothing is written to disk:
#   ./scripts/k8s-secret.sh | kubectl apply -f -
# Run it once. Running it again would replace the database passwords with ones the existing database does not have.
# Hex keeps every value inside the characters Soko accepts (it rejects quotes and other unsafe characters).
set -euo pipefail
rand() { openssl rand -hex "$1"; }
cat <<YAML
apiVersion: v1
kind: Secret
metadata:
  name: soko-secrets
  namespace: soko
type: Opaque
stringData:
  SOKO_DB_OWNER_PASSWORD: $(rand 24)
  SOKO_DB_APP_PASSWORD: $(rand 24)
  SOKO_SYSTEM_KEY: $(rand 24)
  SOKO_JWT_SECRET: $(rand 48)
YAML
