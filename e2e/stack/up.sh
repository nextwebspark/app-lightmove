#!/usr/bin/env bash
# Brings up a disposable stack for the end-to-end auth matrix:
#
#   - postgres:16-alpine in Docker on port 55432 (5432 is usually taken by another project)
#   - the API on :8080, pointed at that container instead of the shared Cloud SQL dev database
#   - the SPA on :5173 (Vite proxies /api to :8080)
#
# Two overrides are load-bearing:
#
#   SPRING_DATASOURCE_URL bypasses the Cloud SQL socket-factory URL in application.yml, so no ADC
#   login and no shared database. Environment variables outrank application-local.yml.
#
#   LIGHTMOVE_EMAIL_PROVIDER=log is not optional. The checked-out application-local.yml pins
#   `provider: resend` with a live API key, so without this every signup mails a real person.
#   EMAIL_PROVIDER does NOT work here — the local file hardcodes the value rather than the
#   placeholder, and only a direct environment variable outranks it.
set -euo pipefail

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$E2E_DIR/.." && pwd)"
RUN_DIR="${RUN_DIR:-$E2E_DIR/results/current}"
PG_CONTAINER="${PG_CONTAINER:-lm-auth-test-pg}"
PG_PORT="${PG_PORT:-55432}"

# Which Spring profile the API boots under. `local` for a developer, because that is the file holding
# their own datasource password and OAuth client. `e2e` on a runner, where application-local.yml does
# not exist at all (it is gitignored) and the run would otherwise inherit production defaults —
# a Secure/Strict cookie no browser keeps over http, and signup capped at five an hour.
PROFILE="${PROFILE:-local}"

# What the mail provider is expected to be once the API is up, and therefore what the gate below
# enforces. Only two values are legal:
#
#   log        — every ordinary script. Nothing can leave the machine.
#   blackhole  — 08-email-outage.sh only, which needs a sender that FAILS. The provider is `resend`
#                pointed at a dead loopback port, so the failure branch runs without any request
#                reaching Resend. The gate refuses to boot this variant unless the caller's EXTRA_ENV
#                really does aim the base URL at loopback.
EXPECT_PROVIDER="${EXPECT_PROVIDER:-log}"

# Extra Spring/env overrides for a boot variant (expired-token runs, tightened rate limits, domain
# blocking). Passed through verbatim: EXTRA_ENV="AUTH_LOGIN_ATTEMPTS_PER_MINUTE=3 ..." ./up.sh
EXTRA_ENV="${EXTRA_ENV:-}"

mkdir -p "$RUN_DIR"

say() { printf '\033[36m[stack]\033[0m %s\n' "$*"; }

# --- 0. decide the mail provider, before anything boots ---------------------
# Checked here rather than after startup on purpose: by the time the API is answering it has already
# read its configuration, and a mistake in this variable is one that sends real mail.
case "$EXPECT_PROVIDER" in
  log)
    MAIL_PROVIDER=log
    ;;
  blackhole)
    # `resend` with no reachable endpoint. Refuse unless the caller has actually pointed it at a dead
    # loopback port — a bogus API key alone still puts the request on the wire to Resend's servers.
    case " $EXTRA_ENV " in
      *" LIGHTMOVE_EMAIL_RESEND_BASE_URL=http://127.0.0.1:"*|*" LIGHTMOVE_EMAIL_RESEND_BASE_URL=http://localhost:"*)
        MAIL_PROVIDER=resend ;;
      *)
        say "REFUSING TO BOOT: EXPECT_PROVIDER=blackhole needs EXTRA_ENV to set"
        say "LIGHTMOVE_EMAIL_RESEND_BASE_URL to a loopback address. Real mail would be sent."
        exit 1 ;;
    esac
    ;;
  *)
    say "EXPECT_PROVIDER must be 'log' or 'blackhole', got '$EXPECT_PROVIDER'"; exit 1 ;;
esac

# --- 1. database ------------------------------------------------------------
if [ "$(docker inspect -f '{{.State.Running}}' "$PG_CONTAINER" 2>/dev/null || echo false)" = "true" ]; then
  say "postgres container $PG_CONTAINER already running on :$PG_PORT"
else
  docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
  say "starting postgres:16-alpine as $PG_CONTAINER on :$PG_PORT"
  docker run -d --name "$PG_CONTAINER" \
    -e POSTGRES_USER=lm_app -e POSTGRES_PASSWORD=lm -e POSTGRES_DB=lightmove \
    -p "$PG_PORT:5432" postgres:16-alpine >/dev/null
fi

say "waiting for postgres to accept connections"
for _ in $(seq 1 60); do
  if docker exec "$PG_CONTAINER" pg_isready -U lm_app -d lightmove >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$PG_CONTAINER" pg_isready -U lm_app -d lightmove >/dev/null

