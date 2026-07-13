#!/usr/bin/env bash
#
# Opens a psql session on the LightMove database as *you*, with no password.
#
#   ./ops/cloudsql/psql.sh                          # interactive shell
#   ./ops/cloudsql/psql.sh -c "SELECT * FROM app_lm_user"   # one-shot query
#
# Why this exists rather than `gcloud sql connect`: that command prompts for a password, and an IAM
# principal does not have one — it authenticates with an OAuth token. The proxy's --auto-iam-authn
# flag mints and presents that token for you, which is the piece `gcloud sql connect` is missing.
#
# Read-only. Migration V2 grants SELECT and nothing else: a human poking at the database
# interactively should be able to look at anything and change nothing. Writes go through the
# application, where they are validated and audited.
set -euo pipefail

INSTANCE="${CLOUD_SQL_CONNECTION_NAME:-hak-talent-mapping:us-central1:bright-gcc}"
DATABASE="${DB_NAME:-lightmove}"
PORT="${PROXY_PORT:-5433}"

# Homebrew keeps libpq keg-only, so psql is not on PATH by default.
export PATH="/opt/homebrew/opt/libpq/bin:$PATH"

command -v psql >/dev/null || { echo "psql not found. brew install libpq"; exit 1; }

IAM_USER="${DB_IAM_USER:-$(gcloud config get-value account 2>/dev/null)}"
[[ -n "$IAM_USER" ]] || { echo "No gcloud account. Run: gcloud auth login"; exit 1; }

proxy_log=$(mktemp)
cloud-sql-proxy "$INSTANCE" --port "$PORT" --auto-iam-authn > "$proxy_log" 2>&1 &
proxy_pid=$!
trap 'kill "$proxy_pid" 2>/dev/null; rm -f "$proxy_log"' EXIT

for _ in {1..20}; do
    grep -q "ready for new connections" "$proxy_log" && break
    sleep 0.5
done

if ! grep -q "ready for new connections" "$proxy_log"; then
    echo "Proxy failed to start:"; cat "$proxy_log"; exit 1
fi

echo "Connected to $DATABASE as $IAM_USER (read-only)."
psql "host=127.0.0.1 port=$PORT dbname=$DATABASE user=$IAM_USER" "$@"
