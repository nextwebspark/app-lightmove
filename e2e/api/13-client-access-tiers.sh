#!/usr/bin/env bash
# Phase 6 — the two tiers of client access, which V22 split apart.
#
#   WORKSPACE tier — who exists as a client and as a contact on it.
#     CLIENT_RECORD_MANAGE, held by ADMIN and MEMBER. Minting a representative here shows them nothing.
#
#   PROJECT tier — whether that contact can see a particular search.
#     CLIENT_ACCESS_MANAGE, held by LEAD alone (plus the standing workspace-admin bypass).
#
# Before V22 the mandate half rode on PROJECT_EDIT. The rule was already true, but only by arithmetic:
# LEAD holds PROJECT_EDIT and RESEARCHER does not. Widen PROJECT_EDIT to RESEARCHER one day and client
# access would have gone with it silently. These cases pin the separation itself, not the arithmetic.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
. "$RUN_DIR/cast.env"

member_id_of() {
  sql "SELECT m.id FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
       WHERE u.email = '$1' AND m.status = 'ACTIVE'"
}
login_as() {
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
  json '.accessToken'
}

section "V1  the catalog after V22"

check V1.1 "CLIENT_ACCESS_MANAGE exists as a PROJECT action" "1" \
  "$(sql "SELECT count(*) FROM app_lm_action WHERE scope = 'PROJECT' AND name = 'CLIENT_ACCESS_MANAGE'")"
check V1.2 "and is granted to exactly one project role" "1" \
  "$(sql "SELECT count(*) FROM app_lm_role_action ra
            JOIN app_lm_action a ON a.id = ra.action_id
            JOIN app_lm_role r ON r.id = ra.role_id
          WHERE a.scope = 'PROJECT' AND a.name = 'CLIENT_ACCESS_MANAGE'")"
check V1.3 "which is LEAD" "LEAD" \
  "$(sql "SELECT r.name FROM app_lm_role_action ra
            JOIN app_lm_action a ON a.id = ra.action_id
            JOIN app_lm_role r ON r.id = ra.role_id
          WHERE a.scope = 'PROJECT' AND a.name = 'CLIENT_ACCESS_MANAGE'")"
check V1.4 "RESEARCHER holds neither client access nor PROJECT_EDIT" "" \
  "$(sql "SELECT string_agg(a.name, ',' ORDER BY a.name) FROM app_lm_role_action ra
            JOIN app_lm_action a ON a.id = ra.action_id
            JOIN app_lm_role r ON r.id = ra.role_id
          WHERE r.scope = 'PROJECT' AND r.name = 'RESEARCHER'
            AND a.name IN ('CLIENT_ACCESS_MANAGE', 'PROJECT_EDIT')")"
check V1.5 "the project CLIENT seat holds only WORK_VIEW" "WORK_VIEW" \
  "$(sql "SELECT string_agg(a.name, ',' ORDER BY a.name) FROM app_lm_role_action ra
            JOIN app_lm_action a ON a.id = ra.action_id
            JOIN app_lm_role r ON r.id = ra.role_id
          WHERE r.scope = 'PROJECT' AND r.name = 'CLIENT'")"
check V1.6 "the workspace registry action is still ADMIN and MEMBER" "ADMIN,MEMBER" \
  "$(sql "SELECT string_agg(r.name, ',' ORDER BY r.name) FROM app_lm_role_action ra
            JOIN app_lm_action a ON a.id = ra.action_id
            JOIN app_lm_role r ON r.id = ra.role_id
          WHERE a.scope = 'WORKSPACE' AND a.name = 'CLIENT_RECORD_MANAGE'")"

section "V2  workspace tier — an ADMIN and a MEMBER may both build the registry"

for actor in admin member; do
  case "$actor" in
    admin) TOKEN="$ADMIN_TOKEN" ;;
    member) TOKEN="$MEMBER_TOKEN" ;;
  esac

  post_json /clients "$(jq -nc --arg n "Tier Co $actor $RANDOM" '{customName:$n, sector:"Industrials"}')" \
    -H "$(auth_header "$TOKEN")"
  check_status "V2.1/$actor" "creates a client" 201
  TIER_CLIENT=$(json '.id')

  post_json "/clients/$TIER_CLIENT/representatives" \
    "$(jq -nc --arg e "$(new_email tier$actor)" '{fullName:"Tier Rep", position:"CFO", email:$e}')" \
    -H "$(auth_header "$TOKEN")"
  check_status "V2.2/$actor" "invites a representative onto it" 201

  get "/clients/$TIER_CLIENT" -H "$(auth_header "$TOKEN")"
  check "V2.3/$actor" "and the contact is on the record" "1" "$(json '.representatives | length')"
