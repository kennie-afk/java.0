#!/usr/bin/env bash
# Prints a Secret manifest with freshly generated random values to stdout. Nothing is written to disk and nothing is echoed to
# the terminal log if you pipe it:   ./scripts/k8s-secret.sh | kubectl apply -f -
# Run it once. Running it again would replace the database passwords with new ones the existing database does not have.
set -euo pipefail
rand() { openssl rand -hex "$1"; }
cat <<YAML
apiVersion: v1
kind: Secret
metadata:
  name: hms-secrets
  namespace: hms
type: Opaque
stringData:
  HMS_DB_OWNER_PASSWORD: $(rand 24)
  HMS_DB_APP_PASSWORD: $(rand 24)
  HMS_JWT_SECRET: $(rand 48)
  HMS_STORAGE_S3_ACCESS_KEY: hms$(rand 6)
  HMS_STORAGE_S3_SECRET_KEY: $(rand 24)
YAML
