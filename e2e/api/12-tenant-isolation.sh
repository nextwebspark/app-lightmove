#!/usr/bin/env bash
# Phase 4 — tenant isolation, then the workspace-delete gate.
#
# The signature check already proved a forged wsId claim is rejected (auth run, N24.6). The question
# here is different and softer: a completely legitimate admin, with a completely legitimate token,
# naming another tenant's id. Every one of those must answer 404 rather than 403 — a 403 confirms the
# id exists, which is the whole thing tenant masking is for.
#
# Runs last: it deletes a workspace.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
. "$RUN_DIR/cast.env"

member_id_of() {
  sql "SELECT m.id FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
       WHERE u.email = '$1' AND m.status = 'ACTIVE'"
}

OUTSIDER_MEMBER_ID=$(member_id_of "$OUTSIDER_EMAIL")
HOME_MEMBER_ID=$(member_id_of "$MEMBER_EMAIL")

section "T1  another tenant's ids, held by a legitimate admin"

# Our admin reaching into the rival workspace.
http PATCH "/members/$OUTSIDER_MEMBER_ID" -H 'Content-Type: application/json' \
  -d '{"roles":["MEMBER"]}' -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.1 "PATCH a foreign member" 404 NOT_A_MEMBER

http DELETE "/members/$OUTSIDER_MEMBER_ID" -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.2 "DELETE a foreign member" 404 NOT_A_MEMBER

get "/clients/$OUTSIDER_CLIENT_ID" -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.3 "GET a foreign client" 404 NOT_FOUND

http PATCH "/clients/$OUTSIDER_CLIENT_ID" -H 'Content-Type: application/json' \
  -d '{"name":"Repossessed"}' -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.4 "PATCH a foreign client" 404 NOT_FOUND

post_json "/clients/$OUTSIDER_CLIENT_ID/representatives" \
  "$(jq -nc --arg e "$(new_email cross)" '{fullName:"Cross Rep", email:$e}')" \
  -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.5 "invite a representative onto a foreign client" 404 NOT_FOUND

http PATCH "/projects/$OUTSIDER_PROJECT_ID" -H 'Content-Type: application/json' \
  -d '{"targetDate":"2027-06-01"}' -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.6 "PATCH a foreign project" 404 NOT_FOUND

get "/projects/$OUTSIDER_PROJECT_ID/position" -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.7 "read a foreign project's brief — the admin bypass must not cross tenants" 404 NOT_FOUND

get "/projects/$OUTSIDER_PROJECT_ID/strategy" -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.8 "nor its strategy" 404 NOT_FOUND

http PUT "/projects/$OUTSIDER_PROJECT_ID/members/$HOME_MEMBER_ID" \
  -H 'Content-Type: application/json' -d '{"role":"LEAD"}' -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.9 "seat one of our own people on a foreign project" 404 NOT_FOUND

http POST "/invitations/$OUTSIDER_INVITATION_ID/resend" -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.10 "resend a foreign invitation" 404 NOT_FOUND

http DELETE "/invitations/$OUTSIDER_INVITATION_ID" -H "$(auth_header "$ADMIN_TOKEN")"
check_code T1.11 "revoke a foreign invitation" 404 NOT_FOUND

post_json "/projects/$PROJECT_ID/representatives" \
  "$(jq -nc --arg r "$(sql "SELECT id FROM app_lm_client_representative
                            WHERE client_id = '$OUTSIDER_CLIENT_ID' LIMIT 1")" '{representativeId:$r}')" \
  -H "$(auth_header "$ADMIN_TOKEN")"
note T1.12 "attaching a foreign representative to our mandate -> $LAST_STATUS $(ecode)"

section "T2  the same, in the other direction"

get "/clients/$CLIENT_ID" -H "$(auth_header "$OUTSIDER_TOKEN")"
check_code T2.1 "the rival admin reading our client" 404 NOT_FOUND
get "/projects/$PROJECT_ID/position" -H "$(auth_header "$OUTSIDER_TOKEN")"
check_code T2.2 "reading our brief" 404 NOT_FOUND
http PATCH "/members/$HOME_MEMBER_ID" -H 'Content-Type: application/json' \
  -d '{"roles":["ADMIN"]}' -H "$(auth_header "$OUTSIDER_TOKEN")"
check_code T2.3 "promoting one of our members" 404 NOT_A_MEMBER

