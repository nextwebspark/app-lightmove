#!/usr/bin/env bash
# Phase 2c — the emailed-token lifecycle: verification, resend, and password reset. These tokens are
# the mailbox proof the whole invite-only model rests on, so replay, expiry, purpose-crossing and
# supersession all matter more than the status codes.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "N15  a verification token is single use"

USER_A=$(new_email tok)
post_json /auth/signup "$(jq -nc --arg e "$USER_A" --arg p "$PASSWORD" \
  '{fullName:"Tok A", email:$e, password:$p, termsAccepted:true}')" >/dev/null
TOKEN_A=$(token_for "$USER_A" verify)

post_json /auth/verify "$(jq -nc --arg t "$TOKEN_A" '{token:$t}')"
check_status N15.1 "first redemption" 200
# Redeeming signs in the browser that opened the link, which is usually not the one that signed up.
check N15.1b "and mints a session for it" "true" \
  "$([ -n "$(json '.accessToken')" ] && echo true || echo false)"
post_json /auth/verify "$(jq -nc --arg t "$TOKEN_A" '{token:$t}')"
check_code N15.2 "replaying the same token" 400 TOKEN_INVALID

post_json /auth/verify "$(jq -nc --arg t "not-a-real-token" '{token:$t}')"
check_code N15.3 "a garbage token" 400 TOKEN_INVALID

post_json /auth/verify "$(jq -nc --arg t "" '{token:$t}')"
check_status N15.4 "an empty token" 400

http POST "/auth/verify"
check_status N15.5 "no body at all" 400

post_json /auth/verify "$(jq -nc --arg t "' OR 1=1 --" '{token:$t}')"
check_code N15.6 "SQL-injection shaped token" 400 TOKEN_INVALID

# Login CSRF. This route is CSRF-exempt (it has to work on a first visit, with nothing to echo) and
# now sets the refresh cookie — so a cross-site form POST would plant an attacker's session in a
# visitor's browser, and the SPA adopts it on next boot. Demanding JSON forces the CORS preflight that
# stops the form POST ever reaching the handler.
http POST /auth/verify -H 'Content-Type: application/x-www-form-urlencoded' -d "token=$TOKEN_A"
check_status N15.6b "a cross-site-shaped form POST is refused outright" 415

# The stored value must be a hash, never the token itself.
check N15.7 "tokens are stored as a 64-character SHA-256 hex digest" "64" \
  "$(sql "SELECT length(token_hash) FROM app_lm_verification_token LIMIT 1")"
check N15.8 "no row stores the plaintext token" "0" \
  "$(sql "SELECT count(*) FROM app_lm_verification_token WHERE token_hash = '$TOKEN_A'")"

section "N16  expiry is told apart from invalidity"

USER_B=$(new_email exp)
post_json /auth/signup "$(jq -nc --arg e "$USER_B" --arg p "$PASSWORD" \
  '{fullName:"Tok B", email:$e, password:$p, termsAccepted:true}')" >/dev/null
TOKEN_B=$(token_for "$USER_B" verify)

check N16.1 "the token TTL is 24 hours" "24" \
  "$(sql "SELECT round(extract(epoch from (t.expires_at - t.created_at)) / 3600) FROM app_lm_verification_token t
            JOIN app_lm_user u ON u.id = t.user_id WHERE u.email = '$USER_B'")"

sql_run "UPDATE app_lm_verification_token SET expires_at = now() - interval '1 hour'
         WHERE user_id = (SELECT id FROM app_lm_user WHERE email = '$USER_B')"
post_json /auth/verify "$(jq -nc --arg t "$TOKEN_B" '{token:$t}')"
check_code N16.2 "an expired token has its own code" 400 TOKEN_EXPIRED
check N16.3 "an expired token does not verify the account" "false" \
  "$(sql "SELECT (email_verified_at IS NOT NULL)::text FROM app_lm_user WHERE email = '$USER_B'" | sed 's/true/true/;s/false/false/')"

section "N17  resend supersedes the previous link"

http POST /auth/verify/resend -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$USER_B" '{email:$e}')"
check_status N17.1 "resend for an unverified address" 202
TOKEN_B2=$(token_for "$USER_B" verify)
check N17.2 "the resend produced a different token" "false" \
  "$(test "$TOKEN_B" = "$TOKEN_B2" && echo true || echo false)"

http POST /auth/verify/resend -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$USER_B" '{email:$e}')"
TOKEN_B3=$(token_for "$USER_B" verify)

post_json /auth/verify "$(jq -nc --arg t "$TOKEN_B2" '{token:$t}')"
check_code N17.3 "the superseded link no longer works" 400 TOKEN_INVALID
post_json /auth/verify "$(jq -nc --arg t "$TOKEN_B3" '{token:$t}')"
check_status N17.4 "only the newest link works" 200

MAILS_BEFORE=$(email_count "Confirm your LightMove email")
http POST /auth/verify/resend -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$USER_B" '{email:$e}')"
check_status N17.5 "resend for an ALREADY VERIFIED address still answers 202" 202
check N17.6 "and sends nothing" "$MAILS_BEFORE" "$(email_count "Confirm your LightMove email")"

