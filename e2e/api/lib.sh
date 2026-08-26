#!/usr/bin/env bash
# Shared helpers for the curl-driven auth matrix. Sourced by every 0*.sh script.
#
# Deliberately bash-3.2 compatible (macOS ships 3.2, so no associative arrays and no ${x,,}).
#
# The assertion helpers never abort the run: a failed case is recorded and the script continues, so
# one broken expectation does not hide the twenty cases behind it. Every case also appends a line to
# results/current/cases.tsv, which is what the findings report is built from.

API="${API:-http://localhost:8080/api/v1}"
WEB="${WEB:-http://localhost:5173}"
E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="${RUN_DIR:-$E2E_DIR/results/current}"
API_LOG="$RUN_DIR/api.log"
CASES_TSV="$RUN_DIR/cases.tsv"
PG_URL="${PG_URL:-postgresql://lm_app:lm@localhost:55432/lightmove}"

mkdir -p "$RUN_DIR"

PASS_COUNT=0
FAIL_COUNT=0

C_OK=$'\033[32m'; C_BAD=$'\033[31m'; C_DIM=$'\033[2m'; C_HEAD=$'\033[1;36m'; C_OFF=$'\033[0m'

section() { printf '\n%s== %s%s\n' "$C_HEAD" "$*" "$C_OFF"; }

# --- HTTP -------------------------------------------------------------------

LAST_STATUS=""
LAST_BODY=""
LAST_HEADERS=""

# An isolated cookie jar per identity, so one user's refresh cookie never leaks into another's calls.
jar() { printf '%s/jar-%s.txt' "$RUN_DIR" "$1"; }

# http METHOD PATH [extra curl args...]
http() {
  local method="$1" path="$2"
  shift 2
  local body_file="$RUN_DIR/.body" head_file="$RUN_DIR/.head"
  LAST_STATUS=$(curl -sS -o "$body_file" -D "$head_file" -w '%{http_code}' \
                     -X "$method" "$API$path" "$@" 2>>"$RUN_DIR/curl.err")
  LAST_BODY=$(cat "$body_file")
  LAST_HEADERS=$(cat "$head_file")
}

# Convenience wrappers for the two shapes we send constantly.
post_json() { local path="$1" data="$2"; shift 2; http POST "$path" -H 'Content-Type: application/json' -d "$data" "$@"; }
get()       { local path="$1"; shift; http GET "$path" "$@"; }

json()  { printf '%s' "$LAST_BODY" | jq -r "$1" 2>/dev/null; }
ecode() { json '.code // empty'; }

# Header value from the last response, e.g. header 'set-cookie'
header() { printf '%s' "$LAST_HEADERS" | tr -d '\r' | grep -i "^$1:" | sed "s/^[^:]*: *//"; }

# --- auth material ----------------------------------------------------------

# Bearer header for a token: auth_header "$TOKEN"
auth_header() { printf 'Authorization: Bearer %s' "$1"; }

