#!/usr/bin/env bash
# Phase 2a — everything that should be refused at the signup door: password policy, terms, duplicate
# addresses, the email-domain gates, normalisation, and malformed requests.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

signup() { # signup EMAIL PASSWORD TERMS [FULLNAME]
  post_json /auth/signup "$(jq -nc --arg e "$1" --arg p "$2" --argjson t "$3" --arg n "${4-Test User}" \
    '{fullName:$n, email:$e, password:$p, termsAccepted:$t}')"
}

field_error() { json '.fieldErrors.password // .fieldErrors.fullName // .fieldErrors.email // .fieldErrors.termsAccepted // empty'; }

section "N1  password policy"

signup "$(new_email pw1)" "Short1" true
check_code N1.1 "7-character password" 400 VALIDATION_FAILED
check N1.2 "the field error names the length rule" "Use at least 8 characters" "$(field_error)"

signup "$(new_email pw2)" "nodigitshere" true
check_code N1.3 "password with no digit" 400 VALIDATION_FAILED
check N1.4 "the field error names the digit rule" "Include at least one number" "$(field_error)"

signup "$(new_email pw3)" "$(printf 'a%.0s' $(seq 1 72))1" true
check_code N1.5 "73-character password (past the BCrypt 72-byte ceiling)" 400 VALIDATION_FAILED
note N1.6 "73-char rejection message: $(field_error)"

signup "$(new_email pw4)" "" true
check_code N1.7 "empty password" 400 VALIDATION_FAILED

# BCrypt's ceiling is 72 *bytes*. A policy that counted characters let this past validation and into
# the encoder, which threw IllegalArgumentException and turned signup into a 500.
MULTIBYTE=$(printf 'é%.0s' $(seq 1 40))1
signup "$(new_email pw5)" "$MULTIBYTE" true
check_code N1.8 "41 two-byte characters (82 bytes) is refused, not crashed on" 400 VALIDATION_FAILED

# The auth run left this half-open: the rule lives in PasswordPolicy rather than on the DTO, so its
# wording was logged at DEBUG while the caller got only "One or more fields are invalid".
# ApiException.withField now carries it through as a field error, the same shape @Size produces.
check N1.9 "and the caller is told the actual rule" \
  "Use at most 72 characters — fewer if they are accented or emoji" "$(json '.fieldErrors.password // empty')"
check N1.10 "as the detail too, not just the field map" \
  "Use at most 72 characters — fewer if they are accented or emoji" "$(json '.detail')"

section "N2  terms and name"

signup "$(new_email t1)" "$PASSWORD" false
check_code N2.1 "termsAccepted false" 400 VALIDATION_FAILED
check N2.2 "the field error names the terms" "You must accept the terms to continue" "$(json '.fieldErrors.termsAccepted // empty')"

signup "$(new_email t2)" "$PASSWORD" true ""
check_code N2.3 "blank full name" 400 VALIDATION_FAILED

signup "$(new_email t3)" "$PASSWORD" true "$(printf 'x%.0s' $(seq 1 161))"
check_code N2.4 "161-character full name" 400 VALIDATION_FAILED

signup "$(new_email t4)" "$PASSWORD" true "<script>alert(1)</script>"
note N2.5 "HTML in the full name -> $LAST_STATUS; stored as: $(json '.user.fullName')"

section "N3  duplicate addresses"

DUP=$(new_email dup)
signup "$DUP" "$PASSWORD" true
check_status N3.1 "first signup" 201

signup "$DUP" "$PASSWORD" true
check_code N3.2 "same address again" 409 EMAIL_ALREADY_REGISTERED

signup "$(printf '%s' "$DUP" | tr 'a-z' 'A-Z')" "$PASSWORD" true
check_code N3.3 "same address upper-cased" 409 EMAIL_ALREADY_REGISTERED

signup "  $DUP  " "$PASSWORD" true
check_code N3.4 "same address with surrounding whitespace" 409 EMAIL_ALREADY_REGISTERED

check N3.5 "still exactly one row for that address" "1" \
  "$(sql "SELECT count(*) FROM app_lm_user WHERE email = '$DUP'")"

section "N4  email-domain gates"

signup "test-$(date +%s)@mailinator.com" "$PASSWORD" true
check_code N4.1 "disposable domain (mailinator.com)" 400 EMAIL_DISPOSABLE

