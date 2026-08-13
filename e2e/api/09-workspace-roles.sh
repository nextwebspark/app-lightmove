#!/usr/bin/env bash
# Phase 1 — every workspace-scoped endpoint, called as every role.
#
# Deliberately non-destructive: where an endpoint mutates (PATCH/DELETE a member, DELETE an
# invitation, PATCH the workspace), only the roles that must be REFUSED are driven here. The admin's
# success path for those lives in 10-role-invariants.sh, where the sequence is controlled and the
# state it leaves behind is expected.
#
# The DUAL column is the reason this file exists. A member who is also a client contact holds
# {MEMBER, CLIENT}; every one of their cells must match MEMBER, never PURE_CLIENT. If isPureClient
# ever loosens from "holds only CLIENT" to "holds CLIENT", this column is what goes red.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
. "$RUN_DIR/cast.env"

token_of() {
  case "$1" in
    admin)  printf '%s' "$ADMIN_TOKEN" ;;
    member) printf '%s' "$MEMBER_TOKEN" ;;
    dual)   printf '%s' "$DUAL_TOKEN" ;;
    client) printf '%s' "$CLIENT_TOKEN" ;;
    *) printf 'unknown-actor-%s' "$1" ;;
  esac
}

# row ID DESCRIPTION METHOD PATH DATA actor=STATUS[:CODE] ...
row() {
  local id="$1" desc="$2" method="$3" path="$4" data="$5"
  shift 5
  local spec actor want want_status want_code token
  for spec in "$@"; do
    actor="${spec%%=*}"; want="${spec#*=}"
    want_status="${want%%:*}"; want_code=""
    case "$want" in *:*) want_code="${want#*:}" ;; esac
    token=$(token_of "$actor")

    if [ -n "$data" ]; then
      http "$method" "$path" -H 'Content-Type: application/json' -d "$data" -H "$(auth_header "$token")"
    else
      http "$method" "$path" -H "$(auth_header "$token")"
    fi

    if [ -n "$want_code" ]; then
      check_code "$id/$actor" "$desc" "$want_status" "$want_code"
    else
      check_status "$id/$actor" "$desc" "$want_status"
    fi
  done
}

