#!/usr/bin/env bash
# Phase 1 — the paths that are supposed to work, driven exactly as the SPA drives them.
#
# The spine is the 4-step signup of a creator: account, the verification click that proves the mailbox
# and signs them in, then the workspace, then the invitations. Everything after that (login, refresh,
# logout, the two invitee routes) hangs off the workspace step 3 created.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

ADMIN_EMAIL=$(new_email admin)
COLLEAGUE_EMAIL=$(new_email colleague)
EXISTING_EMAIL=$(new_email existing)
WORKSPACE_NAME="Acme Search $(date +%s)$RANDOM"

section "P1  signup — step 1 creates an account and no workspace"

post_json /auth/signup "$(jq -nc --arg e "$ADMIN_EMAIL" --arg p "$PASSWORD" \
  '{fullName:"Ada Admin", email:$e, password:$p, termsAccepted:true}')" -c "$(jar admin)"
check_status P1.1 "POST /auth/signup" 201
ADMIN_TOKEN=$(json '.accessToken')
check P1.2 "workspace is null before the org step"   "null"  "$(json '.user.workspace')"
check P1.3 "emailVerified is false"                 "false" "$(json '.user.emailVerified')"
check P1.4 "expiresIn is the 15-minute access TTL"  "900"   "$(json '.expiresIn')"
check P1.5 "email is stored lower-cased"            "$(printf '%s' "$ADMIN_EMAIL" | tr 'A-Z' 'a-z')" "$(json '.user.email')"

SET_COOKIE=$(header 'set-cookie')
check_contains P1.6 "refresh cookie is named lm_refresh"        "lm_refresh"        "$SET_COOKIE"
check_contains P1.7 "refresh cookie is HttpOnly"                "HttpOnly"          "$SET_COOKIE"
check_contains P1.8 "refresh cookie is scoped to /api/v1/auth"  "Path=/api/v1/auth" "$SET_COOKIE"
check_contains P1.9 "no access token in any cookie"             ""                  "$SET_COOKIE"

CLAIMS=$(jwt_claims "$ADMIN_TOKEN")
check P1.10 "access token carries no wsId before a workspace exists" "null" "$(printf '%s' "$CLAIMS" | jq -r '.wsId // "null"')"
check P1.11 "access token carries no roles claim either"             "null" "$(printf '%s' "$CLAIMS" | jq -r '.roles // "null"')"
check P1.12 "emailVerified claim is false"                           "false" "$(printf '%s' "$CLAIMS" | jq -r '.emailVerified')"
note  P1.13 "a verification email was printed: $(latest_link verify | head -c 60)..."

section "P2  step 2 — the organisation and invite steps are shut until the mailbox is proved"

post_json /onboarding/workspace \
  "$(jq -nc --arg n "$WORKSPACE_NAME" \
     '{name:$n, companySize:"11-50 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$ADMIN_TOKEN")"
check_status P2.1 "POST /onboarding/workspace while unverified" 403
check P2.2 "and says why, so the SPA can send them back a step" "EMAIL_NOT_VERIFIED" "$(json '.code')"

post_json /onboarding/invitations \
  "$(jq -nc --arg a "$COLLEAGUE_EMAIL" '[{email:$a, role:"MEMBER"}]')" \
  -H "$(auth_header "$ADMIN_TOKEN")"
check_status P2.3 "POST /onboarding/invitations while unverified" 403

check P2.4 "nothing came into existence on the firm's domain" "0" \
  "$(sql "SELECT count(*) FROM app_lm_workspace WHERE name = '$WORKSPACE_NAME'")"
check P2.5 "no invitation was mailed on an unproven word" "0" \
  "$(sql "SELECT count(*) FROM app_lm_invitation WHERE email = '$COLLEAGUE_EMAIL'")"

section "P3  the verification click proves the mailbox and signs them in"

