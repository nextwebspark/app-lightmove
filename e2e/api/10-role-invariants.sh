#!/usr/bin/env bash
# Phase 2 — privilege escalation, the freshness of the roles claim, and the invariants that are
# enforced imperatively rather than by annotation.
#
# These are the cases no UI can reach: the SPA never offers a member the role editor, so the only way
# to know the server refuses it is to send the request by hand.
#
# NOT IDEMPOTENT — it promotes, demotes and removes people, and the last-admin cases only mean
# anything when the workspace starts with exactly one admin. Re-run `fixtures.sh` before re-running
# this, or E1/E2/E5 will fail against the roles the previous run left behind.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
. "$RUN_DIR/cast.env"

member_id_of() {
  sql "SELECT m.id FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
       WHERE u.email = '$1' AND m.status = 'ACTIVE'"
}
roles_of() {
  sql "SELECT string_agg(r.name, ',' ORDER BY r.name)
       FROM app_lm_workspace_member m
         JOIN app_lm_user u ON u.id = m.user_id
         JOIN app_lm_workspace_member_role mr ON mr.member_id = m.id
         JOIN app_lm_role r ON r.id = mr.role_id
       WHERE u.email = '$1' AND m.status = 'ACTIVE'"
}
login_as() {
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
  json '.accessToken'
}
patch_roles() { # patch_roles TOKEN MEMBER_ID JSON_ARRAY
  http PATCH "/members/$2" -H 'Content-Type: application/json' \
    -d "$(jq -nc --argjson r "$3" '{roles:$r}')" -H "$(auth_header "$1")"
}

ADMIN_MEMBER_ID=$(member_id_of "$ADMIN_EMAIL")
MEMBER_MEMBER_ID=$(member_id_of "$MEMBER_EMAIL")
MEMBER2_MEMBER_ID=$(member_id_of "$MEMBER2_EMAIL")
DUAL_MEMBER_ID=$(member_id_of "$DUAL_EMAIL")
CLIENT_MEMBER_ID=$(member_id_of "$CLIENT_EMAIL")

section "E1  nobody can promote themselves"

patch_roles "$MEMBER_TOKEN" "$MEMBER_MEMBER_ID" '["ADMIN"]'
check_code E1.1 "a MEMBER promoting themselves to ADMIN" 403 FORBIDDEN
check E1.2 "and their roles are untouched" "MEMBER" "$(roles_of "$MEMBER_EMAIL")"

patch_roles "$CLIENT_TOKEN" "$CLIENT_MEMBER_ID" '["ADMIN"]'
check_code E1.3 "a pure CLIENT promoting themselves" 403 FORBIDDEN
check E1.4 "and their roles are untouched" "CLIENT" "$(roles_of "$CLIENT_EMAIL")"

patch_roles "$CLIENT_TOKEN" "$CLIENT_MEMBER_ID" '["MEMBER"]'
check_code E1.5 "a pure CLIENT promoting themselves to plain staff" 403 FORBIDDEN
check E1.6 "still a pure client" "CLIENT" "$(roles_of "$CLIENT_EMAIL")"

patch_roles "$DUAL_TOKEN" "$DUAL_MEMBER_ID" '["ADMIN","CLIENT"]'
check_code E1.7 "the dual-role member promoting themselves" 403 FORBIDDEN

patch_roles "$MEMBER_TOKEN" "$MEMBER2_MEMBER_ID" '["ADMIN"]'
check_code E1.8 "a MEMBER promoting somebody else" 403 FORBIDDEN
check E1.9 "the target is unchanged" "MEMBER" "$(roles_of "$MEMBER2_EMAIL")"

section "E2  the role set an admin may ask for"

patch_roles "$ADMIN_TOKEN" "$MEMBER2_MEMBER_ID" '["CLIENT"]'
check_code E2.1 "CLIENT cannot be granted through the roster" 400 VALIDATION_FAILED

# The rule's own wording now reaches the caller, rather than being logged at DEBUG while the response
# said only "One or more fields are invalid".
check E2.2 "and the admin is told why" \
  "Clients are invited to a project, not granted through the roster" "$(json '.detail')"
# As a banner rather than a field error, which is the right channel here: the roster editor is a role
# set, not a single input. ApiException.withField is used where a rule does belong to one field —
# the password rules take that path.
check E2.3 "as a banner, carrying no field attribution" "null" "$(json '.fieldErrors')"

patch_roles "$ADMIN_TOKEN" "$MEMBER2_MEMBER_ID" '["ADMIN","CLIENT"]'
check_code E2.3 "nor smuggled in alongside a staff role" 400 VALIDATION_FAILED

