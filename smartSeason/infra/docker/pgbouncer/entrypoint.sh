#!/bin/sh
# Builds the password file from the environment so no credential is baked into
# an image or committed to the repository.
#
# The password is stored in plain text on purpose: PgBouncer needs it to answer
# a SCRAM challenge from a client *and* to raise one against Postgres. A stored
# verifier would only work in one direction. The file is mode 600 inside a
# container that holds nothing else.
set -eu

: "${POSTGRES_USER:?POSTGRES_USER must be set}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}"

printf '"%s" "%s"\n' "$POSTGRES_USER" "$POSTGRES_PASSWORD" > /etc/pgbouncer/userlist.txt
chmod 600 /etc/pgbouncer/userlist.txt

exec pgbouncer /etc/pgbouncer/pgbouncer.ini