done

# The tier boundary itself: minting a representative grants no sight of anything.
NAKED_EMAIL=$(new_email naked)
post_json "/clients/$CLIENT_ID/representatives" \
  "$(jq -nc --arg e "$NAKED_EMAIL" '{fullName:"Nora Naked", position:"CHRO", email:$e}')" \
  -H "$(auth_header "$MEMBER_TOKEN")"
check_status V2.4 "a member mints a representative" 201
NAKED_REP_ID=$(json '.id')
check V2.5 "who is seated on no mandate at all" "0" \
  "$(sql "SELECT count(*) FROM app_lm_project_member p
            JOIN app_lm_workspace_member m ON m.id = p.member_id
            JOIN app_lm_user u ON u.id = m.user_id
          WHERE lower(u.email) = '$NAKED_EMAIL'")"

section "V3  project tier — mapping a client to a mandate is the LEAD's alone"

# A mandate whose lead is MEMBER2, with MEMBER seated on it as a RESEARCHER. Both are workspace
# MEMBERs, so both hold CLIENT_RECORD_MANAGE; the only thing telling them apart is the project seat.
MEMBER2_TOKEN=$(login_as "$MEMBER2_EMAIL")
post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Tier Mandate"}')" \
  -H "$(auth_header "$MEMBER2_TOKEN")"
check_status V3.1 "MEMBER2 creates a mandate and is its lead" 201
TIER_PROJECT=$(json '.id')

http PUT "/projects/$TIER_PROJECT/members/$(member_id_of "$MEMBER_EMAIL")" \
  -H 'Content-Type: application/json' -d '{"role":"RESEARCHER"}' -H "$(auth_header "$MEMBER2_TOKEN")"
check_status V3.2 "and seats MEMBER on it as a RESEARCHER" 200

# The representative from the fixtures has accepted, so attaching them creates a seat outright. An
# INVITED one is parked instead until they accept, which is covered separately at V3.9.
attach_as() { # attach_as TOKEN
  http POST "/projects/$TIER_PROJECT/representatives" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg r "$REPRESENTATIVE_ID" '{representativeId:$r}')" -H "$(auth_header "$1")"
}

attach_as "$MEMBER_TOKEN"
check_code V3.3 "a seated RESEARCHER cannot map a client to the mandate" 403 FORBIDDEN
note V3.4 "refusal: $(json '.detail')"

DUAL_TOKEN=$(login_as "$DUAL_EMAIL")
attach_as "$DUAL_TOKEN"
check_code V3.5 "a staff member with no seat cannot either" 403 FORBIDDEN

attach_as "$CLIENT_TOKEN"
check_code V3.6 "nor can a portal guest" 403 FORBIDDEN

attach_as "$MEMBER2_TOKEN"
check_status V3.7 "the mandate's LEAD can" 200
check V3.8 "and the representative now holds a seat" "1" \
  "$(sql "SELECT count(*) FROM app_lm_project_member p
            JOIN app_lm_workspace_member m ON m.id = p.member_id
            JOIN app_lm_user u ON u.id = m.user_id
          WHERE lower(u.email) = '$CLIENT_EMAIL' AND p.project_id = '$TIER_PROJECT'")"

# A representative who has not accepted yet cannot be seated, so the lead's decision is parked and
# redeemed when they do. Same gate, different landing place.
http POST "/projects/$TIER_PROJECT/representatives" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg r "$NAKED_REP_ID" '{representativeId:$r}')" -H "$(auth_header "$MEMBER_TOKEN")"
check_code V3.9 "a researcher cannot park one either" 403 FORBIDDEN
http POST "/projects/$TIER_PROJECT/representatives" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg r "$NAKED_REP_ID" '{representativeId:$r}')" -H "$(auth_header "$MEMBER2_TOKEN")"
check_status V3.10 "the lead parks an as-yet-unaccepted representative" 200
check V3.11 "which lands as a pending attachment, not a seat" "1" \
  "$(sql "SELECT count(*) FROM app_lm_project_pending_representative
          WHERE representative_id = '$NAKED_REP_ID' AND project_id = '$TIER_PROJECT'")"

section "V4  detaching is the same decision, so the same gate"

detach_as() {
  http DELETE "/projects/$TIER_PROJECT/representatives/$REPRESENTATIVE_ID" -H "$(auth_header "$1")"
}

