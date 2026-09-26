#!/usr/bin/env bash
# Phase 2e — the seams around onboarding: a user in several workspaces and the session that is in
# one of them, the verified-email gate in front of the organisation and invite steps, and the
# invitation redemption paths.
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
# 404 NOT_A_MEMBER until a new one is issued — and /auth/me, which answers for the token's exact
# workspace, names none.
relogin() {
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "${2:-$PASSWORD}" '{email:$e, password:$p}')" >/dev/null
  json '.accessToken'
}

section "N27  several workspaces per user, one per session"

OWNER=$(new_email owner)
OWNER_TOKEN=$(signup_verified "$OWNER" "Owen Owner")
WS1="Owner Co $(date +%s)$RANDOM"
make_workspace "$OWNER_TOKEN" "$WS1"
check_status N27.1 "a verified user creates a workspace outright" 201

# The wizard's door is gated on verification alone, so it founds a first workspace only. A further one
# is POST /workspaces, gated on being staff of the workspace the session is in.
make_workspace "$OWNER_TOKEN" "Second Co $RANDOM"
check_code N27.2 "the wizard refuses a user already in a workspace" 409 ALREADY_IN_WORKSPACE

get /auth/me -H "$(auth_header "$OWNER_TOKEN")"
check N27.3 "/auth/me on the pre-workspace token names no workspace" "null" "$(json '.workspace')"
check N27.4 "but lists the one the user is in" "1" "$(json '.workspaces | length')"

# ...but a tenant route does not, because the gate reads wsId off the token.
get /invitations -H "$(auth_header "$OWNER_TOKEN")"
check N27.5 "a tenant route on the pre-workspace token is refused" "404" "$LAST_STATUS"
note N27.6 "pre-workspace token on a tenant route -> $LAST_STATUS $(ecode); a new token is required"

OWNER_TOKEN=$(relogin "$OWNER")
get /invitations -H "$(auth_header "$OWNER_TOKEN")"
check_status N27.7 "the same route after re-issuing the token" 200

WS2="Second Co $(date +%s)$RANDOM"
post_json /workspaces "$(jq -nc --arg n "$WS2" \
  '{name:$n, companySize:"1-10 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$OWNER_TOKEN")"
check_status N27.7b "a second workspace is founded from inside the app" 201
WS2_ID=$(json '.workspace.id')
check N27.7c "two active memberships for one user" "2" \
  "$(sql "SELECT count(*) FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$OWNER' AND m.status = 'ACTIVE'")"
OWNER_TOKEN=$(relogin "$OWNER")

# Creating was an explicit choice, so the sign-in opened in the second workspace. Switching is the
# only way a session moves: bearer + cookie, the cookie rotating as a refresh does.
check N27.8 "the sign-in opened in the workspace created last" "$WS2_ID" \
  "$(jwt_claims "$OWNER_TOKEN" | jq -r '.wsId')"
post_json /auth/login "$(jq -nc --arg e "$OWNER" --arg p "$PASSWORD" '{email:$e, password:$p}')" -c "$(jar owner)" >/dev/null
OWNER_TOKEN=$(json '.accessToken')
WS1_ID=$(json '.user.workspaces[0].id')
http POST /auth/switch-workspace -H 'Content-Type: application/json' -H "$(auth_header "$OWNER_TOKEN")" \
  -b "$(jar owner)" -c "$(jar owner)" -H "$(csrf_header owner)" -d "$(jq -nc --arg w "$WS1_ID" '{workspaceId:$w}')"
check_status N27.9 "POST /auth/switch-workspace to the first workspace" 200
OWNER_TOKEN=$(json '.accessToken')
check N27.10 "the new token names the workspace switched to" "$WS1_ID" "$(jwt_claims "$OWNER_TOKEN" | jq -r '.wsId')"
check N27.11 "and /me agrees" "$WS1" "$(json '.user.workspace.name')"

http POST /auth/switch-workspace -H 'Content-Type: application/json' -H "$(auth_header "$OWNER_TOKEN")" \
  -b "$(jar owner)" -c "$(jar owner)" -H "$(csrf_header owner)" -d '{"workspaceId":"00000000-0000-0000-0000-000000000000"}'
check_code N27.12 "switching to a workspace you are not in" 404 NOT_A_MEMBER
http POST /auth/refresh -b "$(jar owner)" -c "$(jar owner)" -H "$(csrf_header owner)"
check_status N27.13 "the cookie was not spent by the refused switch" 200
check N27.14 "and the refreshed session stays where the switch put it" "$WS1_ID" "$(jwt_claims "$(json '.accessToken')" | jq -r '.wsId')"

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

# The interesting case since V81: a rival workspace addresses an invitation at a member of another
# workspace, and acceptance ADDS a membership rather than failing — the two firms stay walled apart by
# the session's wsId, not by refusing the second membership.
OTHER_OWNER=$(new_email otherowner)
OTHER_TOKEN=$(signup_verified "$OTHER_OWNER" "Ozzy Other")
RIVAL_NAME="Rival Co $RANDOM"
make_workspace "$OTHER_TOKEN" "$RIVAL_NAME" >/dev/null
OTHER_TOKEN=$(relogin "$OTHER_OWNER")

post_json /invitations "$(jq -nc --arg a "$OWNER" '[{email:$a, role:"MEMBER"}]')" \
  -H "$(auth_header "$OTHER_TOKEN")"
check_status N33.4 "a rival workspace inviting a member of another workspace" 200

get /auth/me -H "$(auth_header "$OWNER_TOKEN")"
check N33.5 "the invitation shows on /me beside the workspaces they are already in" "$RIVAL_NAME" \
  "$(json '.pendingInvitations[0].workspaceName')"
RIVAL_TOKEN=$(token_for "$OWNER" accept-invite)
post_json /onboarding/invitations/accept "$(jq -nc --arg t "$RIVAL_TOKEN" '{token:$t}')" \
  -H "$(auth_header "$OWNER_TOKEN")"
check_status N33.6 "the already-placed member accepting it" 200
check N33.7 "answers the workspace joined" "$RIVAL_NAME" "$(json '.workspace.name')"
check N33.8 "they now belong to three workspaces" "3" \
  "$(sql "SELECT count(*) FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$OWNER' AND m.status = 'ACTIVE'")"
# The token they hold still names the first workspace: joining moved nothing until they switch.
check N33.9 "and the session they accepted with is where it was" "$WS1_ID" "$(jwt_claims "$OWNER_TOKEN" | jq -r '.wsId')"

summary
