#!/usr/bin/env bash
# Prints the smartre-secrets Secret to stdout. Nothing is written to disk:
#
#   MAIL_USERNAME=... MAIL_PASSWORD=... SMS_USERNAME=... SMS_API_KEY=... \
#     ./scripts/k8s-secret.sh | kubectl apply -f -
#
# What the platform owns (JWT_SECRET, DB_PASSWORD, INTERNAL_SECRET, GATEWAY_SIGNING_SECRET,
# MPESA_CALLBACK_SECRET) is generated here, randomly, every run. What a provider owns (M-Pesa, mail,
# SMS, Smile Identity, Ardhisasa, Gemini, S3) is taken from the environment variable of the same name
# and left empty when unset. An empty value is honest: the matching service says so at startup (and,
# with SMARTRE_ENVIRONMENT=production, refuses to start) instead of running on a made-up key.
#
# Run it once per cluster. Running it again replaces DB_PASSWORD with one the existing databases do
# not have; to rotate only the provider values, re-run with DB_PASSWORD (and the other generated
# values) exported from the live Secret.
set -euo pipefail

rand() { openssl rand -hex "$1"; }

# A YAML double-quoted scalar from any string.
q() { printf '"%s"' "$(printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"; }

# Generated unless the caller exported a value to keep.
gen() { local name=$1 bytes=$2; printf '%s' "${!name:-$(rand "$bytes")}"; }
# Taken from the environment, empty when unset.
env_or_empty() { local name=$1; printf '%s' "${!name:-}"; }

PROVIDER_KEYS=(MPESA_CONSUMER_KEY MPESA_CONSUMER_SECRET MPESA_PASSKEY MPESA_CALLBACK_URL \
  MAIL_USERNAME MAIL_PASSWORD SMS_USERNAME SMS_API_KEY \
  SMILE_IDENTITY_PARTNER_ID SMILE_IDENTITY_API_KEY ARDHISASA_API_KEY GEMINI_API_KEY \
  S3_ACCESS_KEY S3_SECRET_KEY)
for key in "${PROVIDER_KEYS[@]}"; do
  if [ -z "${!key:-}" ]; then
    echo "note: $key is empty; whatever reads it will be unconfigured" >&2
  fi
done

cat <<YAML
apiVersion: v1
kind: Secret
metadata:
  name: smartre-secrets
  namespace: smartre
type: Opaque
stringData:
  JWT_SECRET: $(q "$(gen JWT_SECRET 48)")
  DB_PASSWORD: $(q "$(gen DB_PASSWORD 24)")
  INTERNAL_SECRET: $(q "$(gen INTERNAL_SECRET 32)")
  GATEWAY_SIGNING_SECRET: $(q "$(gen GATEWAY_SIGNING_SECRET 32)")
  MPESA_CALLBACK_SECRET: $(q "$(gen MPESA_CALLBACK_SECRET 24)")
  MPESA_CONSUMER_KEY: $(q "$(env_or_empty MPESA_CONSUMER_KEY)")
  MPESA_CONSUMER_SECRET: $(q "$(env_or_empty MPESA_CONSUMER_SECRET)")
  MPESA_PASSKEY: $(q "$(env_or_empty MPESA_PASSKEY)")
  MPESA_CALLBACK_URL: $(q "$(env_or_empty MPESA_CALLBACK_URL)")
  MAIL_USERNAME: $(q "$(env_or_empty MAIL_USERNAME)")
  MAIL_PASSWORD: $(q "$(env_or_empty MAIL_PASSWORD)")
  SMS_USERNAME: $(q "$(env_or_empty SMS_USERNAME)")
  SMS_API_KEY: $(q "$(env_or_empty SMS_API_KEY)")
  SMILE_IDENTITY_PARTNER_ID: $(q "$(env_or_empty SMILE_IDENTITY_PARTNER_ID)")
  SMILE_IDENTITY_API_KEY: $(q "$(env_or_empty SMILE_IDENTITY_API_KEY)")
  ARDHISASA_API_KEY: $(q "$(env_or_empty ARDHISASA_API_KEY)")
  GEMINI_API_KEY: $(q "$(env_or_empty GEMINI_API_KEY)")
  S3_ACCESS_KEY: $(q "$(env_or_empty S3_ACCESS_KEY)")
  S3_SECRET_KEY: $(q "$(env_or_empty S3_SECRET_KEY)")
YAML
