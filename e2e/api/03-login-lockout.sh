#!/usr/bin/env bash
# Phase 2b — login: the vague-error promise, account lockout, status gates, and the OAuth-only
# account. Several cases need a state no endpoint can set (SUSPENDED, a null password hash), which is
# why the matrix runs against a disposable database.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

login() { post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$2" '{email:$e, password:$p}')"; }

VICTIM=$(new_email login)
post_json /auth/signup "$(jq -nc --arg e "$VICTIM" --arg p "$PASSWORD" \
  '{fullName:"Vic Tim", email:$e, password:$p, termsAccepted:true}')" >/dev/null
post_json /auth/verify "$(jq -nc --arg t "$(token_for "$VICTIM" verify)" '{token:$t}')" >/dev/null

section "N9  login does not leak which accounts exist"

UNKNOWN="lm-e2e-nobody-$(date +%s)$RANDOM@$MAIL_DOMAIN"
login "$UNKNOWN" "$PASSWORD"
UNKNOWN_STATUS="$LAST_STATUS"; UNKNOWN_BODY=$(printf '%s' "$LAST_BODY" | jq -S 'del(.timestamp,.correlationId)')
check_code N9.1 "unknown address" 401 INVALID_CREDENTIALS

login "$VICTIM" "WrongPassword9"
KNOWN_STATUS="$LAST_STATUS"; KNOWN_BODY=$(printf '%s' "$LAST_BODY" | jq -S 'del(.timestamp,.correlationId)')
check_code N9.2 "known address, wrong password" 401 INVALID_CREDENTIALS
check N9.3 "the two responses are byte-identical" "$UNKNOWN_BODY" "$KNOWN_BODY"

# A known address runs BCrypt(12); an unknown one returns before hashing. That difference is
# measurable and is an account-existence oracle no matter how identical the bodies are.
time_login() {
  local start end
  start=$(python3 -c 'import time;print(int(time.time()*1000))')
  login "$1" "$2" >/dev/null
  end=$(python3 -c 'import time;print(int(time.time()*1000))')
  echo $((end - start))
}
UNKNOWN_MS=0; KNOWN_MS=0
for _ in 1 2 3; do
  UNKNOWN_MS=$((UNKNOWN_MS + $(time_login "$UNKNOWN" "$PASSWORD")))
  KNOWN_MS=$((KNOWN_MS + $(time_login "$VICTIM" "WrongPassword9")))
done
UNKNOWN_MS=$((UNKNOWN_MS / 3)); KNOWN_MS=$((KNOWN_MS / 3))
note N9.4 "timing: unknown address ${UNKNOWN_MS}ms vs real address ${KNOWN_MS}ms"
# Both paths must now pay for one BCrypt comparison (PasswordPolicy.equaliseFailureCost). Allowing 2x
# rather than demanding equality: this measures wall clock over a live HTTP round trip, so scheduling
# noise is expected. The bug being guarded against was a ~10x gap.
check N9.5 "an unknown address costs about as much as a real one" "true" \
  "$(test "$KNOWN_MS" -lt $((UNKNOWN_MS * 2)) && echo true || echo false)"

section "N10  lockout"

# The counter already holds the failures from N9. Reset it so the threshold is measured from zero.
sql_run "UPDATE app_lm_user SET failed_login_attempts = 0, locked_until = NULL WHERE email = '$VICTIM'"

for i in 1 2 3 4 5; do
  login "$VICTIM" "WrongPassword9"
  if [ "$i" -lt 5 ]; then
    check_code "N10.$i" "failed attempt $i of 5" 401 INVALID_CREDENTIALS
  else
    note "N10.5" "attempt 5 returned $LAST_STATUS $(ecode)"
  fi
done
check N10.6 "the account is locked in the database" "5" \
  "$(sql "SELECT failed_login_attempts FROM app_lm_user WHERE email = '$VICTIM'")"

# A locked account must answer exactly what an unknown address answers. It used to answer 423, which
# was reachable only for an address that exists — five wrong guesses confirmed an account.
login "$VICTIM" "WrongPassword9"
check_code N10.7 "a further attempt while locked" 401 INVALID_CREDENTIALS
LOCKED_BODY=$(printf '%s' "$LAST_BODY" | jq -S 'del(.timestamp,.correlationId)')

login "$VICTIM" "$PASSWORD"
check_code N10.8 "the CORRECT password while locked is still refused" 401 INVALID_CREDENTIALS

login "$UNKNOWN" "$PASSWORD"
check N10.9 "a locked account is indistinguishable from an unknown address" \
  "$LOCKED_BODY" "$(printf '%s' "$LAST_BODY" | jq -S 'del(.timestamp,.correlationId)')"

# The lockout still has to reach the owner — the login response deliberately cannot say it, so email
# is the only channel left. Once when the lock arms, not once per guess.
check N10.10 "the owner is told by email that the account locked" "1" "$(email_count "temporarily locked")"

login "$VICTIM" "WrongPassword9" >/dev/null
login "$VICTIM" "WrongPassword9" >/dev/null
check N10.11 "and only once, not once per guess an attacker makes" "1" "$(email_count "temporarily locked")"
check N10.12 "the lockout is still recorded in the audit trail" "t" \
  "$(await_sql "SELECT count(*) > 0 FROM app_lm_audit_event a JOIN app_lm_user u ON u.id = a.actor_user_id
                WHERE u.email = '$VICTIM' AND a.event_type = 'ACCOUNT_LOCKED'" t)"