# Decode a JWT payload. Base64url with no padding, so pad it back before decoding.
jwt_claims() {
  local payload
  payload=$(printf '%s' "$1" | cut -d. -f2 | tr '_-' '/+')
  case $(( ${#payload} % 4 )) in
    2) payload="$payload==" ;;
    3) payload="$payload=" ;;
  esac
  printf '%s' "$payload" | base64 -d 2>/dev/null | jq .
}

# The double-submit CSRF pair for a jar: primes the cookie, then echoes the header argument.
# Usage: http POST /auth/refresh -b "$(jar u1)" -c "$(jar u1)" -H "$(csrf_header u1)"
csrf_header() { printf 'X-XSRF-TOKEN: %s' "$(csrf_value "$1")"; }

# The bare token. Needed whenever the request passes cookies inline with -b "name=value", because
# that form replaces the jar entirely and would otherwise drop the XSRF-TOKEN cookie — which reads
# as a CSRF failure and masks whatever the case was actually testing.
csrf_value() {
  local name="$1"
  curl -sS -o /dev/null -c "$(jar "$name")" -b "$(jar "$name")" "$API/auth/csrf"
  awk '/XSRF-TOKEN/{print $7}' "$(jar "$name")" | tail -1
}

# The refresh cookie value currently in a jar (for replay / theft-detection cases).
refresh_cookie() { awk '/lm_refresh/{print $7}' "$(jar "$1")" | tail -1; }

# --- emails -------------------------------------------------------------------

# The most recent link of a kind printed by LogEmailSender. kind: verify | reset-password | accept-invite
latest_link() {
  local kind="${1:-verify}"
  grep -oE "http://localhost:5173/(auth/)?$kind\?token=[A-Za-z0-9_%.~-]+" "$API_LOG" | tail -1
}

latest_token() { latest_link "$1" | sed 's/.*token=//'; }

# The link from the most recent email addressed to a specific recipient. LogEmailSender prints a box
# whose `To:` line precedes the body, so the last To: seen before a link is that link's recipient.
link_for() { # link_for EMAIL KIND
  awk -v addr="$1" -v kind="${2:-accept-invite}" '
      index($0, "To:") && index($0, addr) { mine = 1; next }
      index($0, "To:")                    { mine = 0 }
      mine && index($0, kind "?token=")   { print }
    ' "$API_LOG" \
  | grep -oE "http://localhost:5173/auth/${2:-accept-invite}\?token=[A-Za-z0-9_%.~-]+" | tail -1
}

token_for() { link_for "$1" "${2:-accept-invite}" | sed 's/.*token=//'; }

# How many emails with this subject fragment have been printed so far.
email_count() { grep -c "Subject: .*$1" "$API_LOG" 2>/dev/null || echo 0; }

# --- database ---------------------------------------------------------------

sql()     { psql "$PG_URL" -Atc "$1"; }
sql_run() { psql "$PG_URL" -q -c "$1" >/dev/null; }

# Audit rows are written on another thread: AuditEventWriter is @Async, deliberately, so that
# recording an event never adds latency to — or fails — the request it observes. The row therefore
# lands shortly AFTER the response that triggered it, and a query fired the instant curl returns can
# beat the writer to the database.
#
# That race went red on 2026-08-24: N34.3 read the RATE_LIMIT_EXCEEDED table and found nothing, while
# N34.4 read the very same row 33ms later and passed. Poll for the expected value instead of reading
# once. Echoes whatever was last read, so a row that never arrives still fails with the real value.
await_sql() { # await_sql QUERY EXPECTED [ATTEMPTS]   (default 40 x 50ms = 2s ceiling)
  local attempts=${3:-40} got
  while :; do
    got=$(sql "$1")
    [ "$got" = "$2" ] && break
    attempts=$((attempts - 1))
    [ "$attempts" -le 0 ] && break
    sleep 0.05
  done
  printf '%s' "$got"
}

# --- assertions -------------------------------------------------------------

record() { # record ID RESULT DETAIL
  printf '%s\t%s\t%s\n' "$1" "$2" "$3" >> "$CASES_TSV"
}

pass() { PASS_COUNT=$((PASS_COUNT+1)); printf '  %sPASS%s %-6s %s\n' "$C_OK" "$C_OFF" "$1" "$2"; record "$1" PASS "$2"; }
fail() { FAIL_COUNT=$((FAIL_COUNT+1)); printf '  %sFAIL%s %-6s %s\n%s       %s%s\n' "$C_BAD" "$C_OFF" "$1" "$2" "$C_DIM" "$3" "$C_OFF"; record "$1" FAIL "$2 -- $3"; }

# A case whose observed behaviour is worth reporting but is not a pass/fail judgement.
note() { printf '  %sNOTE%s %-6s %s\n' "$C_DIM" "$C_OFF" "$1" "$2"; record "$1" NOTE "$2"; }
# Neither a pass nor a failure: the case could not run at all. A precondition this environment cannot
# meet must not go red — a suite that cries wolf nightly gets switched off rather than fixed — but it
# must not go quietly green either, or "0 failed" reads as coverage nobody actually got.
skip() { printf '  %sSKIP%s %-6s %s\n%s       %s%s\n' "$C_DIM" "$C_OFF" "$1" "$2" "$C_DIM" "$3" "$C_OFF"; record "$1" SKIP "$2 -- $3"; }

# check ID DESCRIPTION EXPECTED ACTUAL
check() {
  if [ "$3" = "$4" ]; then pass "$1" "$2"; else fail "$1" "$2" "expected [$3] got [$4]"; fi
}

# check_status ID DESCRIPTION EXPECTED_HTTP_STATUS  (uses LAST_STATUS)
check_status() { check "$1" "$2 -> $3" "$3" "$LAST_STATUS"; }

# check_code ID DESCRIPTION EXPECTED_STATUS EXPECTED_CODE
check_code() {
  local got_status="$LAST_STATUS" got_code
  got_code=$(ecode)
  if [ "$got_status" = "$3" ] && [ "$got_code" = "$4" ]; then
    pass "$1" "$2 -> $3 $4"
  else
    fail "$1" "$2" "expected [$3 $4] got [$got_status $got_code] body: $(printf '%s' "$LAST_BODY" | head -c 300)"
  fi
}

check_contains() {
  case "$4" in
    *"$3"*) pass "$1" "$2" ;;
    *)      fail "$1" "$2" "expected to contain [$3] in [$(printf '%s' "$4" | head -c 300)]" ;;
  esac
}

# Prints the tally and RETURNS it. Every script ends with `summary`, so its exit status becomes the
# script's — which is what lets run-all.sh and the nightly workflow tell a red run from a green one.
# The individual assertions still never abort, so one broken expectation does not hide the twenty
# behind it; only the script as a whole reports failure, at the end.
summary() {
  printf '\n%s---- %s: %s%d passed%s, %s%d failed%s\n' \
    "$C_HEAD" "$(basename "${BASH_SOURCE[1]:-run}")" "$C_OK" "$PASS_COUNT" "$C_OFF" \
    "$C_BAD" "$FAIL_COUNT" "$C_OFF"
  [ "$FAIL_COUNT" -eq 0 ]
}

# --- identities -------------------------------------------------------------

# Throwaway addresses must sit on a domain with real MX records: the validator's MX check is on by
# default and a made-up domain is rejected before anything interesting is reached.
MAIL_DOMAIN="${MAIL_DOMAIN:-nextwebspark.com}"
PASSWORD="${PASSWORD:-Passw0rd123}"

# Lower-case only: the API normalises addresses on the way in, so an upper-case character here would
# make every later `WHERE email = ...` and every grep of the mail log miss.
new_email() { printf 'lm-e2e-%s-%s%s@%s' "${1:-u}" "$(date +%s)" "$RANDOM" "$MAIL_DOMAIN"; }