VERIFY_TOKEN=$(token_for "$ADMIN_EMAIL" verify)
post_json /auth/verify "$(jq -nc --arg t "$VERIFY_TOKEN" '{token:$t}')"
check_status P3.1 "POST /auth/verify" 200
check P3.2 "emailVerified flips to true" "true" "$(json '.user.emailVerified')"
check P3.3 "user status is ACTIVE" "ACTIVE" "$(sql "SELECT status FROM app_lm_user WHERE email = '$ADMIN_EMAIL'")"

# The whole point of returning a session: the link is opened by the mail client, in a browser that has
# never held one. Without this the user proves the address and is then asked to log in.
ADMIN_TOKEN=$(json '.accessToken')
check P3.4 "redeeming mints an access token" "false" "$([ -z "$ADMIN_TOKEN" ] && echo true || echo false)"
check_contains P3.5 "and sets the refresh cookie, exactly as login" "lm_refresh" "$(header 'set-cookie')"
check P3.6 "still no workspace — that is the next step" "null" "$(json '.user.workspace')"
check P3.7 "the minted token already carries the verified claim" "true" \
  "$(jwt_claims "$ADMIN_TOKEN" | jq -r '.emailVerified')"
check P3.8 "verification token is consumed" "1" \
  "$(sql "SELECT count(*) FROM app_lm_verification_token t JOIN app_lm_user u ON u.id = t.user_id
          WHERE u.email = '$ADMIN_EMAIL' AND t.purpose = 'EMAIL_VERIFICATION' AND t.consumed_at IS NOT NULL")"

section "P4  steps 3 and 4 — the workspace and the invitations, created outright"

post_json /onboarding/workspace \
  "$(jq -nc --arg n "$WORKSPACE_NAME" \
     '{name:$n, companySize:"11-50 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$ADMIN_TOKEN")"
check_status P4.1 "POST /onboarding/workspace on the session verifying minted" 201
check P4.2 "workspace now exists" "1" "$(sql "SELECT count(*) FROM app_lm_workspace WHERE name = '$WORKSPACE_NAME'")"
check P4.3 "workspace email_domain came from the creator's address" "${ADMIN_EMAIL#*@}" \
  "$(sql "SELECT email_domain FROM app_lm_workspace WHERE name = '$WORKSPACE_NAME'")"
check P4.4 "creator holds the workspace ADMIN role" "ADMIN" \
  "$(sql "SELECT r.name FROM app_lm_workspace_member m
            JOIN app_lm_user u ON u.id = m.user_id
            JOIN app_lm_workspace_member_role mr ON mr.member_id = m.id
            JOIN app_lm_role r ON r.id = mr.role_id
          WHERE u.email = '$ADMIN_EMAIL'")"

# Re-login first, and that is the flow rather than a test detail: the token verifying minted predates
# the workspace, so it carries no wsId and every tenant route answers 404 NOT_A_MEMBER. The SPA does
# the same thing with reload() before it leaves the organisation step.
post_json /auth/login "$(jq -nc --arg e "$ADMIN_EMAIL" --arg p "$PASSWORD" '{email:$e, password:$p}')" \
  -c "$(jar admin)"
ADMIN_TOKEN=$(json '.accessToken')
check P4.5 "the re-issued token carries the new tenant claim" "false" \
  "$(jwt_claims "$ADMIN_TOKEN" | jq -r '(.wsId // "null") == "null"')"

post_json /onboarding/invitations \
  "$(jq -nc --arg a "$COLLEAGUE_EMAIL" --arg b "$EXISTING_EMAIL" \
     '[{email:$a, role:"MEMBER"}, {email:$b, role:"ADMIN"}]')" \
  -H "$(auth_header "$ADMIN_TOKEN")"
check_status P4.6 "POST /onboarding/invitations" 200
check P4.7 "both invitations went out" "2" \
  "$(sql "SELECT count(*) FROM app_lm_invitation WHERE email IN ('$COLLEAGUE_EMAIL','$EXISTING_EMAIL')")"

section "P5  login now returns a tenant-bearing session"