patch_roles "$ADMIN_TOKEN" "$MEMBER2_MEMBER_ID" '[]'
check_code E2.4 "an empty role set" 400 VALIDATION_FAILED

patch_roles "$ADMIN_TOKEN" "$MEMBER2_MEMBER_ID" '["SUPERADMIN"]'
check_status E2.5 "an invented role name" 400

patch_roles "$ADMIN_TOKEN" "$MEMBER2_MEMBER_ID" '["LEAD"]'
check_status E2.6 "a PROJECT role offered at workspace scope" 400

check E2.7 "the target survived every rejected payload unchanged" "MEMBER" "$(roles_of "$MEMBER2_EMAIL")"

section "E3  the roles claim is coarse material — the guard re-reads the database"

# CLAUDE.md's strongest authorisation claim, and nothing exercised it against a running server.
STALE_MEMBER_TOKEN="$MEMBER_TOKEN"
check E3.1 "the member's token claims MEMBER" "MEMBER" \
  "$(jwt_claims "$STALE_MEMBER_TOKEN" | jq -r '.roles | join(",")')"

patch_roles "$ADMIN_TOKEN" "$MEMBER_MEMBER_ID" '["ADMIN"]'
check_status E3.2 "the admin promotes them" 200
check E3.3 "the database says ADMIN" "ADMIN" "$(roles_of "$MEMBER_EMAIL")"
check E3.4 "but their existing token still claims MEMBER" "MEMBER" \
  "$(jwt_claims "$STALE_MEMBER_TOKEN" | jq -r '.roles | join(",")')"

get /invitations -H "$(auth_header "$STALE_MEMBER_TOKEN")"
check_status E3.5 "an admin-only route on the STALE token now succeeds — promotion is immediate" 200

patch_roles "$ADMIN_TOKEN" "$MEMBER_MEMBER_ID" '["MEMBER"]'
check_status E3.6 "the admin demotes them again" 200

get /invitations -H "$(auth_header "$STALE_MEMBER_TOKEN")"
check_code E3.7 "the same token is refused at once — demotion is immediate too" 403 FORBIDDEN
note E3.8 "the token still claims $(jwt_claims "$STALE_MEMBER_TOKEN" | jq -r '.roles | join(",")') throughout"

section "E4  a removed member's live token"

GHOST_EMAIL=$(new_email ghost)
post_json /invitations "$(jq -nc --arg e "$GHOST_EMAIL" '[{email:$e, role:"MEMBER"}]')" \
  -H "$(auth_header "$ADMIN_TOKEN")" >/dev/null
post_json /onboarding/accept-invitation-signup \
  "$(jq -nc --arg t "$(token_for "$GHOST_EMAIL" accept-invite)" --arg p "$PASSWORD" \
     '{token:$t, fullName:"Gil Ghost", password:$p}')" >/dev/null
GHOST_TOKEN=$(json '.accessToken')
GHOST_MEMBER_ID=$(member_id_of "$GHOST_EMAIL")

get /workspace -H "$(auth_header "$GHOST_TOKEN")"
check_status E4.1 "the new member can read the workspace" 200

http DELETE "/members/$GHOST_MEMBER_ID" -H "$(auth_header "$ADMIN_TOKEN")"
check_status E4.2 "the admin removes them" 204

get /workspace -H "$(auth_header "$GHOST_TOKEN")"
check_code E4.3 "their still-valid access token no longer reaches the workspace" 404 NOT_A_MEMBER
get /projects -H "$(auth_header "$GHOST_TOKEN")"
check_code E4.4 "nor the project list" 404 NOT_A_MEMBER
get /auth/me -H "$(auth_header "$GHOST_TOKEN")"
check E4.5 "/me shows them with no workspace at all" "null" "$(json '.workspace')"

section "E5  a workspace keeps at least one admin"

check E5.1 "there is exactly one admin right now" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace_member m
            JOIN app_lm_workspace_member_role mr ON mr.member_id = m.id
            JOIN app_lm_role r ON r.id = mr.role_id
          WHERE m.workspace_id = (SELECT id FROM app_lm_workspace WHERE name = '$WORKSPACE_NAME')
            AND m.status = 'ACTIVE' AND r.name = 'ADMIN' AND r.scope = 'WORKSPACE'")"

patch_roles "$ADMIN_TOKEN" "$ADMIN_MEMBER_ID" '["MEMBER"]'
check_code E5.2 "the sole admin demoting themselves" 409 LAST_ADMIN
check E5.3 "and they are still ADMIN" "ADMIN" "$(roles_of "$ADMIN_EMAIL")"