section "N11  the lock expires but the counter does not"

sql_run "UPDATE app_lm_user SET locked_until = now() - interval '1 minute' WHERE email = '$VICTIM'"
check N11.1 "the failure counter survived the lock expiry" "5" \
  "$(sql "SELECT failed_login_attempts FROM app_lm_user WHERE email = '$VICTIM'")"

login "$VICTIM" "WrongPassword9"
note N11.2 "one wrong password after the lock lapsed -> $LAST_STATUS $(ecode)"
check N11.3 "the account re-locked immediately on a single failure" "true" \
  "$(test "$(sql "SELECT locked_until > now() FROM app_lm_user WHERE email = '$VICTIM'")" = "t" && echo true || echo false)"

sql_run "UPDATE app_lm_user SET locked_until = NULL WHERE email = '$VICTIM'"
login "$VICTIM" "$PASSWORD"
check_status N11.4 "a successful login clears the lock state" 200
check N11.5 "the counter is zeroed by success" "0" \
  "$(sql "SELECT failed_login_attempts FROM app_lm_user WHERE email = '$VICTIM'")"

section "N12  account status gates"

# Nothing in src/main sets these statuses yet — there is no suspension endpoint. The rows are written
# directly here so the login gate is exercised ahead of whoever builds that feature.
login "$UNKNOWN" "$PASSWORD"
UNKNOWN_BODY=$(printf '%s' "$LAST_BODY" | jq -S 'del(.timestamp,.correlationId)')

sql_run "UPDATE app_lm_user SET status = 'SUSPENDED' WHERE email = '$VICTIM'"
login "$VICTIM" "$PASSWORD"
check_code N12.1 "SUSPENDED account with the right password" 401 INVALID_CREDENTIALS
check N12.2 "and is indistinguishable from an unknown address" \
  "$UNKNOWN_BODY" "$(printf '%s' "$LAST_BODY" | jq -S 'del(.timestamp,.correlationId)')"

sql_run "UPDATE app_lm_user SET status = 'DELETED' WHERE email = '$VICTIM'"
login "$VICTIM" "$PASSWORD"
check_code N12.3 "DELETED account" 401 INVALID_CREDENTIALS

check N12.4 "the real reason is still recorded in the audit trail" "status_DELETED" \
  "$(await_sql "SELECT metadata->>'reason' FROM app_lm_audit_event a JOIN app_lm_user u ON u.id = a.actor_user_id
                WHERE u.email = '$VICTIM' AND a.event_type = 'LOGIN_FAILED' ORDER BY a.occurred_at DESC LIMIT 1" status_DELETED)"

# An access token minted before suspension outlives it, because access tokens are stateless with a
# 15-minute TTL. Recorded rather than asserted: there is no suspension feature to hang a fix on, and
# whoever builds one has to revoke sessions as part of it.
sql_run "UPDATE app_lm_user SET status = 'ACTIVE' WHERE email = '$VICTIM'"
login "$VICTIM" "$PASSWORD"
LIVE_TOKEN=$(json '.accessToken')
sql_run "UPDATE app_lm_user SET status = 'SUSPENDED' WHERE email = '$VICTIM'"
get /auth/me -H "$(auth_header "$LIVE_TOKEN")"
note N12.5 "a token minted before suspension answers $LAST_STATUS on /auth/me — suspension must revoke sessions when it is built"
sql_run "UPDATE app_lm_user SET status = 'ACTIVE' WHERE email = '$VICTIM'"

section "N13  an account with no local password"

OAUTHY=$(new_email oauth)
post_json /auth/signup "$(jq -nc --arg e "$OAUTHY" --arg p "$PASSWORD" \
  '{fullName:"Olive Oauth", email:$e, password:$p, termsAccepted:true}')" >/dev/null
sql_run "UPDATE app_lm_user SET password_hash = NULL WHERE email = '$OAUTHY'"

login "$OAUTHY" "$PASSWORD"
check_code N13.1 "password login against a Google-only account" 401 INVALID_CREDENTIALS
check N13.2 "audited with a reason that names the cause" "no_local_password" \
  "$(await_sql "SELECT metadata->>'reason' FROM app_lm_audit_event WHERE event_type = 'LOGIN_FAILED' ORDER BY occurred_at DESC LIMIT 1" no_local_password)"
check N13.3 "the failure still counts toward lockout" "1" \
  "$(sql "SELECT failed_login_attempts FROM app_lm_user WHERE email = '$OAUTHY'")"

section "N14  login request shapes"

post_json /auth/login '{"email":"","password":""}'
check_code N14.1 "blank email and password" 400 VALIDATION_FAILED

post_json /auth/login '{"password":"x"}'
check_code N14.2 "missing email" 400 VALIDATION_FAILED

post_json /auth/login "$(jq -nc --arg e "$VICTIM" '{email:$e, password:"'"'"' OR 1=1 --"}')"
check_code N14.3 "SQL-injection shaped password" 401 INVALID_CREDENTIALS

login "$(printf 'x%.0s' $(seq 1 300))@$MAIL_DOMAIN" "$PASSWORD"
note N14.4 "300-character local part -> $LAST_STATUS $(ecode)"

summary