# The MX check is meant to catch a typo'd domain. hasMailExchanger() treats every NamingException as
# inconclusive and returns true, and NXDOMAIN arrives as a NamingException — so a domain that does
# not exist at all is let straight through, which is exactly the case the check exists for. A domain
# that resolves but publishes no MX is the only one it actually stops.
signup "test-$(date +%s)@this-domain-does-not-exist-at-all-xyzq.com" "$PASSWORD" true
if [ "$LAST_STATUS" = "201" ]; then
  note N4.2 "NON-EXISTENT domain ACCEPTED (MX check fails open on NXDOMAIN) — see findings"
else
  pass N4.2 "non-existent domain rejected: $(ecode)"
fi

signup "test-$(date +%s)@example.com" "$PASSWORD" true
note N4.2b "domain that resolves but publishes no MX (example.com) -> $LAST_STATUS $(ecode)"

signup "not-an-email" "$PASSWORD" true
check_code N4.3 "malformed address" 400 VALIDATION_FAILED

signup "a@b" "$PASSWORD" true
check_code N4.4 "address with no dot in the domain" 400 EMAIL_UNDELIVERABLE

# block-public-domains defaults to FALSE, so consumer domains are accepted unless an operator turns
# the gate on. CLAUDE.md now documents this; the blocklist itself is covered by EmailAddressValidatorTest.
signup "lm-e2e-$(date +%s)$RANDOM@gmail.com" "$PASSWORD" true
check_status N4.5 "gmail.com is accepted while the gate is off (documented default)" 201
signup "lm-e2e-$(date +%s)$RANDOM@outlook.com" "$PASSWORD" true
check_status N4.6 "outlook.com likewise" 201
note N4.7 "boot with EMAIL_BLOCK_PUBLIC_DOMAINS=true to exercise the rejecting path"

section "N5  normalisation"

CASE_LOCAL="lm-e2e-Case-$(date +%s)$RANDOM"
signup "  ${CASE_LOCAL}@$(printf '%s' "$MAIL_DOMAIN" | tr 'a-z' 'A-Z')  " "$PASSWORD" true
NORMALISED=$(printf '%s@%s' "$CASE_LOCAL" "$MAIL_DOMAIN" | tr 'A-Z' 'a-z')
if [ "$LAST_STATUS" = "201" ]; then
  check N5.1 "mixed-case, space-padded address is stored normalised" "$NORMALISED" "$(json '.user.email')"
  post_json /auth/login "$(jq -nc --arg e "$NORMALISED" --arg p "$PASSWORD" '{email:$e, password:$p}')"
  check_status N5.2 "login with the normalised form" 200
  post_json /auth/login "$(jq -nc --arg e "$(printf '%s' "$NORMALISED" | tr 'a-z' 'A-Z')" --arg p "$PASSWORD" '{email:$e, password:$p}')"
  check_status N5.3 "login with the upper-cased form" 200
else
  note N5.1 "space-padded address rejected at validation: $LAST_STATUS $(ecode) $(json '.fieldErrors.email // empty')"
fi

section "N6  malformed requests"

http POST /auth/signup -H 'Content-Type: application/json' -d '{"fullName":'
check_status N6.1 "truncated JSON body" 400
check_contains N6.2 "the parse error is not leaked to the client" "" "$(json '.detail')"
note N6.3 "truncated-JSON detail: $(json '.detail')"

http POST /auth/signup -H 'Content-Type: application/json' -d ''
check_status N6.4 "empty body" 400

http POST /auth/signup -H 'Content-Type: text/plain' -d 'hello'
check_status N6.5 "wrong content type" 415

http POST /auth/signup -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg e "$(new_email wrongtype)" '{fullName:"X", email:$e, password:12345678, termsAccepted:"yes"}')"
check_status N6.6 "wrong JSON types for password and termsAccepted" 400

get /auth/signup
check_status N6.7 "GET on a POST-only route" 405

http POST /auth/signup -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg e "$(new_email extra)" --arg p "$PASSWORD" \
       '{fullName:"X", email:$e, password:$p, termsAccepted:true, status:"ACTIVE", emailVerified:true, id:"00000000-0000-0000-0000-000000000001"}')"
if [ "$LAST_STATUS" = "201" ]; then
  check N6.8 "unknown fields cannot force emailVerified" "false" "$(json '.user.emailVerified')"
  check N6.9 "unknown fields cannot force the user id" "false" \
    "$(test "$(json '.user.id')" = "00000000-0000-0000-0000-000000000001" && echo true || echo false)"
else
  note N6.8 "unknown fields rejected outright: $LAST_STATUS $(ecode)"
fi

summary
