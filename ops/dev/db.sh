#!/usr/bin/env bash
# The Postgres a developer runs against. `npm run dev` starts this and points the API at it.
#
# Why this exists: application.yml's only datasource is the Cloud SQL socket factory, so before this
# script every casual `npm run dev` applied whatever migrations were in your tree to the *shared*
# bright-gcc dev database the moment the API booted. Flyway at boot plus a shared database means a
# half-finished migration on a side branch is everyone's problem. A container is yours alone.
#
# Deliberately NOT the e2e container:
#
#   Port 55433, not e2e's 55432 — and neither is 5432, which is usually taken by another project.
#   e2e/stack/down.sh does `docker rm -f lm-auth-test-pg` and run-all.sh restarts the stack three
#   times per run, so sharing that container would let an e2e run delete your dev data mid-session.
#
#   A named volume, unlike e2e's container, which is disposable on purpose. Dev data has to survive
#   a restart or every boot starts at "sign up again". `reset` is how you ask for a virgin database.
#
# The user, password and database name DO match e2e (lm_app / lm / lightmove), so psql habits and
# e2e/api/lib.sh's connection string shape transfer unchanged.
set -euo pipefail

PG_CONTAINER="${PG_CONTAINER:-lm-dev-pg}"
PG_PORT="${PG_PORT:-55433}"
PG_VOLUME="${PG_VOLUME:-lm-dev-pgdata}"
PG_IMAGE="${PG_IMAGE:-postgres:16-alpine}"

# The Apollo universe survives `reset` through this file rather than through the volume. See apollo_save.
APOLLO_TABLE="app_lm_apollo_companies"
APOLLO_CACHE="${APOLLO_CACHE:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.cache/apollo.dump}"

# Cloud SQL, for `apollo-pull`. Same instance and defaults as ops/cloudsql/psql.sh.
CLOUD_INSTANCE="${CLOUD_SQL_CONNECTION_NAME:-hak-talent-mapping:us-central1:bright-gcc}"
CLOUD_DATABASE="${DB_NAME:-lightmove}"
CLOUD_PROXY_PORT="${PROXY_PORT:-5433}"

say() { printf '\033[36m[dev-db]\033[0m %s\n' "$*"; }

require_docker() {
  if ! docker info >/dev/null 2>&1; then
    say "Docker is not running. Start Docker Desktop and try again."
    say "(Or use \`npm run dev:cloud\` to run against the shared Cloud SQL dev database instead.)"
    exit 1
  fi
}

running() {
  [ "$(docker inspect -f '{{.State.Running}}' "$PG_CONTAINER" 2>/dev/null || echo false)" = "true" ]
}

# psql inside the container, quiet and fatal on the first error, for the one-value queries below.
q() { docker exec "$PG_CONTAINER" psql -U lm_app -d lightmove -v ON_ERROR_STOP=1 -tAc "$1"; }

apollo_exists() { [ -n "$(q "SELECT to_regclass('public.$APOLLO_TABLE')" 2>/dev/null || true)" ]; }
apollo_rows()   { q "SELECT count(*) FROM $APOLLO_TABLE" 2>/dev/null || echo 0; }

# ---------------------------------------------------------------------------
# The Apollo universe
#
# 71,822 rows the pipeline owns and nothing in this repo can regenerate — see the db-ops skill. It is
# reference data, not dev data: wiping it with `reset` costs a 15-second re-pull over cloud-sql-proxy
# and leaves every Strategy screen empty until you remember to do it. So `reset` dumps the table to
# $APOLLO_CACHE on the way out and `up` restores it on the way back in.
#
# The dump carries schema *and* data, and is restored before the API boots — which is exactly the
# shape V23 already handles: its whole body is guarded by `IF to_regclass(...) IS NOT NULL THEN
# RETURN`, so finding the table already there makes the migration a no-op that records the version,
# the same way it behaves against the deployed database. Custom format rather than SQL so the same
# file serves both cases: whole table when Flyway has not run yet, --data-only when it has.
#
# Restoring first is also why application.yml pins `flyway.baseline-version: 0`. The restored table
# makes the schema non-empty, and baseline-on-migrate at Flyway's default of 1 reads that as "V1 has
# already been applied" — it baselines, skips V1, and V4 dies on a missing app_lm_user. Don't drop
# that setting without moving this restore.
# ---------------------------------------------------------------------------

