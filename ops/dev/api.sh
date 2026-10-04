#!/usr/bin/env bash
# Boots the API against the local Docker Postgres from ops/dev/db.sh. This is what `npm run dev` runs.
#
# Three of the assignments below are load-bearing, and each one is a trap someone has already hit:
#
#   SPRING_DATASOURCE_URL carries a host, which is what bypasses the Cloud SQL socket-factory URL in
#   application.yml — no ADC login, no shared database. Environment variables outrank profile files,
#   so this wins over application-local.yml without editing it. (Same mechanism as e2e/stack/up.sh.)
#
#   DB_PASSWORD is not redundant with SPRING_DATASOURCE_PASSWORD. application.yml has
#   `password: ${DB_PASSWORD}` with NO default, so a clone with no application-local.yml dies on an
#   unresolved placeholder while assembling the property — before Spring ever looks at the override.
#
#   LIGHTMOVE_EMAIL_PROVIDER=log is not optional. application-local.yml pins `provider: resend` with a
#   live API key *literally* rather than through the ${EMAIL_PROVIDER} placeholder, so only the full
#   property path outranks it. Without this, every signup against your empty local database mails a
#   real person.
#
# The profile stays `local` rather than `e2e`: it holds your OAuth client credentials, logback-spring
# keeps human-readable output there so emailed verification links are legible in the console, and
# JwtConfig only lets local/dev/test mint their own signing keypair.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PG_PORT="${PG_PORT:-55433}"

# A dev-only key-encryption key for the secrets the app stores (core/crypto), the same one the tests use. It
# protects nothing real; a deployment's own comes from Secret Manager.
DEV_CREDENTIAL_KEYSET='{"primaryKeyId":653769729,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.AesGcmKey","value":"GiDdcDK+eD7jyE+NjJ55C7Ta4t9IgqMRKbdXgoG/+4KVfw==","keyMaterialType":"SYMMETRIC"},"status":"ENABLED","keyId":653769729,"outputPrefixType":"TINK"}]}'

cd "$REPO_DIR/apps/api"
exec env \
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$PG_PORT/lightmove" \
  SPRING_DATASOURCE_USERNAME=lm_app \
  SPRING_DATASOURCE_PASSWORD=lm \
  DB_PASSWORD=lm \
  LIGHTMOVE_EMAIL_PROVIDER=log \
  CREDENTIAL_KEYSET="${CREDENTIAL_KEYSET:-$DEV_CREDENTIAL_KEYSET}" \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