get /members -H "$(auth_header "$OUTSIDER_TOKEN")"
check T2.4 "their roster contains only their own people" "0" \
  "$(json "[.[] | select(.email == \"$MEMBER_EMAIL\" or .email == \"$ADMIN_EMAIL\")] | length")"
get /clients -H "$(auth_header "$OUTSIDER_TOKEN")"
check T2.5 "and their client list only their own clients" "0" \
  "$(json "[.[] | select(.id == \"$CLIENT_ID\")] | length")"
get /projects -H "$(auth_header "$OUTSIDER_TOKEN")"
check T2.6 "and their project list only their own mandates" "0" \
  "$(json "[.[] | select(.id == \"$PROJECT_ID\")] | length")"

section "T3  a client of one workspace is nothing in another"

# The pure client's token belongs to our workspace; the rival's ids must be invisible to them too.
get "/projects/$OUTSIDER_PROJECT_ID/position" -H "$(auth_header "$CLIENT_TOKEN")"
check_code T3.1 "the pure client reaching a foreign mandate" 404 NOT_FOUND
get "/clients/$OUTSIDER_CLIENT_ID" -H "$(auth_header "$CLIENT_TOKEN")"
check_code T3.2 "and a foreign client record" 403 FORBIDDEN
note T3.3 "note the ordering: the registry refuses on role before it ever looks the id up"

section "T4  the workspace-delete gate, on a workspace built to be destroyed"

DOOMED_ADMIN=$(new_email doomedadmin)
DOOMED_MEMBER=$(new_email doomedmember)
DOOMED_NAME="Doomed Partners $(date +%s)$RANDOM"

post_json /auth/signup "$(jq -nc --arg e "$DOOMED_ADMIN" --arg p "$PASSWORD" \
  '{fullName:"Dee Doomed", email:$e, password:$p, termsAccepted:true}')" >/dev/null
post_json /auth/verify "$(jq -nc --arg t "$(token_for "$DOOMED_ADMIN" verify)" '{token:$t}')" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$DOOMED_ADMIN" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
DOOMED_ADMIN_TOKEN=$(json '.accessToken')
post_json /onboarding/workspace "$(jq -nc --arg n "$DOOMED_NAME" \
  '{name:$n, companySize:"1-10 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$DOOMED_ADMIN_TOKEN")" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$DOOMED_ADMIN" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
DOOMED_ADMIN_TOKEN=$(json '.accessToken')

post_json /invitations "$(jq -nc --arg e "$DOOMED_MEMBER" '[{email:$e, role:"MEMBER"}]')" \
  -H "$(auth_header "$DOOMED_ADMIN_TOKEN")" >/dev/null
post_json /onboarding/accept-invitation-signup \
  "$(jq -nc --arg t "$(token_for "$DOOMED_MEMBER" accept-invite)" --arg p "$PASSWORD" \
     '{token:$t, fullName:"Dan Doomed", password:$p}')" >/dev/null
DOOMED_MEMBER_TOKEN=$(json '.accessToken')

# A pending invitation that must die with the workspace.
post_json /invitations "$(jq -nc --arg e "$(new_email doomedpending)" '[{email:$e, role:"MEMBER"}]')" \
  -H "$(auth_header "$DOOMED_ADMIN_TOKEN")" >/dev/null

http DELETE /workspace -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg n "$DOOMED_NAME" '{confirmName:$n}')" -H "$(auth_header "$DOOMED_MEMBER_TOKEN")"
check_code T4.1 "a plain MEMBER deleting the workspace" 403 FORBIDDEN
check T4.2 "and it is still standing" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace WHERE name = '$DOOMED_NAME'")"

http DELETE /workspace -H 'Content-Type: application/json' \
  -d '{"confirmName":"Not The Name"}' -H "$(auth_header "$DOOMED_ADMIN_TOKEN")"
check_code T4.3 "the admin typing the wrong name" 400 WORKSPACE_NAME_MISMATCH
check T4.4 "still standing" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace WHERE name = '$DOOMED_NAME'")"

http DELETE /workspace -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg n "$DOOMED_NAME" '{confirmName:$n}')" -H "$(auth_header "$DOOMED_ADMIN_TOKEN")"
check_status T4.5 "the admin typing it correctly" 204

check T4.6 "no active membership survives" "0" \
  "$(sql "SELECT count(*) FROM app_lm_workspace_member m
            JOIN app_lm_workspace w ON w.id = m.workspace_id
          WHERE w.name = '$DOOMED_NAME' AND m.status = 'ACTIVE'")"
check T4.7 "no invitation is still pending" "0" \
  "$(sql "SELECT count(*) FROM app_lm_invitation i
            JOIN app_lm_workspace w ON w.id = i.workspace_id
          WHERE w.name = '$DOOMED_NAME' AND i.status = 'PENDING'")"

get /workspace -H "$(auth_header "$DOOMED_MEMBER_TOKEN")"
check_code T4.8 "the member's live token no longer reaches a workspace" 404 NOT_A_MEMBER
get /workspace -H "$(auth_header "$DOOMED_ADMIN_TOKEN")"
check_code T4.9 "nor the admin's" 404 NOT_A_MEMBER
get /auth/me -H "$(auth_header "$DOOMED_ADMIN_TOKEN")"
check T4.10 "and /me shows them workspace-less, free to start again" "null" "$(json '.workspace')"

section "T5  the surviving workspace is untouched"

get /members -H "$(auth_header "$ADMIN_TOKEN")"
check_status T5.1 "our roster still reads" 200
get /projects -H "$(auth_header "$CLIENT_TOKEN")"
check T5.2 "and the client still sees their one mandate" "1" "$(json 'length')"

summary