apollo_save() {
  require_docker
  running || { say "$PG_CONTAINER is not running — nothing to snapshot"; return 0; }
  apollo_exists || { say "no $APOLLO_TABLE to snapshot"; return 0; }
  local rows; rows="$(apollo_rows)"
  [ "$rows" -gt 0 ] || { say "$APOLLO_TABLE is empty — keeping any existing snapshot as-is"; return 0; }

  mkdir -p "$(dirname "$APOLLO_CACHE")" 2>/dev/null || true
  say "snapshotting $rows $APOLLO_TABLE rows to ${APOLLO_CACHE/#$PWD\//}"
  # Via a temp file: a partial dump left in place by a mid-stream failure would be restored silently
  # on the next boot, and pg_restore is happy to read a truncated archive's opening tables.
  #
  # A failure here is fatal on purpose. `reset` snapshots before it wipes, so swallowing this would
  # turn a full disk into a silently destroyed universe — refuse the wipe instead.
  if ! docker exec "$PG_CONTAINER" pg_dump -U lm_app -d lightmove -Fc -t "$APOLLO_TABLE" > "$APOLLO_CACHE.tmp"; then
    rm -f "$APOLLO_CACHE.tmp"
    say "could not write the snapshot to ${APOLLO_CACHE/#$PWD\//} — refusing to go any further"
    exit 1
  fi
  mv "$APOLLO_CACHE.tmp" "$APOLLO_CACHE"
}

apollo_restore() {
  require_docker
  running || { say "$PG_CONTAINER is not running — run \`npm run dev:db\` first"; exit 1; }
  [ -f "$APOLLO_CACHE" ] || { say "no snapshot at ${APOLLO_CACHE/#$PWD\//} — run \`npm run dev:db:apollo\` to pull from Cloud SQL"; return 0; }

  local mode=()
  if apollo_exists; then
    local rows; rows="$(apollo_rows)"
    [ "$rows" -eq 0 ] || { say "$APOLLO_TABLE already holds $rows rows — leaving it alone"; return 0; }
    # Flyway got there first (V23 created it empty). Load the rows into the table it made.
    mode=(--data-only)
  fi

  say "restoring $APOLLO_TABLE from ${APOLLO_CACHE/#$PWD\//}"
  docker exec -i "$PG_CONTAINER" pg_restore -U lm_app -d lightmove --no-owner "${mode[@]+"${mode[@]}"}" < "$APOLLO_CACHE"
  say "restored $(apollo_rows) rows"
}

# One-off (and refresh) fetch of the universe from the shared Cloud SQL database, straight into the
# local container — no intermediate CSV on disk. Reads as *you* over cloud-sql-proxy with
# --auto-iam-authn, the same read-only path as ops/cloudsql/psql.sh; V2 grants SELECT and nothing else.
#
# The column list is spelled out rather than SELECT *: the two tables hold the same 46 columns in a
# different physical order — the deployed one was created by the pipeline, the local one by V23 — and
# a positional COPY between them would shift every value one column sideways.
apollo_pull() {
  require_docker
  running || { say "$PG_CONTAINER is not running — run \`npm run dev:db\` first"; exit 1; }

  export PATH="/opt/homebrew/opt/libpq/bin:$PATH"   # Homebrew keeps libpq keg-only
  command -v psql >/dev/null || { say "psql not found. brew install libpq"; exit 1; }
  command -v cloud-sql-proxy >/dev/null || { say "cloud-sql-proxy not found. brew install cloud-sql-proxy"; exit 1; }

  local iam_user="${DB_IAM_USER:-$(gcloud config get-value account 2>/dev/null)}"
  [ -n "$iam_user" ] || { say "No gcloud account. Run: gcloud auth login"; exit 1; }

  apollo_exists || { say "$APOLLO_TABLE does not exist locally yet — boot the API once so Flyway applies V23"; exit 1; }
  local rows; rows="$(apollo_rows)"
  [ "$rows" -eq 0 ] || { say "$APOLLO_TABLE already holds $rows rows — nothing to pull. Drop them yourself first if you want a refresh."; return 0; }

  local proxy_log proxy_pid
  proxy_log="$(mktemp)"
  cloud-sql-proxy "$CLOUD_INSTANCE" --port "$CLOUD_PROXY_PORT" --auto-iam-authn > "$proxy_log" 2>&1 &
  proxy_pid=$!
  trap 'kill "$proxy_pid" 2>/dev/null || true; rm -f "$proxy_log"' EXIT

  for _ in $(seq 1 20); do grep -q "ready for new connections" "$proxy_log" && break; sleep 0.5; done
  grep -q "ready for new connections" "$proxy_log" || { say "proxy failed to start:"; cat "$proxy_log"; exit 1; }

  # Local column order is the canonical one on both sides of the pipe.
  local cols
  cols="$(q "SELECT string_agg(column_name, ',' ORDER BY ordinal_position)
              FROM information_schema.columns WHERE table_name = '$APOLLO_TABLE'")"

  say "pulling $APOLLO_TABLE from $CLOUD_DATABASE as $iam_user (read-only)"
  psql "host=127.0.0.1 port=$CLOUD_PROXY_PORT dbname=$CLOUD_DATABASE user=$iam_user" -v ON_ERROR_STOP=1 \
      -c "\copy (SELECT $cols FROM $APOLLO_TABLE) TO STDOUT WITH (FORMAT csv)" \
    | docker exec -i "$PG_CONTAINER" psql -U lm_app -d lightmove -v ON_ERROR_STOP=1 \
        -c "\copy $APOLLO_TABLE ($cols) FROM STDIN WITH (FORMAT csv)"

  say "loaded $(apollo_rows) rows"
  refresh_keyword_vocabulary
  apollo_save
}

