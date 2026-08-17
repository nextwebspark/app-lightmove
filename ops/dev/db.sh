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
  say "ready on localhost:$PG_PORT (lm_app/lm, database lightmove)"
}

down() {
  require_docker
  say "removing $PG_CONTAINER (volume $PG_VOLUME kept — use \`reset\` to drop the data)"
  docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
}

reset() {
  down
  say "removing volume $PG_VOLUME — the next boot re-runs every migration from V1"
  docker volume rm "$PG_VOLUME" >/dev/null 2>&1 || true
}

case "${1:-up}" in
  up)    up ;;
  down)  down ;;
  reset) reset ;;
  psql)
    require_docker
    running || { say "$PG_CONTAINER is not running — run \`npm run dev:db\` first"; exit 1; }
    shift
    exec docker exec -it "$PG_CONTAINER" psql -U lm_app -d lightmove "$@"
    ;;
  *)
    say "usage: ops/dev/db.sh [up|down|reset|psql]"
    exit 1 ;;
esac