MEMBER2_MEMBER_ID=$(sql "SELECT m.id FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
                         WHERE u.email = '$MEMBER2_EMAIL' AND m.status = 'ACTIVE'")

section "R1  the roster"

row R1.1 "GET /members" GET /members "" \
  admin=200 member=200 dual=200 client=403:FORBIDDEN

row R1.2 "PATCH /members/{id} — role change is admin-only" \
  PATCH "/members/$MEMBER2_MEMBER_ID" '{"roles":["ADMIN"]}' \
  member=403:FORBIDDEN dual=403:FORBIDDEN client=403:FORBIDDEN

row R1.3 "DELETE /members/{id} — removal is admin-only" \
  DELETE "/members/$MEMBER2_MEMBER_ID" "" \
  member=403:FORBIDDEN dual=403:FORBIDDEN client=403:FORBIDDEN

# The roster body is the other half of the client fence: a pure client must not be listed, not merely
# refused the call. activeStaff() is what makes that true and nothing in the Java suite asserts it.
get /members -H "$(auth_header "$ADMIN_TOKEN")"
check R1.4 "the pure client is absent from the roster body" "0" \
  "$(json "[.[] | select(.email == \"$CLIENT_EMAIL\")] | length")"
check R1.5 "the dual-role member IS listed — they are staff" "1" \
  "$(json "[.[] | select(.email == \"$DUAL_EMAIL\")] | length")"
check R1.6 "and their roles carry both" "CLIENT,MEMBER" \
  "$(json "[.[] | select(.email == \"$DUAL_EMAIL\")] | .[0].roles | sort | join(\",\")")"

section "R2  invitations"

row R2.1 "GET /invitations" GET /invitations "" \
  admin=200 member=403:FORBIDDEN dual=403:FORBIDDEN client=403:FORBIDDEN

row R2.2 "POST /invitations" POST /invitations \
  "$(jq -nc --arg e "$(new_email probe)" '[{email:$e, role:"MEMBER"}]')" \
  member=403:FORBIDDEN dual=403:FORBIDDEN client=403:FORBIDDEN

row R2.3 "POST /invitations/{id}/resend" POST "/invitations/$INVITATION_ID/resend" "" \
  member=403:FORBIDDEN dual=403:FORBIDDEN client=403:FORBIDDEN

row R2.4 "DELETE /invitations/{id}" DELETE "/invitations/$INVITATION_ID" "" \
  member=403:FORBIDDEN dual=403:FORBIDDEN client=403:FORBIDDEN

section "R3  workspace settings"

row R3.1 "GET /workspace" GET /workspace "" \
  admin=200 member=200 dual=200 client=403:FORBIDDEN

row R3.2 "PATCH /workspace" PATCH /workspace "$(jq -nc --arg n "$WORKSPACE_NAME" '{name:$n}')" \
  member=403:FORBIDDEN dual=403:FORBIDDEN client=403:FORBIDDEN

row R3.3 "DELETE /workspace — the gate nothing tested" \
  DELETE /workspace "$(jq -nc --arg n "$WORKSPACE_NAME" '{confirmName:$n}')" \
  member=403:FORBIDDEN dual=403:FORBIDDEN client=403:FORBIDDEN

check R3.4 "and the workspace is still there afterwards" "1" \
  "$(sql "SELECT count(*) FROM app_lm_workspace WHERE name = '$WORKSPACE_NAME'")"

section "R4  the client registry"

row R4.1 "GET /clients" GET /clients "" \
  admin=200 member=200 dual=200 client=403:FORBIDDEN

row R4.2 "GET /clients/{id}" GET "/clients/$CLIENT_ID" "" \
  admin=200 member=200 dual=200 client=403:FORBIDDEN

row R4.3 "POST /clients" POST /clients "$(jq -nc --arg n "Probe Co $RANDOM" '{customName:$n}')" \
  client=403:FORBIDDEN

row R4.4 "PATCH /clients/{id}" PATCH "/clients/$CLIENT_ID" \
  '{"name":"Northwind Industries renamed","sector":"Industrials"}' \
  client=403:FORBIDDEN

row R4.5 "POST /clients/{id}/representatives" POST "/clients/$CLIENT_ID/representatives" \
  "$(jq -nc --arg e "$(new_email probe2)" '{fullName:"Probe Rep", email:$e}')" \
  client=403:FORBIDDEN

section "R5  projects and the company universe"

row R5.1 "GET /projects — any active member, scoped in the service" GET /projects "" \
  admin=200 member=200 dual=200 client=200

row R5.2 "POST /projects" POST /projects \
  "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Probe Role"}')" \
  client=403:FORBIDDEN

row R5.3 "GET /companies/sectors — PROJECT_BROWSE" GET /companies/sectors "" \
  admin=200 member=200 dual=200 client=403:FORBIDDEN

row R5.4 "GET /companies/search" GET "/companies/search?query=acme" "" \
  admin=200 member=200 dual=200 client=403:FORBIDDEN

section "R6  what each role's project list actually contains"

get /projects -H "$(auth_header "$ADMIN_TOKEN")"
ADMIN_PROJECTS=$(json 'length')
check R6.1 "an admin sees every mandate in the workspace" "true" "$(test "$ADMIN_PROJECTS" -ge 3 && echo true || echo false)"

get /projects -H "$(auth_header "$MEMBER_TOKEN")"
check R6.2 "a staff member sees them all too, seat or no seat" "$ADMIN_PROJECTS" "$(json 'length')"

get /projects -H "$(auth_header "$DUAL_TOKEN")"
check R6.3 "so does the dual-role member — CLIENT does not narrow staff" "$ADMIN_PROJECTS" "$(json 'length')"

get /projects -H "$(auth_header "$CLIENT_TOKEN")"
check R6.4 "the pure client sees ONLY the mandate they are seated on" "1" "$(json 'length')"
check R6.5 "and it is the right one" "$PROJECT_ID" "$(json '.[0].id')"

summary
