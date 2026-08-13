#!/usr/bin/env bash
# Tears the disposable stack down. KEEP_DB=1 leaves the postgres container alive so a later run can
# inspect the rows the matrix created.
set -uo pipefail

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="${RUN_DIR:-$E2E_DIR/results/current}"
PG_CONTAINER="${PG_CONTAINER:-lm-auth-test-pg}"

say() { printf '\033[36m[stack]\033[0m %s\n' "$*"; }

for name in api web; do
  pidfile="$RUN_DIR/$name.pid"
  [ -f "$pidfile" ] || continue
  pid=$(cat "$pidfile")
  if kill -0 "$pid" 2>/dev/null; then
    say "stopping $name (pid $pid) and its children"
    pkill -P "$pid" 2>/dev/null || true
    kill "$pid" 2>/dev/null || true
  fi
  rm -f "$pidfile"
done

# spring-boot:run forks a child JVM that outlives the Maven wrapper.
pkill -f 'app.lightmove.api.LightMoveApplication' 2>/dev/null || true
pkill -f 'vite.*apps/web' 2>/dev/null || true

if [ "${KEEP_DB:-}" = "1" ]; then
  say "KEEP_DB=1 — leaving $PG_CONTAINER running"
else
  say "removing $PG_CONTAINER"
  docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
fi

say "down."
