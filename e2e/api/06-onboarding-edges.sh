#!/usr/bin/env bash
# Phase 2e — the seams around onboarding: the one-workspace-per-user constraint, the held-onboarding
# row and its expiry, and the invitation redemption paths.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

signup_verified() { # signup_verified EMAIL [FULLNAME] -> echoes an access token for a verified user
  post_json /auth/signup "$(jq -nc --arg e "$1" --arg p "$PASSWORD" --arg n "${2:-Test User}" \
    '{fullName:$n, email:$e, password:$p, termsAccepted:true}')" >/dev/null
  http POST "/auth/verify?token=$(token_for "$1" verify)" >/dev/null
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
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

section "N28  editing a workspace, held and live"

HELD=$(new_email held)
post_json /auth/signup "$(jq -nc --arg e "$HELD" --arg p "$PASSWORD" \
  '{fullName:"Hank Held", email:$e, password:$p, termsAccepted:true}')" >/dev/null
HELD_TOKEN=$(json '.accessToken')
make_workspace "$HELD_TOKEN" "Held Co $RANDOM"
check_status N28.1 "an unverified user's workspace is held" 202

http PATCH /onboarding/workspace -H 'Content-Type: application/json' \
  -H "$(auth_header "$HELD_TOKEN")" -d '{"name":"Held Co Renamed","companySize":"11-50 people"}'
check_status N28.2 "PATCH edits the held draft" 202
check N28.3 "the draft name changed on the held row" "Held Co Renamed" \
  "$(sql "SELECT p.name FROM app_lm_pending_onboarding p JOIN app_lm_user u ON u.id = p.user_id WHERE u.email = '$HELD'")"

http PATCH /onboarding/workspace -H 'Content-Type: application/json' \
  -H "$(auth_header "$OWNER_TOKEN")" -d "$(jq -nc --arg n "$WS1 Renamed" '{name:$n}')"
check_status N28.4 "PATCH edits a live workspace" 200

section "N29  invitations without a held workspace are dropped"

STRAY=$(new_email stray)
post_json /auth/signup "$(jq -nc --arg e "$STRAY" --arg p "$PASSWORD" \
  '{fullName:"Stan Stray", email:$e, password:$p, termsAccepted:true}')" >/dev/null
STRAY_TOKEN=$(json '.accessToken')
STRAY_INVITEE="lm-e2e-dropped-$(date +%s)$RANDOM@$MAIL_DOMAIN"

post_json /onboarding/invitations "$(jq -nc --arg a "$STRAY_INVITEE" '[{email:$a, role:"MEMBER"}]')" \
  -H "$(auth_header "$STRAY_TOKEN")"
note N29.1 "invitations with no held workspace -> $LAST_STATUS $(ecode)"
check N29.2 "nothing was stored and nothing was sent" "0" \
  "$(sql "SELECT count(*) FROM app_lm_invitation WHERE email = '$STRAY_INVITEE'")"
check N29.3 "the API says so only in its own log, not to the caller" "true" \
  "$(test "$(grep -c "with no held organisation — dropped" "$API_LOG" || echo 0)" -ge 1 && echo true || echo false)"

section "N30  a held onboarding that expires"

LAPSED=$(new_email lapsed)
post_json /auth/signup "$(jq -nc --arg e "$LAPSED" --arg p "$PASSWORD" \
  '{fullName:"Lap Sed", email:$e, password:$p, termsAccepted:true}')" >/dev/null
LAPSED_TOKEN=$(json '.accessToken')
LAPSED_WS="Lapsed Co $RANDOM"
make_workspace "$LAPSED_TOKEN" "$LAPSED_WS"
sql_run "UPDATE app_lm_pending_onboarding SET expires_at = now() - interval '1 hour'
         WHERE user_id = (SELECT id FROM app_lm_user WHERE email = '$LAPSED')"

# The hold's expiry sweeps up drafts nobody returned for. It must not veto a user who has just proved
# the mailbox: the old code deleted the row before checking, so the draft was destroyed by the very
# path that declined to use it, and the SPA sent them back to a blank form with nothing said.
http POST "/auth/verify?token=$(token_for "$LAPSED" verify)"
check_status N30.1 "verification still succeeds" 200
check N30.2 "the workspace is created even though the hold had lapsed" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace WHERE name = '$LAPSED_WS'")"
check N30.3 "the held row is consumed" "0" \
  "$(sql "SELECT count(*) FROM app_lm_pending_onboarding p JOIN app_lm_user u ON u.id = p.user_id WHERE u.email = '$LAPSED'")"

post_json /auth/login "$(jq -nc --arg e "$LAPSED" --arg p "$PASSWORD" '{email:$e, password:$p}')"
check N30.4 "the user lands in the workspace they typed" "$LAPSED_WS" "$(json '.user.workspace.name')"
check N30.5 "as its ADMIN" "ADMIN" "$(json '.user.workspace.roles[0]')"

# The competing case must still win: a user who joined a workspace in the meantime keeps that one, and
# the stale draft is discarded rather than creating a second membership.
JOINED=$(new_email joined)
post_json /auth/signup "$(jq -nc --arg e "$JOINED" --arg p "$PASSWORD" \
  '{fullName:"Jo Ined", email:$e, password:$p, termsAccepted:true}')" >/dev/null
JOINED_TOKEN=$(json '.accessToken')
make_workspace "$JOINED_TOKEN" "Ghost Co $RANDOM" >/dev/null
post_json /invitations "$(jq -nc --arg a "$JOINED" '[{email:$a, role:"MEMBER"}]')" \
  -H "$(auth_header "$OWNER_TOKEN")" >/dev/null
post_json /onboarding/accept-invitation-signup \
  "$(jq -nc --arg t "$(token_for "$JOINED" accept-invite)" --arg p "$PASSWORD" \
     '{token:$t, fullName:"Jo Ined", password:$p}')" >/dev/null
http POST "/auth/verify?token=$(token_for "$JOINED" verify)" >/dev/null
check N30.6 "a held draft is discarded for someone already in a workspace" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$JOINED' AND m.status = 'ACTIVE'")"

section "N31  a password reset also materialises a held workspace"

RESETTER=$(new_email resetter)
post_json /auth/signup "$(jq -nc --arg e "$RESETTER" --arg p "$PASSWORD" \
  '{fullName:"Rae Setter", email:$e, password:$p, termsAccepted:true}')" >/dev/null
RESETTER_TOKEN=$(json '.accessToken')
RESETTER_WS="Reset Co $RANDOM"
make_workspace "$RESETTER_TOKEN" "$RESETTER_WS"

http POST /auth/password/forgot -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$RESETTER" '{email:$e}')"
post_json /auth/password/reset "$(jq -nc --arg t "$(token_for "$RESETTER" reset-password)" --arg p "ResetPass123" '{token:$t, password:$p}')"
check_status N31.1 "reset succeeds for an unverified user" 200
check N31.2 "the reset verified the address" "true" \
  "$(sql "SELECT (email_verified_at IS NOT NULL)::text FROM app_lm_user WHERE email = '$RESETTER'")"
check N31.3 "and materialised the held workspace" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace WHERE name = '$RESETTER_WS'")"
note N31.4 "a password-reset link is therefore a second route past email verification"

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
