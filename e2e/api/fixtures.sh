#!/usr/bin/env bash
# Builds the cast the workspace-RBAC matrix runs against, once, and writes results/current/cast.env
# for 09-12 to source. Built once rather than per script because signup is rate limited and six
# accounts x four scripts would trip it.
#
# The cast follows the business flow the session is testing: an admin invites a member, the member
# creates a client and invites its representative, the representative signs up and logs in directly.
# Two extra actors exist only to find bugs:
#
#   DUAL     — a staff member who is ALSO a client contact. Must be treated as staff everywhere; this
#              is what tells apart "holds CLIENT" from "holds only CLIENT".
#   OUTSIDER — an admin of a second workspace, for proving every id is tenant-scoped.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "Fixtures — building the cast"

# --- helpers ----------------------------------------------------------------

signup_verified() { # signup_verified EMAIL FULLNAME -> access token, verified, no workspace
  post_json /auth/signup "$(jq -nc --arg e "$1" --arg p "$PASSWORD" --arg n "$2" \
    '{fullName:$n, email:$e, password:$p, termsAccepted:true}')" >/dev/null
  post_json /auth/verify "$(jq -nc --arg t "$(token_for "$1" verify)" '{token:$t}')" >/dev/null
  login_as "$1"
}

login_as() { # login_as EMAIL -> a fresh access token carrying whatever membership exists now
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
  json '.accessToken'
}

make_workspace() { # make_workspace TOKEN NAME
  post_json /onboarding/workspace "$(jq -nc --arg n "$2" \
    '{name:$n, companySize:"11-50 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
    -H "$(auth_header "$1")" >/dev/null
}

invite_staff() { # invite_staff ADMIN_TOKEN EMAIL ROLE
  post_json /invitations "$(jq -nc --arg e "$2" --arg r "$3" '[{email:$e, role:$r}]')" \
    -H "$(auth_header "$1")" >/dev/null
}

accept_invite_signup() { # accept_invite_signup EMAIL FULLNAME -> access token
  post_json /onboarding/accept-invitation-signup \
    "$(jq -nc --arg t "$(token_for "$1" accept-invite)" --arg n "$2" --arg p "$PASSWORD" \
       '{token:$t, fullName:$n, password:$p}')" >/dev/null
  json '.accessToken'
}

member_id_of() { # member_id_of EMAIL
  sql "SELECT m.id FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
       WHERE u.email = '$1' AND m.status = 'ACTIVE'"
}

roles_of() { # roles_of EMAIL -> comma-separated, sorted
  sql "SELECT string_agg(r.name, ',' ORDER BY r.name)
       FROM app_lm_workspace_member m
         JOIN app_lm_user u ON u.id = m.user_id
         JOIN app_lm_workspace_member_role mr ON mr.member_id = m.id
         JOIN app_lm_role r ON r.id = mr.role_id
       WHERE u.email = '$1' AND m.status = 'ACTIVE'"
}

# --- the primary workspace --------------------------------------------------

ADMIN_EMAIL=$(new_email admin)
MEMBER_EMAIL=$(new_email member)
MEMBER2_EMAIL=$(new_email member2)
DUAL_EMAIL=$(new_email dual)
CLIENT_EMAIL=$(new_email client)
OUTSIDER_EMAIL=$(new_email outsider)
WORKSPACE_NAME="Meridian Search $(date +%s)$RANDOM"

ADMIN_TOKEN=$(signup_verified "$ADMIN_EMAIL" "Ada Admin")
make_workspace "$ADMIN_TOKEN" "$WORKSPACE_NAME"
# A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
ADMIN_TOKEN=$(login_as "$ADMIN_EMAIL")
check fixtures.1 "admin holds ADMIN" "ADMIN" "$(roles_of "$ADMIN_EMAIL")"

for pair in "$MEMBER_EMAIL:Mel Member" "$MEMBER2_EMAIL:Moe Member" "$DUAL_EMAIL:Dana Dual"; do
  invite_staff "$ADMIN_TOKEN" "${pair%%:*}" MEMBER
done

MEMBER_TOKEN=$(accept_invite_signup "$MEMBER_EMAIL" "Mel Member")
MEMBER2_TOKEN=$(accept_invite_signup "$MEMBER2_EMAIL" "Moe Member")
DUAL_TOKEN=$(accept_invite_signup "$DUAL_EMAIL" "Dana Dual")
check fixtures.2 "the invited colleague holds MEMBER" "MEMBER" "$(roles_of "$MEMBER_EMAIL")"

# --- the client registry, created by the MEMBER (they hold CLIENT_RECORD_MANAGE) ---

CLIENT_NAME="Northwind Industries $RANDOM"
post_json /clients "$(jq -nc --arg n "$CLIENT_NAME" \
  '{customName:$n, customDomain:"northwind.example", sector:"Industrials", hqCountry:"UAE"}')" \
  -H "$(auth_header "$MEMBER_TOKEN")"
check_status fixtures.3 "a plain MEMBER can create a client" 201
CLIENT_ID=$(json '.id')

OTHER_CLIENT_NAME="Sirocco Group $RANDOM"
post_json /clients "$(jq -nc --arg n "$OTHER_CLIENT_NAME" \
  '{customName:$n, sector:"Energy", hqCountry:"KSA"}')" -H "$(auth_header "$ADMIN_TOKEN")"
