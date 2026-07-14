#!/usr/bin/env bash
#
# Gives a person access to the LightMove database under their own Google identity — nobody has to share
# a password, and taking the access away later is one IAM change rather than a rotation.
#
#   ./ops/cloudsql/grant-db-user.sh ada@example.com            # read-only
#   ./ops/cloudsql/grant-db-user.sh ada@example.com --write    # + INSERT/UPDATE/DELETE on app_lm_*
#
# Any Google account works, a consumer one included. That has nothing to do with the application's
# work-email rule: this is a *database* login, not a LightMove account.
#
# Three layers have to line up, and doing two of them fails in a way that looks like nothing:
#
#   1. Project IAM — roles/cloudsql.instanceUser is what lets the token authenticate at all;
#      roles/cloudsql.client is what lets cloud-sql-proxy open the tunnel.
#   2. The instance  — registering the principal is what mints the Postgres role. No role, no GRANT.
#   3. The database  — the GRANTs themselves (grant-db-user.sql).
#
# Connects as lm_app, not postgres: on Cloud SQL postgres is not a superuser and is not a member of
# lm_app, so it cannot grant on tables lm_app owns — which today is all of them. See grant-db-user.sql.
#
# Idempotent. Run it again after a migration adds tables, or to widen someone from read to write.
set -euo pipefail

PROJECT="${GCP_PROJECT:-hak-talent-mapping}"
INSTANCE="${CLOUD_SQL_INSTANCE_NAME:-bright-gcc}"
CONNECTION="${CLOUD_SQL_CONNECTION_NAME:-hak-talent-mapping:us-central1:bright-gcc}"
DATABASE="${DB_NAME:-lightmove}"
GRANTOR="${DB_GRANTOR:-lm_app}"
PORT="${PROXY_PORT:-5434}"

LOCAL_CONFIG="apps/api/src/main/resources/application-local.yml"

EMAIL="${1:-}"
[[ -n "$EMAIL" ]] || { echo "Usage: $0 <google-account-email> [--write]"; exit 1; }

MODE="read"
[[ "${2:-}" == "--write" ]] && MODE="write"

# Homebrew keeps libpq keg-only, so psql is not on PATH by default.
export PATH="/opt/homebrew/opt/libpq/bin:$PATH"
command -v psql >/dev/null || { echo "psql not found. brew install libpq"; exit 1; }

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
SQL="$repo_root/ops/cloudsql/grant-db-user.sql"

# The grantor authenticates with a password, not IAM, so the proxy cannot mint its credential the way
# psql.sh does. It is already on disk in the gitignored local config; fall back to a prompt.
if [[ -z "${PGPASSWORD:-}" && -f "$repo_root/$LOCAL_CONFIG" ]]; then
    PGPASSWORD="$(sed -n 's/^[[:space:]]*password:[[:space:]]*//p' "$repo_root/$LOCAL_CONFIG" | head -1 | tr -d '"'\''')"
    export PGPASSWORD
fi

echo "Granting $EMAIL $MODE access to '$DATABASE' on '$INSTANCE'."
echo

# ── 1. Project IAM ───────────────────────────────────────────────────────────
for role in roles/cloudsql.instanceUser roles/cloudsql.client; do
    gcloud projects add-iam-policy-binding "$PROJECT" \
        --member="user:$EMAIL" \
        --role="$role" \
        --condition=None \
        --quiet > /dev/null
    echo "✓ $role"
done

# ── 2. The instance ──────────────────────────────────────────────────────────
if gcloud sql users list --instance="$INSTANCE" --format='value(name)' | grep -qx "$EMAIL"; then
    echo "✓ IAM user '$EMAIL' already registered on the instance."
else
    gcloud sql users create "$EMAIL" --instance="$INSTANCE" --type=cloud_iam_user
    echo "✓ Registered IAM user '$EMAIL'."
fi

# ── 3. The database ──────────────────────────────────────────────────────────
# No --auto-iam-authn: the grantor is a password role, not an IAM principal.
# Not `gcloud sql connect` either — it cannot pass the -v variables this SQL is parameterised by.
proxy_log=$(mktemp)
cloud-sql-proxy "$CONNECTION" --port "$PORT" > "$proxy_log" 2>&1 &
proxy_pid=$!
trap 'kill "$proxy_pid" 2>/dev/null; rm -f "$proxy_log"' EXIT

for _ in {1..20}; do
    grep -q "ready for new connections" "$proxy_log" && break
    sleep 0.5
done

if ! grep -q "ready for new connections" "$proxy_log"; then
    echo "Proxy failed to start:"; cat "$proxy_log"; exit 1
fi

echo
psql "host=127.0.0.1 port=$PORT dbname=$DATABASE user=$GRANTOR" \
    --quiet \
    -v ON_ERROR_STOP=1 \
    -v principal="$EMAIL" \
    -v mode="$MODE" \
    -f "$SQL"

cat <<EOF

✓ $EMAIL now has $MODE access to '$DATABASE'.

Tell them to authenticate as themselves and connect — psql.sh falls back to the active gcloud account,
so it needs no arguments:

    gcloud auth login
    ./ops/cloudsql/psql.sh

They need cloud-sql-proxy and libpq (brew install libpq).
EOF
