#!/usr/bin/env bash
# The whole matrix, in one command, with the boot variants it needs.
#
#   ./run-all.sh              # as a developer, on the `local` profile
#   PROFILE=e2e ./run-all.sh  # as CI, on the committed profile
#
# Exits non-zero if any script reported a failing case. That is the only reason this file exists in
# preference to the README's `for s in api/0*.sh` loop, which missed 13-client-access-tiers.sh,
# ignored exit codes, and left the two scripts that need a *different* API to the reader.
#
# Three legs, because three scripts cannot share one boot:
#
#   1. ordinary   — provider=log, generous rate budgets. Everything except 07 and 08.
#   2. throttled  — tightened rate budgets. 07 only, and the restart is also what empties the
#                   in-memory buckets a previous leg filled.
#   3. blackhole  — provider=resend aimed at a dead loopback port, so sending FAILS. 08 only.
#
# Ordering inside leg 1 is load-bearing:
#   - fixtures.sh writes cast.env, which 09-13 and spa/roles.mjs all source.
#   - 10-role-invariants.sh is NOT idempotent (it promotes, demotes and removes people) and its
#     last-admin cases only mean anything against a workspace with exactly one admin, so the cast is
#     rebuilt immediately before it.
#   - 12-tenant-isolation.sh deletes a workspace and goes last.
set -uo pipefail

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="${RUN_DIR:-$E2E_DIR/results/current}"
export RUN_DIR

FAILED_LEGS=""

say()  { printf '\n\033[1;35m[run-all]\033[0m %s\n' "$*"; }

# Runs one script and remembers its name if it failed. Never aborts: a red 03 must not hide 04-13,
# which is the same reason the individual assertions do not abort either.
step() {
  local script="$1"
  say "→ $script"
  if ! bash "$E2E_DIR/$script"; then
    FAILED_LEGS="$FAILED_LEGS $script"
  fi
}

step_node() {
  local script="$1"
  say "→ $script"
  # Playwright resolves relative to the process cwd for its browser download, and the scripts default
  # RUN_DIR from their own location, so e2e/ is the documented working directory.
  if ! ( cd "$E2E_DIR" && node "$script" ); then
    FAILED_LEGS="$FAILED_LEGS $script"
  fi
}

# A previous run's cases.tsv would be counted as part of this one — the tally is what the findings
# reports are built from, so it starts empty.
mkdir -p "$RUN_DIR"
rm -f "$RUN_DIR/cases.tsv"

trap 'say "interrupted — tearing the stack down"; "$E2E_DIR/stack/down.sh" || true; exit 130' INT TERM

# --- leg 1: the ordinary stack ----------------------------------------------
# Torn down first: up.sh leaves an API that is already answering on :8080 alone, which is the right
# behaviour for a developer and the wrong one here — a full run must not inherit whatever variant the
# last one left behind.
say "clearing anything left over from a previous run"
"$E2E_DIR/stack/down.sh" >/dev/null 2>&1 || true

say "leg 1/3 — ordinary stack (provider=log, generous rate budgets)"
# A stack that never came up is not a test result. Running the scripts anyway turns one boot failure
# into several hundred cases that all failed with 000, which buries the four lines that say why —
# a missing JWT keypair on a runner read as 466 assertion failures once.
if ! "$E2E_DIR/stack/up.sh"; then
  say "the stack never came up — see $RUN_DIR/api.log. Not running leg 1; every case would fail with 000."
  "$E2E_DIR/stack/down.sh" || true
  exit 1
fi

for script in api/01-happy-path.sh \
              api/02-signup-validation.sh \
              api/03-login-lockout.sh \
              api/04-tokens-verification.sh \
              api/05-session-csrf.sh \
              api/06-onboarding-edges.sh; do
  step "$script"
done

step api/fixtures.sh
step api/09-workspace-roles.sh
step api/11-client-access.sh
step api/13-client-access-tiers.sh

# 14 and spa/strategy.mjs build their own cast and need the Apollo universe, which is pulled with
# gcloud and is therefore absent on a runner. Both skip themselves and exit 0 when the table is empty,
# so they cost a second here and do real work on a laptop that has run `npm run dev:db:apollo`.
step api/14-strategy-company-search.sh

# 15 needs no Apollo universe — an extension capture files a company by name, never by universe id —
# so it does its full work on a runner.
step api/15-extension-capture.sh

step_node spa/run.mjs
step_node spa/roles.mjs
step_node spa/strategy.mjs

# 10 mutates the cast it runs against, so it gets a fresh one. 12 goes after it and last.
step api/fixtures.sh
step api/10-role-invariants.sh
step api/12-tenant-isolation.sh

# --- leg 2: tightened rate budgets ------------------------------------------
say "leg 2/3 — restarting with tightened rate budgets for 07"
KEEP_DB=1 "$E2E_DIR/stack/down.sh"
if EXTRA_ENV="LIGHTMOVE_AUTH_RATE_LIMIT_LOGIN_ATTEMPTS_PER_MINUTE=3 \
              LIGHTMOVE_AUTH_RATE_LIMIT_SIGNUP_ATTEMPTS_PER_HOUR=5 \
              LIGHTMOVE_AUTH_RATE_LIMIT_VERIFICATION_RESENDS_PER_HOUR=2 \
              LIGHTMOVE_AUTH_RATE_LIMIT_PASSWORD_RESET_REQUESTS_PER_HOUR=2" \
     SKIP_WEB=1 "$E2E_DIR/stack/up.sh"; then
  step api/07-rate-limits.sh
else
  # Reported and skipped rather than aborting: legs 1 and 3 are independent, and their results are
  # worth having even when this variant will not boot.
  say "the throttled stack never came up — skipping 07"
  FAILED_LEGS="$FAILED_LEGS stack/up.sh(leg2)"
fi

# --- leg 3: a mail provider that cannot deliver -----------------------------
# Port 9 is discard and nothing listens, so the connection is refused on this machine. A bogus key
# alone would still put the request on the wire to Resend; up.sh refuses this variant unless the base
# URL really is loopback.
say "leg 3/3 — restarting with a black-holed mail provider for 08"
KEEP_DB=1 "$E2E_DIR/stack/down.sh"
if EXPECT_PROVIDER=blackhole \
   EXTRA_ENV="LIGHTMOVE_EMAIL_RESEND_API_KEY=re_e2e_bogus_key_not_a_real_credential \
              LIGHTMOVE_EMAIL_RESEND_BASE_URL=http://127.0.0.1:9" \
   SKIP_WEB=1 "$E2E_DIR/stack/up.sh"; then
  step api/08-email-outage.sh
else
  say "the black-holed stack never came up — skipping 08"
  FAILED_LEGS="$FAILED_LEGS stack/up.sh(leg3)"
fi

# --- teardown and tally -----------------------------------------------------
say "tearing the stack down"
"$E2E_DIR/stack/down.sh"

# `|| true`, not `|| echo 0`: grep -c already prints 0 when nothing matches, and exits 1 while doing
# it — the fallback would append a second line to the count.
PASSED=$(grep -c $'\tPASS\t' "$RUN_DIR/cases.tsv" 2>/dev/null || true)
FAILED=$(grep -c $'\tFAIL\t' "$RUN_DIR/cases.tsv" 2>/dev/null || true)
printf '\n\033[1;36m======== %s passed, %s failed ========\033[0m\n' "$PASSED" "$FAILED"

if [ -n "$FAILED_LEGS" ]; then
  printf '\033[31mfailing scripts:%s\033[0m\n' "$FAILED_LEGS"
  exit 1
fi
say "all green"