http DELETE "/members/$ADMIN_MEMBER_ID" -H "$(auth_header "$ADMIN_TOKEN")"
check_code E5.4 "the sole admin leaving" 409 LAST_ADMIN
check E5.5 "and they are still a member" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$ADMIN_EMAIL' AND m.status = 'ACTIVE'")"

patch_roles "$ADMIN_TOKEN" "$MEMBER2_MEMBER_ID" '["ADMIN"]'
check_status E5.6 "promote a second admin" 200

patch_roles "$ADMIN_TOKEN" "$ADMIN_MEMBER_ID" '["MEMBER"]'
check_status E5.7 "now the first admin CAN step down" 200
check E5.8 "and is a plain member" "MEMBER" "$(roles_of "$ADMIN_EMAIL")"

# Everything after this point needs an admin token again, and the old one is no longer one.
ADMIN2_TOKEN="$MEMBER2_TOKEN"
get /invitations -H "$(auth_header "$ADMIN2_TOKEN")"
check_status E5.9 "the promoted admin's own stale token works immediately" 200

http PATCH "/members/$ADMIN_MEMBER_ID" -H 'Content-Type: application/json' \
  -d '{"roles":["ADMIN"]}' -H "$(auth_header "$ADMIN2_TOKEN")"
check_status E5.10 "and can hand the role back" 200
ADMIN_TOKEN=$(login_as "$ADMIN_EMAIL")

section "E6  a member who leads a live mandate cannot simply be removed"

LEAD_EMAIL=$(new_email lead)
post_json /invitations "$(jq -nc --arg e "$LEAD_EMAIL" '[{email:$e, role:"MEMBER"}]')" \
  -H "$(auth_header "$ADMIN_TOKEN")" >/dev/null
post_json /onboarding/accept-invitation-signup \
  "$(jq -nc --arg t "$(token_for "$LEAD_EMAIL" accept-invite)" --arg p "$PASSWORD" \
     '{token:$t, fullName:"Lee Lead", password:$p}')" >/dev/null
LEAD_TOKEN=$(json '.accessToken')
LEAD_MEMBER_ID=$(member_id_of "$LEAD_EMAIL")

post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Sole Lead Mandate"}')" \
  -H "$(auth_header "$LEAD_TOKEN")"
check_status E6.1 "they create a mandate, becoming its sole lead" 201
SOLE_LEAD_PROJECT=$(json '.id')

http DELETE "/members/$LEAD_MEMBER_ID" -H "$(auth_header "$ADMIN_TOKEN")"
check_code E6.2 "removing the sole lead of a live mandate" 409 MEMBER_LEADS_PROJECTS
note E6.3 "message: $(json '.detail')"

# The exemption: a mandate that is finished no longer holds anybody hostage.
sql_run "UPDATE app_lm_project SET stage = 'DELIVERED' WHERE id = '$SOLE_LEAD_PROJECT'"
http DELETE "/members/$LEAD_MEMBER_ID" -H "$(auth_header "$ADMIN_TOKEN")"
check_status E6.4 "once the mandate is DELIVERED, the same removal succeeds" 204
check E6.5 "and their seats are gone" "0" \
  "$(sql "SELECT count(*) FROM app_lm_project_member WHERE member_id = '$LEAD_MEMBER_ID'")"

section "E7  the workspace-admin bypass at project tier is deliberate — pin it"

# MEMBER2 created LED_PROJECT_ID, so neither the admin nor MEMBER holds a seat on it.
check E7.1 "the admin has no seat on that mandate" "0" \
  "$(sql "SELECT count(*) FROM app_lm_project_member p
            JOIN app_lm_workspace_member m ON m.id = p.member_id
            JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$ADMIN_EMAIL' AND p.project_id = '$LED_PROJECT_ID'")"

get "/projects/$LED_PROJECT_ID/position" -H "$(auth_header "$ADMIN_TOKEN")"
check_status E7.2 "and still reads its brief — the admin bypass" 200
get "/projects/$LED_PROJECT_ID/strategy" -H "$(auth_header "$ADMIN_TOKEN")"
check_status E7.3 "and its strategy" 200

get "/projects/$LED_PROJECT_ID/position" -H "$(auth_header "$MEMBER_TOKEN")"
check_code E7.4 "a plain member without a seat cannot" 403 FORBIDDEN
get "/projects/$LED_PROJECT_ID/strategy" -H "$(auth_header "$MEMBER_TOKEN")"
check_code E7.5 "on either surface" 403 FORBIDDEN

summary
