#!/usr/bin/env bash
# A person in several workspaces: founding a second from inside the app, switching between them, and
# the tenant wall that holds across the switch.
#
# What no other script exercises is the seam V81 adds: one user, two active memberships, one session
# in exactly one of them — and the data of the workspace just left being unreachable from the one
# entered. Builds its own cast, does nothing destructive, and needs no Apollo universe.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "20 — several workspaces, one session"

signup_verified() { # signup_verified EMAIL FULLNAME JAR -> echoes an access token for a verified, signed-in user
  post_json /auth/signup "$(jq -nc --arg e "$1" --arg p "$PASSWORD" --arg n "$2" \
    '{fullName:$n, email:$e, password:$p, termsAccepted:true}')" >/dev/null
  post_json /auth/verify "$(jq -nc --arg t "$(token_for "$1" verify)" '{token:$t}')" >/dev/null
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" -c "$(jar "$3")" >/dev/null
  json '.accessToken'
}
login_as() { # login_as EMAIL JAR -> a fresh session; echoes its access token
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" -c "$(jar "$2")" >/dev/null
  json '.accessToken'
}
switch_to() { # switch_to TOKEN JAR WORKSPACE_ID
  http POST /auth/switch-workspace -H 'Content-Type: application/json' -H "$(auth_header "$1")" \
    -b "$(jar "$2")" -c "$(jar "$2")" -H "$(csrf_header "$2")" -d "$(jq -nc --arg w "$3" '{workspaceId:$w}')"
}
ws_of() { jwt_claims "$1" | jq -r '.wsId'; }

# --- the cast: Alok founds one firm at signup and a second from the app --------------------------

ALOK=$(new_email alok)
ALOK_TOKEN=$(signup_verified "$ALOK" "Alok Kumar" alok)
FIRST_NAME="First Firm $(date +%s)$RANDOM"
post_json /onboarding/workspace "$(jq -nc --arg n "$FIRST_NAME" \
  '{name:$n, companySize:"11-50 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$ALOK_TOKEN")" >/dev/null
FIRST_ID=$(json '.workspace.id')
ALOK_TOKEN=$(login_as "$ALOK" alok)

post_json /clients '{"customName":"First Holding"}' -H "$(auth_header "$ALOK_TOKEN")" >/dev/null
post_json /projects "$(jq -nc --arg c "$(json '.id')" '{clientId:$c, positionTitle:"Chief Financial Officer"}')" \
  -H "$(auth_header "$ALOK_TOKEN")" >/dev/null
FIRST_PROJECT=$(json '.id')

section "W1  a staff member founds a second workspace from the app"

SECOND_NAME="Second Firm $(date +%s)$RANDOM"
post_json /workspaces "$(jq -nc --arg n "$SECOND_NAME" \
  '{name:$n, companySize:"1-10 people", primaryRegion:"GCC", teamFocus:"Talent mapping"}')" \
  -H "$(auth_header "$ALOK_TOKEN")"
check_status W1.1 "POST /workspaces" 201
SECOND_ID=$(json '.workspace.id')
check W1.2 "the answer names the new workspace" "$SECOND_NAME" "$(json '.workspace.name')"
check W1.3 "with the founder as its admin" "ADMIN" "$(json '.workspace.roles[0]')"
check W1.4 "and lists both" "2" "$(json '.workspaces | length')"
check W1.5 "the token that created it still names the first" "$FIRST_ID" "$(ws_of "$ALOK_TOKEN")"

section "W2  a fresh sign-in opens in the workspace last chosen, and the wall holds"

ALOK_TOKEN=$(login_as "$ALOK" alok)
check W2.1 "sign-in opened in the second workspace" "$SECOND_ID" "$(ws_of "$ALOK_TOKEN")"
get /projects -H "$(auth_header "$ALOK_TOKEN")"
check W2.2 "the first firm's mandate is not on this list" "0" "$(json 'length')"
get "/projects/$FIRST_PROJECT/activity" -H "$(auth_header "$ALOK_TOKEN")"
check_code W2.3 "nor reachable by id" 404 NOT_A_MEMBER

section "W3  switching moves the session, and only the session"

switch_to "$ALOK_TOKEN" alok "$FIRST_ID"
check_status W3.1 "POST /auth/switch-workspace" 200
ALOK_TOKEN=$(json '.accessToken')
check W3.2 "the new token names the first workspace" "$FIRST_ID" "$(ws_of "$ALOK_TOKEN")"
check W3.3 "and /me agrees" "$FIRST_NAME" "$(json '.user.workspace.name')"
get /projects -H "$(auth_header "$ALOK_TOKEN")"
check W3.4 "the mandate is back" "$FIRST_PROJECT" "$(json '.[0].id')"