# --- 2. jwt signing keys ----------------------------------------------------
# JwtConfig only lets the API conjure its own keypair on the `local`, `dev` and `test` profiles, and
# `e2e` is deliberately not one of them — a profile that mints its own signing key is a profile that
# could be started in production. So on `e2e` the keys have to come from outside, exactly as they do
# in production, and the harness is the thing outside. Without this the API refuses to start with
# "No JWT signing keys at file:.keys/jwt-private.pem", every case answers 000, and one boot failure
# reads as several hundred assertion failures.
#
# They live under RUN_DIR (gitignored) rather than apps/api/.keys, so a run leaves the working tree
# alone, and they are minted once and reused: a fresh keypair per leg would invalidate every access
# token the previous leg issued.
KEY_DIR="$RUN_DIR/keys"
PRIVATE_KEY="$KEY_DIR/jwt-private.pem"
PUBLIC_KEY="$KEY_DIR/jwt-public.pem"
if [ ! -s "$PRIVATE_KEY" ] || [ ! -s "$PUBLIC_KEY" ]; then
  say "minting a disposable JWT keypair at $KEY_DIR"
  mkdir -p "$KEY_DIR"
  # PKCS#8 private, X.509 public — the two encodings RsaKeyProvider parses.
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$PRIVATE_KEY" 2>/dev/null
  openssl rsa -pubout -in "$PRIVATE_KEY" -out "$PUBLIC_KEY" 2>/dev/null
  chmod 600 "$PRIVATE_KEY"
fi

# --- 3. api -----------------------------------------------------------------
if curl -sf -o /dev/null "http://localhost:8080/api/v1/auth/providers"; then
  say "API already up on :8080 — leaving it alone"
else
  say "starting API on profile '$PROFILE', mail provider '$MAIL_PROVIDER' (logs: $RUN_DIR/api.log)"
  # EXTRA_ENV comes first so the assignments after it win: the datasource, the provider and the signing
  # keys are the things a boot variant must never be able to redirect, and `env` takes the last
  # assignment.
  ( cd "$REPO_DIR/apps/api" && \
    env $EXTRA_ENV \
      SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$PG_PORT/lightmove" \
      SPRING_DATASOURCE_USERNAME=lm_app \
      SPRING_DATASOURCE_PASSWORD=lm \
      DB_PASSWORD=lm \
      LIGHTMOVE_EMAIL_PROVIDER="$MAIL_PROVIDER" \
      JWT_PRIVATE_KEY_LOCATION="file:$PRIVATE_KEY" \
      JWT_PUBLIC_KEY_LOCATION="file:$PUBLIC_KEY" \
      ./mvnw -q spring-boot:run -Dspring-boot.run.profiles="$PROFILE" \
      > "$RUN_DIR/api.log" 2>&1 & echo $! > "$RUN_DIR/api.pid" )

  say "waiting for the API to answer (boot ~40s: Flyway V1-V19 against a virgin database)"
  for _ in $(seq 1 120); do
    if curl -sf -o /dev/null "http://localhost:8080/api/v1/auth/providers"; then break; fi
    if ! kill -0 "$(cat "$RUN_DIR/api.pid")" 2>/dev/null; then
      say "API process died — tail of the log:"; tail -40 "$RUN_DIR/api.log"; exit 1
    fi
    sleep 1
  done
  curl -sf -o /dev/null "http://localhost:8080/api/v1/auth/providers" || {
    say "API never came up — tail of the log:"; tail -60 "$RUN_DIR/api.log"; exit 1; }
fi

# --- 4. gates ---------------------------------------------------------------
say "provider line: $(grep -m1 'Email provider is' "$RUN_DIR/api.log" || echo '(not found)')"
if [ "$EXPECT_PROVIDER" = "log" ]; then
  grep -q "Email provider is 'log'" "$RUN_DIR/api.log" || {
    say "REFUSING TO CONTINUE: the email provider is not 'log'. Real mail would be sent."; exit 1; }
else
  # The mirror image: 08-email-outage.sh proves the failure branch, and it cannot do that against a
  # sender that succeeds. A `log` banner here means the variant did not take.
  if grep -q "Email provider is 'log'" "$RUN_DIR/api.log"; then
    say "REFUSING TO CONTINUE: EXPECT_PROVIDER=blackhole but the provider booted as 'log'."; exit 1
  fi
fi
# The harness reads emailed links out of this log, and lib.sh's link_for separates the `To:` line from
# the link by LINE. logback-spring.xml gives JSON to any profile that is not local/test/e2e, and JSON
# collapses the whole printed box into one escaped string — so a mis-set profile does not fail here, it
# quietly returns no token and every path that starts with an emailed link fails instead. 335 cases once.
if grep -qm1 '^{"time"' "$RUN_DIR/api.log"; then
  say "REFUSING TO CONTINUE: the API is logging JSON, so no emailed token can be read out of it."
  say "Use a profile logback-spring.xml treats as human-readable (local, test, e2e)."
  exit 1
fi

say "migrations: $(grep -m1 'Successfully applied' "$RUN_DIR/api.log" || echo '(already migrated)')"
say "providers:  $(curl -s http://localhost:8080/api/v1/auth/providers)"

# --- 5. web -----------------------------------------------------------------
if curl -sf -o /dev/null "http://localhost:5173/"; then
  say "SPA already up on :5173"
elif [ "${SKIP_WEB:-}" = "1" ]; then
  say "SKIP_WEB=1 — not starting Vite"
else
  say "starting Vite (logs: $RUN_DIR/web.log)"
  ( cd "$REPO_DIR" && npm run dev:web > "$RUN_DIR/web.log" 2>&1 & echo $! > "$RUN_DIR/web.pid" )
  for _ in $(seq 1 60); do
    if curl -sf -o /dev/null "http://localhost:5173/"; then break; fi
    sleep 1
  done
  curl -sf -o /dev/null "http://localhost:5173/" || { say "Vite never came up"; tail -20 "$RUN_DIR/web.log"; }
fi

say "up. api=:8080  web=:5173  db=localhost:$PG_PORT  logs=$RUN_DIR"