http POST /auth/verify/resend -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg e "lm-e2e-ghost-$(date +%s)@$MAIL_DOMAIN" '{email:$e}')"
check_status N17.7 "resend for an unknown address answers 202 too" 202
check N17.8 "and sends nothing — no enumeration" "$MAILS_BEFORE" "$(email_count "Confirm your LightMove email")"

section "N18  purposes do not cross"

USER_C=$(new_email cross)
post_json /auth/signup "$(jq -nc --arg e "$USER_C" --arg p "$PASSWORD" \
  '{fullName:"Cross Over", email:$e, password:$p, termsAccepted:true}')" >/dev/null
VERIFY_C=$(token_for "$USER_C" verify)

http POST /auth/password/forgot -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$USER_C" '{email:$e}')"
check_status N18.1 "password reset requested" 202
RESET_C=$(token_for "$USER_C" reset-password)

post_json /auth/verify "$(jq -nc --arg t "$RESET_C" '{token:$t}')"
check_code N18.2 "a reset token offered to /auth/verify" 400 TOKEN_INVALID

post_json /auth/password/reset "$(jq -nc --arg t "$VERIFY_C" --arg p "NewPassw0rd1" '{token:$t, password:$p}')"
check_code N18.3 "a verification token offered to /auth/password/reset" 400 TOKEN_INVALID

check N18.4 "neither cross-use burned the other token" "2" \
  "$(sql "SELECT count(*) FROM app_lm_verification_token t JOIN app_lm_user u ON u.id = t.user_id
          WHERE u.email = '$USER_C' AND t.consumed_at IS NULL")"

section "N19  password reset"

post_json /auth/password/reset "$(jq -nc --arg t "$RESET_C" '{token:$t, password:"weak"}')"
check_code N19.1 "a weak password is refused" 400 VALIDATION_FAILED
post_json /auth/verify "$(jq -nc --arg t "$VERIFY_C" '{token:$t}')" >/dev/null

post_json /auth/password/reset "$(jq -nc --arg t "$RESET_C" --arg p "NewPassw0rd1" '{token:$t, password:$p}')"
check_status N19.2 "the link still worked after the weak-password attempt" 200
check N19.3 "reset logs the user straight in" "false" "$(test -z "$(json '.accessToken')" && echo true || echo false)"

post_json /auth/password/reset "$(jq -nc --arg t "$RESET_C" --arg p "AnotherPass1" '{token:$t, password:$p}')"
check_code N19.4 "the reset link is single use" 400 TOKEN_INVALID

post_json /auth/login "$(jq -nc --arg e "$USER_C" --arg p "$PASSWORD" '{email:$e, password:$p}')"
check_code N19.5 "the old password no longer works" 401 INVALID_CREDENTIALS
post_json /auth/login "$(jq -nc --arg e "$USER_C" --arg p "NewPassw0rd1" '{email:$e, password:$p}')"
check_status N19.6 "the new password works" 200

# A reset must end every other session, or a thief who set the password keeps their own.
RESET_SESSION_JAR=$(jar resetsess)
post_json /auth/login "$(jq -nc --arg e "$USER_C" --arg p "NewPassw0rd1" '{email:$e, password:$p}')" -c "$RESET_SESSION_JAR"
OLD_SESSION=$(refresh_cookie resetsess)
http POST /auth/password/forgot -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$USER_C" '{email:$e}')"
post_json /auth/password/reset "$(jq -nc --arg t "$(token_for "$USER_C" reset-password)" --arg p "ThirdPass123" '{token:$t, password:$p}')"
XSRF=$(csrf_value rs2)
http POST /auth/refresh -b "lm_refresh=$OLD_SESSION; XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF"
check_code N19.7 "a session opened before the reset is revoked" 401 REFRESH_TOKEN_INVALID
check N19.8 "a password change is an expected end, so no theft alert fires" "0" \
  "$(sql "SELECT count(*) FROM app_lm_audit_event a JOIN app_lm_user u ON u.id = a.actor_user_id
          WHERE u.email = '$USER_C' AND a.event_type = 'TOKEN_REUSE_DETECTED'")"

http POST /auth/password/forgot -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg e "lm-e2e-ghost2-$(date +%s)@$MAIL_DOMAIN" '{email:$e}')"
check_status N19.9 "forgot-password for an unknown address" 202

section "N20  reset clears a lockout"

sql_run "UPDATE app_lm_user SET failed_login_attempts = 5, locked_until = now() + interval '15 minutes'
         WHERE email = '$USER_C'"
post_json /auth/login "$(jq -nc --arg e "$USER_C" --arg p "ThirdPass123" '{email:$e, password:$p}')"
check_code N20.1 "locked before the reset — refused as ordinary bad credentials" 401 INVALID_CREDENTIALS

http POST /auth/password/forgot -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$USER_C" '{email:$e}')"
post_json /auth/password/reset "$(jq -nc --arg t "$(token_for "$USER_C" reset-password)" --arg p "FourthPas12" '{token:$t, password:$p}')"
check N20.2 "the reset cleared the lock" "0" \
  "$(sql "SELECT failed_login_attempts FROM app_lm_user WHERE email = '$USER_C'")"
post_json /auth/login "$(jq -nc --arg e "$USER_C" --arg p "FourthPas12" '{email:$e, password:$p}')"
check_status N20.3 "login works again straight away" 200

summary