OTHER_CLIENT_ID=$(json '.id')

# --- two projects, so a client's list scoping can be told from "sees everything" ---

post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Chief Financial Officer"}')" \
  -H "$(auth_header "$ADMIN_TOKEN")"
check_status fixtures.4 "project on the attached client" 201
PROJECT_ID=$(json '.id')

post_json /projects "$(jq -nc --arg c "$OTHER_CLIENT_ID" '{clientId:$c, positionTitle:"Head of Trading"}')" \
  -H "$(auth_header "$ADMIN_TOKEN")"
OTHER_PROJECT_ID=$(json '.id')

# A project led by MEMBER2, for the sole-lead removal invariant.
post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"VP Engineering"}')" \
  -H "$(auth_header "$MEMBER2_TOKEN")"
LED_PROJECT_ID=$(json '.id')

# --- the representative: stranger path, signs up and logs in directly -------

post_json "/clients/$CLIENT_ID/representatives" \
  "$(jq -nc --arg e "$CLIENT_EMAIL" '{fullName:"Cass Client", position:"Group CFO", email:$e}')" \
  -H "$(auth_header "$MEMBER_TOKEN")"
check_status fixtures.5 "a MEMBER can invite a client representative" 201
REPRESENTATIVE_ID=$(json '.id')

CLIENT_TOKEN=$(accept_invite_signup "$CLIENT_EMAIL" "Cass Client")
check fixtures.6 "the representative lands as a PURE client" "CLIENT" "$(roles_of "$CLIENT_EMAIL")"
CLIENT_TOKEN=$(login_as "$CLIENT_EMAIL")

post_json "/projects/$PROJECT_ID/representatives" \
  "$(jq -nc --arg r "$REPRESENTATIVE_ID" '{representativeId:$r}')" -H "$(auth_header "$ADMIN_TOKEN")"
check_status fixtures.7 "the representative is seated on one mandate" 200

# --- the dual-role actor: already staff, then made a representative ---------

post_json "/clients/$OTHER_CLIENT_ID/representatives" \
  "$(jq -nc --arg e "$DUAL_EMAIL" '{fullName:"Dana Dual", position:"Board Observer", email:$e}')" \
  -H "$(auth_header "$ADMIN_TOKEN")"
check_status fixtures.8 "an existing staff member can also be a client contact" 201
DUAL_REPRESENTATIVE_ID=$(json '.id')
check fixtures.9 "and now holds BOTH roles" "CLIENT,MEMBER" "$(roles_of "$DUAL_EMAIL")"
DUAL_TOKEN=$(login_as "$DUAL_EMAIL")

# --- a second workspace, for tenant isolation -------------------------------

OUTSIDER_TOKEN=$(signup_verified "$OUTSIDER_EMAIL" "Otto Outsider")
OUTSIDER_WORKSPACE="Rival Partners $RANDOM"
make_workspace "$OUTSIDER_TOKEN" "$OUTSIDER_WORKSPACE"
OUTSIDER_TOKEN=$(login_as "$OUTSIDER_EMAIL")

post_json /clients "$(jq -nc --arg n "Rival Client $RANDOM" '{customName:$n}')" \
  -H "$(auth_header "$OUTSIDER_TOKEN")"
OUTSIDER_CLIENT_ID=$(json '.id')
post_json /projects "$(jq -nc --arg c "$OUTSIDER_CLIENT_ID" '{clientId:$c, positionTitle:"Rival Role"}')" \
  -H "$(auth_header "$OUTSIDER_TOKEN")"
OUTSIDER_PROJECT_ID=$(json '.id')

# A live invitation in each workspace, for the invitation-id cases.
invite_staff "$ADMIN_TOKEN" "$(new_email pending)" MEMBER
get /invitations -H "$(auth_header "$ADMIN_TOKEN")"
INVITATION_ID=$(json '.[0].id')
invite_staff "$OUTSIDER_TOKEN" "$(new_email rivalpending)" MEMBER
get /invitations -H "$(auth_header "$OUTSIDER_TOKEN")"
OUTSIDER_INVITATION_ID=$(json '.[0].id')

# --- hand it all to the matrix ----------------------------------------------

# Every value is quoted: workspace and client names carry spaces, and an unquoted assignment in a
# sourced file runs the rest of the name as a command.
{
  for name in ADMIN_EMAIL MEMBER_EMAIL MEMBER2_EMAIL DUAL_EMAIL CLIENT_EMAIL OUTSIDER_EMAIL \
              ADMIN_TOKEN MEMBER_TOKEN MEMBER2_TOKEN DUAL_TOKEN CLIENT_TOKEN OUTSIDER_TOKEN \
              WORKSPACE_NAME OUTSIDER_WORKSPACE CLIENT_NAME OTHER_CLIENT_NAME \
              CLIENT_ID OTHER_CLIENT_ID OUTSIDER_CLIENT_ID \
              PROJECT_ID OTHER_PROJECT_ID LED_PROJECT_ID OUTSIDER_PROJECT_ID \
              REPRESENTATIVE_ID DUAL_REPRESENTATIVE_ID INVITATION_ID OUTSIDER_INVITATION_ID; do
    eval "printf '%s=%q\n' \"\$name\" \"\${$name}\""
  done
} > "$RUN_DIR/cast.env"

summary
printf 'cast written to %s\n' "$RUN_DIR/cast.env"