post_json /auth/login "$(jq -nc --arg e "$ADMIN_EMAIL" --arg p "$PASSWORD" '{email:$e, password:$p}')" \
  -c "$(jar admin)"
check_status P5.1 "POST /auth/login" 200
ADMIN_TOKEN=$(json '.accessToken')
check P5.2 "workspace is on the login response" "$WORKSPACE_NAME" "$(json '.user.workspace.name')"
check P5.3 "workspace roles include ADMIN" "ADMIN" "$(json '.user.workspace.roles[0]')"
CLAIMS=$(jwt_claims "$ADMIN_TOKEN")
check P5.4 "access token now carries wsId" "false" "$(printf '%s' "$CLAIMS" | jq -r '(.wsId // "null") == "null"')"
check P5.5 "access token carries the roles claim" "ADMIN" "$(printf '%s' "$CLAIMS" | jq -r '.roles[0] // .roles')"

get /auth/me -H "$(auth_header "$ADMIN_TOKEN")"
check_status P5.6 "GET /auth/me" 200
check P5.7 "/me agrees about the workspace" "$WORKSPACE_NAME" "$(json '.workspace.name')"
check P5.8 "/me reports no pending invitation for the creator" "null" "$(json '.pendingInvitation')"

section "P6  refresh rotates the cookie under CSRF"

OLD_REFRESH=$(refresh_cookie admin)
http POST /auth/refresh -b "$(jar admin)" -c "$(jar admin)" -H "$(csrf_header admin)"
check_status P6.1 "POST /auth/refresh with the CSRF header" 200
NEW_REFRESH=$(refresh_cookie admin)
if [ -n "$NEW_REFRESH" ] && [ "$NEW_REFRESH" != "$OLD_REFRESH" ]; then
  pass P6.2 "refresh cookie rotated to a new value"
else
  fail P6.2 "refresh cookie rotated to a new value" "old=[$OLD_REFRESH] new=[$NEW_REFRESH]"
fi
ROTATED_TOKEN=$(json '.accessToken')
check P6.3 "the rotated access token is a different jti" "false" \
  "$(test "$(jwt_claims "$ROTATED_TOKEN" | jq -r .jti)" = "$(jwt_claims "$ADMIN_TOKEN" | jq -r .jti)" && echo true || echo false)"
check P6.4 "predecessor is marked ROTATED, not revoked as theft" "ROTATED" \
  "$(sql "SELECT revoked_reason FROM app_lm_refresh_token r JOIN app_lm_user u ON u.id = r.user_id
          WHERE u.email = '$ADMIN_EMAIL' AND r.revoked_reason IS NOT NULL ORDER BY r.created_at LIMIT 1")"
ADMIN_TOKEN="$ROTATED_TOKEN"

section "P7  logout ends the session"

http POST /auth/logout -b "$(jar admin)" -c "$(jar admin)" -H "$(csrf_header admin)"
check_status P7.1 "POST /auth/logout" 204
check_contains P7.2 "logout expires the refresh cookie" "Max-Age=0" "$(header 'set-cookie')"

# The jar now holds the expired cookie, so replay the value captured before logout. Passing cookies
# inline replaces the jar, so the CSRF cookie has to be carried alongside it by hand.
XSRF=$(csrf_value admin2)
http POST /auth/refresh -b "lm_refresh=$NEW_REFRESH; XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF"
# A logout leaves a dead token behind by design. Calling that theft revoked the family, told the user
# their session ended "for security reasons", and burned TOKEN_REUSE_DETECTED — the one audit event
# meant to page a human — on a routine sign-out.
check_code P7.3 "refresh after logout is an ended session, not a theft" 401 REFRESH_TOKEN_INVALID
check P7.4 "and no reuse alert was raised for it" "0" \
  "$(sql "SELECT count(*) FROM app_lm_audit_event a JOIN app_lm_user u ON u.id = a.actor_user_id
          WHERE u.email = '$ADMIN_EMAIL' AND a.event_type = 'TOKEN_REUSE_DETECTED'")"

section "P8  a stranger invitee signs up through the invitation and lands ACTIVE"

