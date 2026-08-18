#!/usr/bin/env bash
# Phase 2e — the seams around onboarding: the one-workspace-per-user constraint, the verified-email
# gate in front of the organisation and invite steps, and the invitation redemption paths.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

signup_verified() { # signup_verified EMAIL [FULLNAME] -> echoes an access token for a verified user
  post_json /auth/signup "$(jq -nc --arg e "$1" --arg p "$PASSWORD" --arg n "${2:-Test User}" \
    '{fullName:$n, email:$e, password:$p, termsAccepted:true}')" >/dev/null
  # No login afterwards: redeeming the link is one, and returns the session the wizard carries on with.
  post_json /auth/verify "$(jq -nc --arg t "$(token_for "$1" verify)" '{token:$t}')" >/dev/null
  json '.accessToken'
}

make_workspace() { # make_workspace TOKEN NAME
  post_json /onboarding/workspace "$(jq -nc --arg n "$2" \
    '{name:$n, companySize:"1-10 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
    -H "$(auth_header "$1")"
}

# A token minted before the workspace existed carries no wsId claim, so every tenant route answers
# 404 NOT_A_MEMBER until a new one is issued. /auth/me is the exception — it re-reads membership.
relogin() {
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "${2:-$PASSWORD}" '{email:$e, password:$p}')" >/dev/null
  json '.accessToken'
}

section "N27  one workspace per user"

OWNER=$(new_email owner)
OWNER_TOKEN=$(signup_verified "$OWNER" "Owen Owner")
WS1="Owner Co $(date +%s)$RANDOM"
make_workspace "$OWNER_TOKEN" "$WS1"
check_status N27.1 "a verified user creates a workspace outright" 201

make_workspace "$OWNER_TOKEN" "Second Co $RANDOM"
check_code N27.2 "the same user creating a second workspace" 409 ALREADY_IN_WORKSPACE

check N27.3 "the partial unique index holds at one active membership" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$OWNER' AND m.status = 'ACTIVE'")"

# The token minted before the workspace existed carries no wsId; the API must re-read membership.
get /auth/me -H "$(auth_header "$OWNER_TOKEN")"
check N27.4 "/auth/me reflects the new workspace on the old token" "$WS1" "$(json '.workspace.name')"

# ...but a tenant route does not, because the gate reads wsId off the token.
get /invitations -H "$(auth_header "$OWNER_TOKEN")"
check N27.5 "a tenant route on the pre-workspace token is refused" "404" "$LAST_STATUS"
note N27.6 "pre-workspace token on a tenant route -> $LAST_STATUS $(ecode); a new token is required"

OWNER_TOKEN=$(relogin "$OWNER")
get /invitations -H "$(auth_header "$OWNER_TOKEN")"
check_status N27.7 "the same route after re-issuing the token" 200

section "N28  the organisation step is shut until the mailbox is proved"

UNVERIFIED=$(new_email unver)
post_json /auth/signup "$(jq -nc --arg e "$UNVERIFIED" --arg p "$PASSWORD" \
  '{fullName:"Uma Nverified", email:$e, password:$p, termsAccepted:true}')" >/dev/null
UNVERIFIED_TOKEN=$(json '.accessToken')

make_workspace "$UNVERIFIED_TOKEN" "Squatted Co $RANDOM"
check_code N28.1 "an unverified user creating a workspace" 403 EMAIL_NOT_VERIFIED

http PATCH /onboarding/workspace -H 'Content-Type: application/json' \
  -H "$(auth_header "$UNVERIFIED_TOKEN")" -d '{"name":"Squatted Renamed","companySize":"11-50 people"}'
check_status N28.2 "PATCH is shut to them too" 403

check N28.3 "nothing exists on the domain they claimed" "0" \
  "$(sql "SELECT count(*) FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$UNVERIFIED'")"

http PATCH /onboarding/workspace -H 'Content-Type: application/json' \
  -H "$(auth_header "$OWNER_TOKEN")" -d "$(jq -nc --arg n "$WS1 Renamed" '{name:$n}')"
check_status N28.4 "PATCH edits a live workspace" 200

section "N29  the invite step is shut too — an unproven word must not mail strangers"