detach_as "$MEMBER_TOKEN"
check_code V4.1 "a seated RESEARCHER cannot withdraw client access" 403 FORBIDDEN
detach_as "$CLIENT_TOKEN"
check_code V4.2 "nor a portal guest" 403 FORBIDDEN
detach_as "$MEMBER2_TOKEN"
check_status V4.3 "the LEAD can" 200
check V4.4 "and the seat is gone" "0" \
  "$(sql "SELECT count(*) FROM app_lm_project_member p
            JOIN app_lm_workspace_member m ON m.id = p.member_id
            JOIN app_lm_user u ON u.id = m.user_id
          WHERE lower(u.email) = '$CLIENT_EMAIL' AND p.project_id = '$TIER_PROJECT'")"
check V4.5 "and their view of that mandate closes immediately" "403" \
  "$(get "/projects/$TIER_PROJECT/position" -H "$(auth_header "$CLIENT_TOKEN")"; printf '%s' "$LAST_STATUS")"

section "V5  create-and-attach in one step needs BOTH tiers"

invite_to_mandate_as() { # invite_to_mandate_as TOKEN EMAIL
  post_json "/projects/$TIER_PROJECT/representatives/invitations" \
    "$(jq -nc --arg e "$2" '{fullName:"One Step", position:"COO", email:$e}')" \
    -H "$(auth_header "$1")"
}

invite_to_mandate_as "$MEMBER_TOKEN" "$(new_email onestepresearcher)"
check_code V5.1 "a seated RESEARCHER holds the registry half but not the mandate half" 403 FORBIDDEN

invite_to_mandate_as "$CLIENT_TOKEN" "$(new_email onestepclient)"
check_code V5.2 "a portal guest holds neither" 403 FORBIDDEN

ONE_STEP_EMAIL=$(new_email onestep)
invite_to_mandate_as "$MEMBER2_TOKEN" "$ONE_STEP_EMAIL"
check_status V5.3 "the LEAD holds both and it goes through" 200
check V5.4 "the contact was written to the registry" "1" \
  "$(sql "SELECT count(*) FROM app_lm_client_representative WHERE lower(email) = '$ONE_STEP_EMAIL'")"
check V5.5 "and parked against this mandate pending their accept" "1" \
  "$(sql "SELECT count(*) FROM app_lm_project_pending_representative pr
            JOIN app_lm_client_representative cr ON cr.id = pr.representative_id
          WHERE lower(cr.email) = '$ONE_STEP_EMAIL' AND pr.project_id = '$TIER_PROJECT'")"

section "V6  the workspace-admin bypass still covers the new action"

check V6.1 "the admin holds no seat on that mandate" "0" \
  "$(sql "SELECT count(*) FROM app_lm_project_member p
            JOIN app_lm_workspace_member m ON m.id = p.member_id
            JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$ADMIN_EMAIL' AND p.project_id = '$TIER_PROJECT'")"
attach_as "$ADMIN_TOKEN"
check_status V6.2 "and can still map a client — a search never strands on a departed lead" 200
detach_as "$ADMIN_TOKEN"
check_status V6.3 "and withdraw it" 200

# ...but the bypass is a workspace-admin bypass, not a cross-tenant one.
http POST "/projects/$OUTSIDER_PROJECT_ID/representatives" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg r "$NAKED_REP_ID" '{representativeId:$r}')" -H "$(auth_header "$ADMIN_TOKEN")"
check_code V6.4 "the bypass does not reach another tenant's mandate" 404 NOT_FOUND

section "V7  PROJECT_EDIT and client access are now separable"

# Both are LEAD-only today, so this cannot be proven by role arithmetic. It is proven by the seat: a
# researcher is refused client access with the same 403 they get for PROJECT_EDIT, and the two now
# come from different actions rather than the same one.
http PATCH "/projects/$TIER_PROJECT" -H 'Content-Type: application/json' \
  -d '{"targetDate":"2027-03-01"}' -H "$(auth_header "$MEMBER_TOKEN")"
check_code V7.1 "a researcher cannot move the target date either" 403 FORBIDDEN
http PATCH "/projects/$TIER_PROJECT" -H 'Content-Type: application/json' \
  -d '{"targetDate":"2027-03-01"}' -H "$(auth_header "$MEMBER2_TOKEN")"
check_status V7.2 "the lead can" 200
check V7.3 "and the lead's grant now lists both actions separately" "CLIENT_ACCESS_MANAGE,PROJECT_EDIT" \
  "$(sql "SELECT string_agg(a.name, ',' ORDER BY a.name) FROM app_lm_role_action ra
            JOIN app_lm_action a ON a.id = ra.action_id
            JOIN app_lm_role r ON r.id = ra.role_id
          WHERE r.scope = 'PROJECT' AND r.name = 'LEAD'
            AND a.name IN ('CLIENT_ACCESS_MANAGE', 'PROJECT_EDIT')")"

summary