COLLEAGUE_TOKEN_RAW=$(token_for "$COLLEAGUE_EMAIL" accept-invite)

get "/onboarding/invitations/preview?token=$COLLEAGUE_TOKEN_RAW"
check_status P8.1 "invitation preview is readable anonymously" 200
check P8.2 "preview names the workspace" "$WORKSPACE_NAME" "$(json '.workspaceName')"
check P8.3 "preview echoes the invited address" "$COLLEAGUE_EMAIL" "$(json '.email')"

VERIFY_MAILS_BEFORE=$(email_count "Confirm your LightMove email")
post_json /onboarding/accept-invitation-signup \
  "$(jq -nc --arg t "$COLLEAGUE_TOKEN_RAW" --arg p "$PASSWORD" \
     '{token:$t, fullName:"Colin Colleague", password:$p}')" -c "$(jar colleague)"
check_status P8.4 "POST /onboarding/accept-invitation-signup" 201
check P8.5 "invitee is already verified" "true" "$(json '.user.emailVerified')"
check P8.6 "invitee lands in the workspace immediately" "$WORKSPACE_NAME" "$(json '.user.workspace.name')"
check P8.7 "invitee holds the invited MEMBER role" "MEMBER" "$(json '.user.workspace.roles[0]')"
check P8.8 "membership is ACTIVE" "ACTIVE" \
  "$(sql "SELECT m.status FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id WHERE u.email = '$COLLEAGUE_EMAIL'")"
check P8.9 "no verification email was sent to the invitee" "$VERIFY_MAILS_BEFORE" "$(email_count "Confirm your LightMove email")"

section "P9  an invitee who already has an account redeems token-lessly"

# This identity was invited above, but signs up on their own instead of clicking the link.
post_json /auth/signup "$(jq -nc --arg e "$EXISTING_EMAIL" --arg p "$PASSWORD" \
  '{fullName:"Eve Existing", email:$e, password:$p, termsAccepted:true}')" -c "$(jar existing)"
check_status P9.1 "the invited address can still sign up directly" 201
EXISTING_TOKEN=$(json '.accessToken')
check P9.2 "/signup surfaces the pending invitation" "$WORKSPACE_NAME" "$(json '.user.pendingInvitation.workspaceName')"

# Verified email is required before the token-less accept.
http POST /onboarding/accept-invitation -H "$(auth_header "$EXISTING_TOKEN")"
check_code P9.3 "token-less accept is refused while unverified" 403 EMAIL_NOT_VERIFIED

post_json /auth/verify "$(jq -nc --arg t "$(token_for "$EXISTING_EMAIL" verify)" '{token:$t}')"
check_status P9.4 "verify the second account" 200
post_json /auth/login "$(jq -nc --arg e "$EXISTING_EMAIL" --arg p "$PASSWORD" '{email:$e, password:$p}')" -c "$(jar existing)"
EXISTING_TOKEN=$(json '.accessToken')

http POST /onboarding/accept-invitation -H "$(auth_header "$EXISTING_TOKEN")"
check_status P9.5 "token-less accept once verified" 200
check P9.6 "accepted into the inviting workspace" "$WORKSPACE_NAME" "$(json '.workspace.name')"
check P9.7 "with the invited ADMIN role" "ADMIN" "$(json '.workspace.roles[0]')"

http POST /onboarding/accept-invitation -H "$(auth_header "$EXISTING_TOKEN")"
check P9.8 "a second token-less accept is refused" "true" \
  "$(test "$LAST_STATUS" != "200" && echo true || echo false)"
note P9.9 "second accept returned $LAST_STATUS $(ecode)"

# Hand the identities to the negative suites.
cat > "$RUN_DIR/identities.env" <<EOF
ADMIN_EMAIL=$ADMIN_EMAIL
COLLEAGUE_EMAIL=$COLLEAGUE_EMAIL
EXISTING_EMAIL=$EXISTING_EMAIL
PASSWORD=$PASSWORD
EOF

summary