STRAY_INVITEE="lm-e2e-dropped-$(date +%s)$RANDOM@$MAIL_DOMAIN"
post_json /onboarding/invitations "$(jq -nc --arg a "$STRAY_INVITEE" '[{email:$a, role:"MEMBER"}]')" \
  -H "$(auth_header "$UNVERIFIED_TOKEN")"
check_code N29.1 "an unverified user sending invitations" 403 EMAIL_NOT_VERIFIED
check N29.2 "nothing was stored and nothing was sent" "0" \
  "$(sql "SELECT count(*) FROM app_lm_invitation WHERE email = '$STRAY_INVITEE'")"

# Verified, but with no workspace to invite anyone into. The gate is past; the tenant claim is missing.
NOWS=$(new_email nows)
NOWS_TOKEN=$(signup_verified "$NOWS" "Nora Nows")
NOWS_INVITEE="lm-e2e-nows-$(date +%s)$RANDOM@$MAIL_DOMAIN"
post_json /onboarding/invitations "$(jq -nc --arg a "$NOWS_INVITEE" '[{email:$a, role:"MEMBER"}]')" \
  -H "$(auth_header "$NOWS_TOKEN")"
note N29.3 "verified, no workspace, inviting -> $LAST_STATUS $(ecode)"
check N29.4 "and still mails nobody" "0" \
  "$(sql "SELECT count(*) FROM app_lm_invitation WHERE email = '$NOWS_INVITEE'")"

section "N30  a password reset is the other route past the verification gate"

RESETTER=$(new_email resetter)
post_json /auth/signup "$(jq -nc --arg e "$RESETTER" --arg p "$PASSWORD" \
  '{fullName:"Rae Setter", email:$e, password:$p, termsAccepted:true}')" >/dev/null

http POST /auth/password/forgot -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$RESETTER" '{email:$e}')"
post_json /auth/password/reset "$(jq -nc --arg t "$(token_for "$RESETTER" reset-password)" --arg p "ResetPass123" '{token:$t, password:$p}')"
check_status N30.1 "reset succeeds for an unverified user" 200
RESETTER_TOKEN=$(json '.accessToken')
check N30.2 "the reset verified the address" "true" \
  "$(sql "SELECT (email_verified_at IS NOT NULL)::text FROM app_lm_user WHERE email = '$RESETTER'")"

# The proof it is not merely a column: the session the reset returned clears the gate.
RESETTER_WS="Reset Co $RANDOM"
make_workspace "$RESETTER_TOKEN" "$RESETTER_WS"
check_status N30.3 "and the organisation step opens on that session" 201
check N30.4 "the workspace is real" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace WHERE name = '$RESETTER_WS'")"
note N30.5 "a password-reset link is therefore a second route past email verification"

section "N32  invitation redemption edges"

INVITEE_NEW=$(new_email invnew)
INVITEE_TAKEN=$(new_email invtaken)
post_json /invitations "$(jq -nc --arg a "$INVITEE_NEW" --arg b "$INVITEE_TAKEN" \
  '[{email:$a, role:"MEMBER"}, {email:$b, role:"MEMBER"}]')" -H "$(auth_header "$OWNER_TOKEN")"
check_status N32.1 "an admin sends invitations" 200

get "/onboarding/invitations/preview?token=garbage-token"
check_code N32.2 "preview with a garbage token" 400 INVITATION_INVALID

TOKEN_NEW=$(token_for "$INVITEE_NEW" accept-invite)

post_json /onboarding/accept-invitation-signup \
  "$(jq -nc --arg t "$TOKEN_NEW" '{token:$t, fullName:"New Person", password:"weak"}')"
check_code N32.3 "a weak password on invited signup" 400 VALIDATION_FAILED
check N32.4 "the invitation was not burned by the failed attempt" "PENDING" \
  "$(sql "SELECT status FROM app_lm_invitation WHERE email = '$INVITEE_NEW'")"

post_json /onboarding/accept-invitation-signup \
  "$(jq -nc --arg t "$TOKEN_NEW" --arg p "$PASSWORD" '{token:$t, fullName:"New Person", password:$p}')"
check_status N32.5 "the invitation still works afterwards" 201

post_json /onboarding/accept-invitation-signup \
  "$(jq -nc --arg t "$TOKEN_NEW" --arg p "$PASSWORD" '{token:$t, fullName:"Impostor", password:$p}')"