http POST /auth/refresh -b "$(jar alok)" -c "$(jar alok)" -H "$(csrf_header alok)"
check W3.5 "a refresh keeps the session where the switch put it" "$FIRST_ID" "$(ws_of "$(json '.accessToken')")"
check W3.6 "and the next sign-in opens there too" "$FIRST_ID" "$(ws_of "$(login_as "$ALOK" alok2)")"

section "W4  an invitation to a further workspace is accepted from inside the app"

SARA=$(new_email sara)
SARA_TOKEN=$(signup_verified "$SARA" "Sara Al-Mansour" sara)
THIRD_NAME="Third Firm $(date +%s)$RANDOM"
post_json /onboarding/workspace "$(jq -nc --arg n "$THIRD_NAME" \
  '{name:$n, companySize:"1-10 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$SARA_TOKEN")" >/dev/null
THIRD_ID=$(json '.workspace.id')
SARA_TOKEN=$(login_as "$SARA" sara)
post_json /invitations "$(jq -nc --arg e "$ALOK" '[{email:$e, role:"ADMIN"}]')" -H "$(auth_header "$SARA_TOKEN")" >/dev/null

get /auth/me -H "$(auth_header "$ALOK_TOKEN")"
check W4.1 "/me carries the invitation to the third workspace" "$THIRD_NAME" "$(json '.pendingInvitations[0].workspaceName')"
check W4.2 "naming who sent it" "Sara Al-Mansour" "$(json '.pendingInvitations[0].inviterName')"
INVITATION=$(json '.pendingInvitations[0].id')

http POST "/onboarding/invitations/$INVITATION/accept" -H "$(auth_header "$ALOK_TOKEN")"
check_status W4.3 "accepting it by id" 200
check W4.4 "answers the workspace joined" "$THIRD_ID" "$(json '.workspace.id')"
check W4.5 "and three memberships now" "3" "$(json '.workspaces | length')"

switch_to "$ALOK_TOKEN" alok "$THIRD_ID"
check_status W4.6 "switching into it" 200
ALOK_IN_THIRD=$(json '.accessToken')
get /members -H "$(auth_header "$ALOK_IN_THIRD")"
check W4.7 "the third firm's roster reads as its new admin" "2" "$(json 'length')"

section "W5  leaving, and being removed, fall through to another workspace"

ALOK_MEMBER_ID=$(get /members -H "$(auth_header "$ALOK_IN_THIRD")" >/dev/null; json ".[] | select(.email == \"$ALOK\") | .memberId")
http DELETE "/members/$ALOK_MEMBER_ID" -H "$(auth_header "$ALOK_IN_THIRD")"
check_status W5.1 "leaving the third workspace (Sara stays its admin)" 204
switch_to "$ALOK_IN_THIRD" alok "$THIRD_ID"
check_code W5.2 "switching back into it is refused" 404 NOT_A_MEMBER
http POST /auth/refresh -b "$(jar alok)" -c "$(jar alok)" -H "$(csrf_header alok)"
check_status W5.3 "the session's own refresh still works" 200
check W5.4 "and lands in a workspace he is still in" "true" \
  "$(test "$(ws_of "$(json '.accessToken')")" != "$THIRD_ID" && echo true || echo false)"
check W5.5 "a move nobody asked for is audited, naming the workspace left" "1" \
  "$(sql "SELECT count(*) FROM app_lm_audit_event e JOIN app_lm_user u ON u.id = e.actor_user_id
          WHERE u.email = '$ALOK' AND e.event_type = 'WORKSPACE_SWITCHED'
            AND e.target_id = '$THIRD_ID' AND e.metadata ->> 'reason' = 'MEMBERSHIP_ENDED'")"

section "W6  a removed member is re-invited onto the same row"

post_json /invitations "$(jq -nc --arg e "$ALOK" '[{email:$e, role:"MEMBER"}]')" -H "$(auth_header "$SARA_TOKEN")"
check_status W6.1 "Sara invites Alok back" 200
post_json /onboarding/invitations/accept "$(jq -nc --arg t "$(token_for "$ALOK" accept-invite)" '{token:$t}')" \
  -H "$(auth_header "$(login_as "$ALOK" alok3)")"
check_status W6.2 "accepting the emailed link as an existing user" 200
check W6.3 "as a MEMBER this time" "MEMBER" "$(json '.workspace.roles[0]')"
check W6.4 "one row for him in that workspace, whatever its status" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$ALOK' AND m.workspace_id = '$THIRD_ID'")"

summary
