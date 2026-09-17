#!/usr/bin/env bash
#
# Makes an existing LightMove account a platform super admin — or takes it away. A super admin edits
# the role-template library every workspace shares (Settings → Template library) and nothing else: the
# role reads no tenant's data.
#
#   ./ops/cloudsql/grant-platform-role.sh owner@firm.com                 # grant, on Cloud SQL
#   ./ops/cloudsql/grant-platform-role.sh owner@firm.com --revoke        # revoke, on Cloud SQL
#   ./ops/cloudsql/grant-platform-role.sh owner@firm.com --local         # grant, on `npm run dev`'s database
#
# The account must already exist. The change takes effect on the user's next request — platform roles
# are re-read every time and never carried in the token.
#
# There is no in-app way to do this, on purpose; see grant-platform-role.sql.
#
# On Cloud SQL it connects as the table's owner: lm_app until harden.sql reassigns the table, postgres
# after. Set DB_GRANTOR accordingly.
#
# The audit row names the person who ran this, not the database role every operator shares: gcloud's
# active account where there is one, else the shell user and host. Override with LM_OPERATOR.
set -euo pipefail

CONNECTION="${CLOUD_SQL_CONNECTION_NAME:-hak-talent-mapping:us-central1:bright-gcc}"
DATABASE="${DB_NAME:-lightmove}"
GRANTOR="${DB_GRANTOR:-lm_app}"
PORT="${PROXY_PORT:-5435}"
PG_CONTAINER="${PG_CONTAINER:-lm-dev-pg}"
ROLE="SUPER_ADMIN"

LOCAL_CONFIG="apps/api/src/main/resources/application-local.yml"

operator_identity() {
    if [[ -n "${LM_OPERATOR:-}" ]]; then
        echo "$LM_OPERATOR"
        return
    fi
    local account=""
    command -v gcloud >/dev/null && account="$(gcloud config get-value account 2>/dev/null || true)"
    if [[ -n "$account" && "$account" != "(unset)" ]]; then
        echo "$account"
    else
        echo "$(whoami)@$(hostname -s)"
    fi
}

EMAIL="${1:-}"
[[ -n "$EMAIL" ]] || { echo "Usage: $0 <lightmove-account-email> [--revoke] [--local]"; exit 1; }
shift

MODE="grant"
TARGET="cloud"
for arg in "$@"; do
    case "$arg" in
        --revoke) MODE="revoke" ;;
        --local)  TARGET="local" ;;
        *) echo "Unknown option: $arg"; exit 1 ;;
    esac
done

OPERATOR="$(operator_identity)"

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
SQL="$repo_root/ops/cloudsql/grant-platform-role.sql"

if [[ "$TARGET" == "local" ]]; then
    docker exec -i "$PG_CONTAINER" psql -U lm_app -d lightmove --quiet \
        -v email="$EMAIL" -v role="$ROLE" -v mode="$MODE" -v operator="$OPERATOR" < "$SQL"
    exit 0
fi

# Homebrew keeps libpq keg-only, so psql is not on PATH by default.
export PATH="/opt/homebrew/opt/libpq/bin:$PATH"
command -v psql >/dev/null || { echo "psql not found. brew install libpq"; exit 1; }

# The grantor authenticates with a password. For lm_app it is already in the gitignored local config.
if [[ -z "${PGPASSWORD:-}" && "$GRANTOR" == "lm_app" && -f "$repo_root/$LOCAL_CONFIG" ]]; then
    PGPASSWORD="$(sed -n 's/^[[:space:]]*password:[[:space:]]*//p' "$repo_root/$LOCAL_CONFIG" | head -1 | tr -d '"'\''')"
    export PGPASSWORD
fi

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

psql "host=127.0.0.1 port=$PORT dbname=$DATABASE user=$GRANTOR" \
    --quiet \
    -v ON_ERROR_STOP=1 \
    -v email="$EMAIL" \
    -v role="$ROLE" \
    -v mode="$MODE" \
    -v operator="$OPERATOR" \
    -f "$SQL"