check N32.6 "the invitation cannot be redeemed twice" "true" \
  "$(test "$LAST_STATUS" != "201" && echo true || echo false)"
note N32.7 "second redemption -> $LAST_STATUS $(ecode)"

# An address that already has an account must be routed to log in, never duplicated.
signup_verified "$INVITEE_TAKEN" "Taken Person" >/dev/null
post_json /onboarding/accept-invitation-signup \
  "$(jq -nc --arg t "$(token_for "$INVITEE_TAKEN" accept-invite)" --arg p "$PASSWORD" \
     '{token:$t, fullName:"Taken Person", password:$p}')"
check_code N32.8 "invited signup for an address that already exists" 409 EMAIL_ALREADY_REGISTERED
check N32.9 "no duplicate user row was created" "1" \
  "$(sql "SELECT count(*) FROM app_lm_user WHERE email = '$INVITEE_TAKEN'")"

section "N33  an expired invitation, and one aimed at a member of another workspace"

EXPIRED_INVITEE=$(new_email invexp)
post_json /invitations "$(jq -nc --arg a "$EXPIRED_INVITEE" '[{email:$a, role:"MEMBER"}]')" \
  -H "$(auth_header "$OWNER_TOKEN")" >/dev/null
EXPIRED_TOKEN=$(token_for "$EXPIRED_INVITEE" accept-invite)
sql_run "UPDATE app_lm_invitation SET expires_at = now() - interval '1 hour' WHERE email = '$EXPIRED_INVITEE'"

get "/onboarding/invitations/preview?token=$EXPIRED_TOKEN"
check_code N33.1 "preview of an expired invitation" 400 INVITATION_EXPIRED
post_json /onboarding/accept-invitation-signup \
  "$(jq -nc --arg t "$EXPIRED_TOKEN" --arg p "$PASSWORD" '{token:$t, fullName:"Too Late", password:$p}')"
check_code N33.2 "redeeming an expired invitation" 400 INVITATION_EXPIRED

# The invitee already belongs to a different workspace: the single-membership index must win.
LOCKED_MAILS=$(email_count "invited you to")
post_json /invitations "$(jq -nc --arg a "$OWNER" '[{email:$a, role:"MEMBER"}]')" \
  -H "$(auth_header "$OWNER_TOKEN")"
check_status N33.3 "inviting somebody already in this workspace" 200
check N33.3b "reports nothing sent, rather than counting it as sent" "0" "$(json '.sent')"
check N33.3c "and mails them nothing" "$LOCKED_MAILS" "$(email_count "invited you to")"

# The interesting case: a user belongs to at most one workspace, enforced by a partial unique index.
# A rival workspace may still address an invitation at them; acceptance is where it has to fail.
OTHER_OWNER=$(new_email otherowner)
OTHER_TOKEN=$(signup_verified "$OTHER_OWNER" "Ozzy Other")
make_workspace "$OTHER_TOKEN" "Rival Co $RANDOM" >/dev/null
OTHER_TOKEN=$(relogin "$OTHER_OWNER")

post_json /invitations "$(jq -nc --arg a "$OWNER" '[{email:$a, role:"MEMBER"}]')" \
  -H "$(auth_header "$OTHER_TOKEN")"
note N33.4 "a rival workspace inviting a member of another workspace -> $LAST_STATUS $(ecode)"

if [ "$LAST_STATUS" = "200" ]; then
  RIVAL_TOKEN=$(token_for "$OWNER" accept-invite)
  post_json /onboarding/invitations/accept "$(jq -nc --arg t "$RIVAL_TOKEN" '{token:$t}')" \
    -H "$(auth_header "$OWNER_TOKEN")"
  note N33.5 "the already-placed member accepting it -> $LAST_STATUS $(ecode)"
  check N33.6 "they still belong to exactly one workspace" "1" \
    "$(sql "SELECT count(*) FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
            WHERE u.email = '$OWNER' AND m.status = 'ACTIVE'")"
  check N33.7 "and it is still their original workspace" "$WS1 Renamed" \
    "$(sql "SELECT w.name FROM app_lm_workspace w
              JOIN app_lm_workspace_member m ON m.workspace_id = w.id
              JOIN app_lm_user u ON u.id = m.user_id
            WHERE u.email = '$OWNER' AND m.status = 'ACTIVE'")"
fi

summary
