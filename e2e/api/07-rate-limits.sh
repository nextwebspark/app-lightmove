#!/usr/bin/env bash
# Phase 2f — rate limiting. The limiter is switched OFF in the Java test profile and has no unit test,
# so nothing in the repo proves it works; this is the only place it is exercised end to end.
#
# REQUIRES the API to have been restarted with tightened limits:
#   EXTRA_ENV="AUTH_LOGIN_ATTEMPTS_PER_MINUTE=3 AUTH_SIGNUP_ATTEMPTS_PER_HOUR=5 \
#              AUTH_VERIFICATION_RESENDS_PER_HOUR=2 AUTH_PASSWORD_RESET_REQUESTS_PER_HOUR=2"
# The buckets are in-memory, so a restart is also how they are reset.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

LOGIN_LIMIT=3
RESEND_LIMIT=2
RESET_LIMIT=2
SIGNUP_LIMIT=5

login() { post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$2" '{email:$e, password:$p}')" "${@:3}"; }

VICTIM=$(new_email rl)
post_json /auth/signup "$(jq -nc --arg e "$VICTIM" --arg p "$PASSWORD" \
  '{fullName:"Rae Limit", email:$e, password:$p, termsAccepted:true}')" >/dev/null
post_json /auth/verify "$(jq -nc --arg t "$(token_for "$VICTIM" verify)" '{token:$t}')" >/dev/null

section "N34  login is rate limited, and the limit precedes the credential check"

for i in $(seq 1 $LOGIN_LIMIT); do
  login "$VICTIM" "WrongPassword9" >/dev/null
done
login "$VICTIM" "WrongPassword9"
check_code N34.1 "the attempt after the per-minute budget" 429 RATE_LIMITED

# The CORRECT password must also be refused, or the limiter is only slowing down wrong guesses.
login "$VICTIM" "$PASSWORD"
check_code N34.2 "the correct password while rate limited" 429 RATE_LIMITED

check N34.3 "the rejection is audited" "t" \
  "$(await_sql "SELECT count(*) > 0 FROM app_lm_audit_event WHERE event_type = 'RATE_LIMIT_EXCEEDED'" t)"

EXHAUSTED=$(sql "SELECT metadata->>'exhausted' FROM app_lm_audit_event WHERE event_type = 'RATE_LIMIT_EXCEEDED' ORDER BY occurred_at DESC LIMIT 1")
check N34.4 "the audit row names which bucket was exhausted" "true" \
  "$(test -n "$EXHAUSTED" && echo true || echo false)"
note N34.5 "exhausted bucket recorded as: $EXHAUSTED"

check N34.6 "the throttled attempts did NOT count toward account lockout" "true" \
  "$(test "$(sql "SELECT failed_login_attempts FROM app_lm_user WHERE email = '$VICTIM'")" -le "$LOGIN_LIMIT" && echo true || echo false)"

section "N35  a forged X-Forwarded-For cannot buy a fresh IP budget"

# trusted-proxy-count defaults to 0, so the header must be ignored entirely. If it were honoured, an
# attacker would rotate it per request and the IP bucket would be worthless.
login "$VICTIM" "WrongPassword9" -H "X-Forwarded-For: 203.0.113.$RANDOM"
check_code N35.1 "a request carrying a forged X-Forwarded-For" 429 RATE_LIMITED
login "$VICTIM" "WrongPassword9" -H "X-Real-IP: 198.51.100.7"
check_code N35.2 "a request carrying a forged X-Real-IP" 429 RATE_LIMITED

section "N36  a different address from the same IP shares the IP budget"

OTHER=$(new_email rl2)
login "$OTHER" "$PASSWORD"
check_code N36.1 "an untouched address from an exhausted IP" 429 RATE_LIMITED
note N36.2 "the IP bucket is shared across addresses, so one noisy client throttles everyone behind that IP"

section "N37  the endpoints that are deliberately NOT limited"

# Redeeming an emailed token is not limited: the token is already 256 bits of proof, and throttling
# it would let anybody lock a stranger out of their own verification link.
for _ in 1 2 3 4 5 6; do post_json /auth/verify "$(jq -nc --arg t "bogus-$RANDOM" '{token:$t}')" >/dev/null; done
post_json /auth/verify "$(jq -nc --arg t "bogus-final" '{token:$t}')"
check_code N37.1 "repeated verification redemptions are not throttled" 400 TOKEN_INVALID

XSRF=$(csrf_value rl)
for _ in 1 2 3 4 5 6; do
  http POST /auth/refresh -b "lm_refresh=bogus; XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF" >/dev/null
done
http POST /auth/refresh -b "lm_refresh=bogus; XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF"
check_code N37.2 "repeated refresh attempts are not throttled" 401 REFRESH_TOKEN_INVALID
note N37.3 "an unlimited /auth/refresh is an unthrottled oracle for guessing a 256-bit token — infeasible to brute force, but also unmetered"

section "N38  verification resend"

RESEND_TARGET=$(new_email resend)
post_json /auth/signup "$(jq -nc --arg e "$RESEND_TARGET" --arg p "$PASSWORD" \
  '{fullName:"Res End", email:$e, password:$p, termsAccepted:true}')" >/dev/null

for _ in $(seq 1 $RESEND_LIMIT); do
  http POST /auth/verify/resend -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$RESEND_TARGET" '{email:$e}')" >/dev/null
done
http POST /auth/verify/resend -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$RESEND_TARGET" '{email:$e}')"
check_code N38.1 "resend past the hourly budget" 429 RATE_LIMITED
note N38.2 "a 429 here distinguishes a real unverified address from an unknown one only if the budget is per-email; the IP budget burns either way"

section "N39  password reset requests"

for _ in $(seq 1 $RESET_LIMIT); do
  http POST /auth/password/forgot -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$VICTIM" '{email:$e}')" >/dev/null
done
http POST /auth/password/forgot -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$VICTIM" '{email:$e}')"
check_code N39.1 "forgot-password past the hourly budget" 429 RATE_LIMITED
note N39.2 "production default is 3/hour and application-local.yml does not raise it — the one limit a developer meets by accident"

section "N40  signup"

for _ in $(seq 1 $SIGNUP_LIMIT); do
  post_json /auth/signup "$(jq -nc --arg e "$(new_email flood)" --arg p "$PASSWORD" \
    '{fullName:"Flo Od", email:$e, password:$p, termsAccepted:true}')" >/dev/null
done
USERS_BEFORE=$(sql "SELECT count(*) FROM app_lm_user")
THROTTLED_EMAIL=$(new_email flood)
post_json /auth/signup "$(jq -nc --arg e "$THROTTLED_EMAIL" --arg p "$PASSWORD" \
  '{fullName:"Flo Od", email:$e, password:$p, termsAccepted:true}')"
check_code N40.1 "signup past the hourly budget" 429 RATE_LIMITED
check N40.2 "the throttled signup created no user" "$USERS_BEFORE" "$(sql "SELECT count(*) FROM app_lm_user")"
check N40.3 "and the address it was refused for is still free" "0" \
  "$(sql "SELECT count(*) FROM app_lm_user WHERE email = '$THROTTLED_EMAIL'")"

note N40.3 "the limiter is in-memory (Caffeine) and per instance — every limit above multiplies by the number of Cloud Run instances"

summary