# The keyword box reads a materialised view that does not follow the table under it, so a pull that
# skipped this would leave the box offering the vocabulary of whatever was loaded before.
refresh_keyword_vocabulary() {
  if [ -z "$(q "SELECT to_regclass('public.app_lm_apollo_keywords')")" ]; then
    say "app_lm_apollo_keywords absent — it arrives with V33, skipping the refresh"
    return
  fi
  say "refreshing the keyword vocabulary"
  docker exec -i "$PG_CONTAINER" psql -U lm_app -d lightmove -v ON_ERROR_STOP=1 \
      -c "REFRESH MATERIALIZED VIEW app_lm_apollo_keywords"
}

up() {
  require_docker
  if running; then
    say "$PG_CONTAINER already running on :$PG_PORT"
  else
    # An existing-but-stopped container of the same name would block `docker run`. Removing it is safe:
    # the data lives in the volume, not the container's writable layer.
    docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
    say "starting $PG_IMAGE as $PG_CONTAINER on :$PG_PORT (volume $PG_VOLUME)"
    docker run -d --name "$PG_CONTAINER" \
      -e POSTGRES_USER=lm_app -e POSTGRES_PASSWORD=lm -e POSTGRES_DB=lightmove \
      -v "$PG_VOLUME:/var/lib/postgresql/data" \
      -p "$PG_PORT:5432" "$PG_IMAGE" >/dev/null
  fi

  say "waiting for postgres to accept connections"
  for _ in $(seq 1 60); do
    if docker exec "$PG_CONTAINER" pg_isready -U lm_app -d lightmove >/dev/null 2>&1; then break; fi
    sleep 1
  done
  # Not `|| true`: if it never came up, fail here rather than let Flyway produce a Hikari stack trace
  # that says nothing about the actual cause.
  docker exec "$PG_CONTAINER" pg_isready -U lm_app -d lightmove >/dev/null || {
    say "postgres never became ready — logs:"; docker logs --tail 40 "$PG_CONTAINER"; exit 1; }

  # Before the API boots, so Flyway meets the table already present and V23's guard skips its body.
  [ -f "$APOLLO_CACHE" ] && apollo_restore || true

  say "ready on localhost:$PG_PORT (lm_app/lm, database lightmove)"
}

down() {
  require_docker
  say "removing $PG_CONTAINER (volume $PG_VOLUME kept — use \`reset\` to drop the data)"
  docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
}

reset() {
  require_docker
  # The universe is reference data the pipeline owns and this repo cannot regenerate, so it is carried
  # across the wipe rather than dropped with everything else. Snapshotting needs a live server, and a
  # `reset` from a cold container is the common case, so start it back up for as long as the dump takes.
  if ! running && docker volume inspect "$PG_VOLUME" >/dev/null 2>&1; then
    say "starting $PG_CONTAINER briefly to snapshot $APOLLO_TABLE before the wipe"
    up >/dev/null
  fi
  apollo_save

  down
  say "removing volume $PG_VOLUME — the next boot re-runs every migration from V1"
  docker volume rm "$PG_VOLUME" >/dev/null 2>&1 || true
  [ -f "$APOLLO_CACHE" ] && say "$APOLLO_TABLE kept in ${APOLLO_CACHE/#$PWD\//} — the next \`up\` restores it" || true
}

case "${1:-up}" in
  up)    up ;;
  down)  down ;;
  reset) reset ;;
  apollo-pull)    apollo_pull ;;
  apollo-save)    apollo_save ;;
  apollo-restore) apollo_restore ;;
  psql)
    require_docker
    running || { say "$PG_CONTAINER is not running — run \`npm run dev:db\` first"; exit 1; }
    shift
    exec docker exec -it "$PG_CONTAINER" psql -U lm_app -d lightmove "$@"
    ;;
  *)
    say "usage: ops/dev/db.sh [up|down|reset|psql|apollo-pull|apollo-save|apollo-restore]"
    exit 1 ;;
esac
