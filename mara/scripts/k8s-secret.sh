#!/usr/bin/env bash
# Prints the Secret manifest with freshly minted values to stdout. Nothing is written to disk:
#     ./scripts/k8s-secret.sh | kubectl apply -f -
# Set MARA_WITH_BOOTSTRAP=1 to include a 24 h bootstrap credential (it can only create the first platform operator;
# remove it from the Secret and restart identity-service when you have). Run this ONCE: running it again would replace the
# database passwords with ones the existing database does not have.
set -euo pipefail
cd "$(dirname "$0")/.."
mint() { python3 scripts/new-credential.py; }
cat <<YAML
apiVersion: v1
kind: Secret
metadata:
  name: mara-secrets
  namespace: mara
type: Opaque
stringData:
  MARA_DB_OWNER_PASSWORD: $(openssl rand -hex 24)
  MARA_DB_APP_PASSWORD: $(openssl rand -hex 24)
  MARA_SVC_SYNC_CREDENTIAL: $(mint)
  MARA_SVC_CORE_CREDENTIAL: $(mint)
YAML
if [ "${MARA_WITH_BOOTSTRAP:-0}" = "1" ]; then
  echo "  MARA_BOOTSTRAP_CREDENTIAL: $(mint)"
fi
