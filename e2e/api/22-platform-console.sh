#!/usr/bin/env bash
# The platform console (docs/platform-console.md) end to end, with both rollout switches on as application-e2e.yml
# sets them: a super admin is asked for two-factor before anything opens and for a fresh code before a high-impact
# act, then changes and resets a variable, saves a prompt version, puts it live and rolls it back; and a workspace
# admin, a member and a client are told 404 on every platform route, a bad body included. Try it and eval runs
# call the model, which this stack does not have; @IntegrationTest covers them.
#
# Builds its own cast and leaves it in platform.env for spa/platform.mjs.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "22 — the platform console"

login_as() { # login_as EMAIL [JAR] -> access token; the jar keeps the refresh cookie two-factor setup asks for
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" \
    ${2:+-c "$(jar "$2")"} >/dev/null
  json '.accessToken'
}

signup_verified() { # signup_verified EMAIL FULLNAME -> access token, verified, no workspace yet
  post_json /auth/signup "$(jq -nc --arg e "$1" --arg p "$PASSWORD" --arg n "$2" \
    '{fullName:$n, email:$e, password:$p, termsAccepted:true}')" >/dev/null
  post_json /auth/verify "$(jq -nc --arg t "$(token_for "$1" verify)" '{token:$t}')" >/dev/null
  login_as "$1"
}

accept_invite_signup() { # accept_invite_signup EMAIL FULLNAME
  post_json /onboarding/accept-invitation-signup \
    "$(jq -nc --arg t "$(token_for "$1" accept-invite)" --arg n "$2" --arg p "$PASSWORD" \
       '{token:$t, fullName:$n, password:$p, termsAccepted:true}')" >/dev/null
}

grant_super_admin() { # what ops/cloudsql/grant-platform-role.sh does; no application route grants the role
  sql_run "INSERT INTO app_lm_user_platform_role (user_id, role_id)
           SELECT u.id, r.id FROM app_lm_user u, app_lm_role r
           WHERE u.email = '$1' AND r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'"
}

# A code for now. The one before it was spent, and the API refuses a code of a step it has already taken; clearing
# the step deliberately bypasses that replay protection, to spare the run a 30-second wait per code. The refusal
# itself is covered by the two-factor integration tests, not here.
fresh_code() { # fresh_code EMAIL SECRET
  sql_run "UPDATE app_lm_user_mfa_factor SET last_used_step = NULL
           WHERE user_id = (SELECT id FROM app_lm_user WHERE email = '$1')"
  node "$(dirname "${BASH_SOURCE[0]}")/totp.mjs" "$2"
}

# mutate METHOD PATH JSON: a write as the super admin, with the session's cookies and CSRF pair
mutate() {
  http "$1" "$2" -H 'Content-Type: application/json' -d "$3" -H "$SUPER" \
    -b "$(jar super)" -c "$(jar super)" -H "$(csrf_header super)"
}

# --- the cast -------------------------------------------------------------------

SUPER_EMAIL=$(new_email platsuper)
UNENROLLED_EMAIL=$(new_email platnew)
ADMIN_EMAIL=$(new_email platadmin)
MEMBER_EMAIL=$(new_email platmember)
CLIENT_EMAIL=$(new_email platclient)

for founder in "$SUPER_EMAIL:Noor Rahman:Platform Staff" "$UNENROLLED_EMAIL:Dana Aboud:Platform Newcomer" \
               "$ADMIN_EMAIL:Yara Haddad:Ordinary Firm"; do
  IFS=: read -r address name firm <<<"$founder"
  token=$(signup_verified "$address" "$name")
  post_json /onboarding/workspace "$(jq -nc --arg n "$firm $(date +%s)$RANDOM" '{mode:"COMPANY", name:$n}')" \
    -H "$(auth_header "$token")" >/dev/null
done
grant_super_admin "$SUPER_EMAIL"
grant_super_admin "$UNENROLLED_EMAIL"

# A token minted before the workspace existed carries no wsId, so each signs in again.
SUPER="$(auth_header "$(login_as "$SUPER_EMAIL" super)")"
UNENROLLED="$(auth_header "$(login_as "$UNENROLLED_EMAIL")")"
ADMIN="$(auth_header "$(login_as "$ADMIN_EMAIL")")"

post_json /invitations "$(jq -nc --arg e "$MEMBER_EMAIL" '[{email:$e, role:"MEMBER"}]')" -H "$ADMIN" >/dev/null
accept_invite_signup "$MEMBER_EMAIL" "Mira Member"
MEMBER="$(auth_header "$(login_as "$MEMBER_EMAIL")")"

post_json /clients '{"customName":"Platform Probe Holding"}' -H "$ADMIN" >/dev/null
post_json "/clients/$(json '.id')/representatives" \
  "$(jq -nc --arg e "$CLIENT_EMAIL" '{fullName:"Cora Client", position:"Sponsor", email:$e}')" -H "$ADMIN" >/dev/null
accept_invite_signup "$CLIENT_EMAIL" "Cora Client"
CLIENT="$(auth_header "$(login_as "$CLIENT_EMAIL")")"

{
  printf 'PLATFORM_UNENROLLED_EMAIL=%q\n' "$UNENROLLED_EMAIL"
  printf 'PLATFORM_ADMIN_EMAIL=%q\n' "$ADMIN_EMAIL"
  printf 'PLATFORM_MEMBER_EMAIL=%q\n' "$MEMBER_EMAIL"
  printf 'PLATFORM_CLIENT_EMAIL=%q\n' "$CLIENT_EMAIL"
} > "$RUN_DIR/platform.env"

# --- two-factor, then a fresh code ----------------------------------------------
section "22.1  a super admin needs two-factor, and a fresh code for a high-impact act"

get /auth/me -H "$SUPER"
check 22.1a "/me tells the super admin which platform actions they hold" "true" \
  "$(json '.platformActions | index("PROMPT_PROMOTE") != null')"
get /platform/prompts -H "$SUPER"
check_code 22.1b "without two-factor the console stays shut" 403 PLATFORM_TWO_FACTOR_REQUIRED

http POST /auth/mfa/setup -H "$SUPER" -b "$(jar super)" -c "$(jar super)" -H "$(csrf_header super)"
SECRET=$(json '.secret')
mutate POST /auth/mfa/enable "$(jq -nc --arg p "$PASSWORD" --arg c "$(fresh_code "$SUPER_EMAIL" "$SECRET")" \
  '{currentPassword:$p, code:$c}')"
check 22.1c "two-factor is turned on from the session" "true" "$(json '.recoveryCodes | length > 0')"

get /platform/prompts -H "$SUPER"
check_status 22.1d "with it on, the prompts list opens" 200
check 22.1e "every shipped prompt is listed with the version that serves" "true" \
  "$(json 'length >= 16 and all(.[]; .liveVersion >= 1)')"

get /platform/settings -H "$SUPER"
check_status 22.1f "and the variables" 200
KEY=$(json '.[0].key')
DEFAULT=$(json '.[0].defaultValue')
MIN=$(json '.[0].min')
MAX=$(json '.[0].max')
NEW=$([ "$DEFAULT" = "$MIN" ] && echo "$MAX" || echo "$MIN")

mutate PUT "/platform/settings/$KEY" "$(jq -nc --arg v "$NEW" '{value:$v, note:"e2e: a change"}')"
check_code 22.1g "changing a variable asks for a fresh code first" 403 PLATFORM_STEP_UP_REQUIRED

mutate POST /platform/step-up '{"code":"000000"}'
check 22.1h "a wrong code is not a fresh one" "false" "$([ "$LAST_STATUS" = 204 ] && echo true || echo false)"
mutate POST /platform/step-up "$(jq -nc --arg c "$(fresh_code "$SUPER_EMAIL" "$SECRET")" '{code:$c}')"
check_status 22.1i "the right code is" 204

# --- a variable -----------------------------------------------------------------
section "22.2  a variable is changed and reset, with its history kept"

mutate PUT "/platform/settings/$KEY" "$(jq -nc --arg v "$NEW" '{value:$v, note:"e2e: a change"}')"
check_status 22.2a "the change is taken" 200
get /platform/settings -H "$SUPER"
check 22.2b "the list shows the new value as changed" "$NEW true" \
  "$(printf '%s' "$LAST_BODY" | jq -r --arg k "$KEY" '.[] | select(.key == $k) | "\(.value) \(.changed)"')"

mutate PUT "/platform/settings/$KEY" "$(jq -nc --arg v "$((MAX + 1))" '{value:$v, note:"e2e: out of range"}')"
check_status 22.2c "a value outside the variable's range is refused" 400

mutate POST "/platform/settings/$KEY/reset" '{"note":"e2e: back to the default"}'
check_status 22.2d "a reset is taken" 200
get /platform/settings -H "$SUPER"
check 22.2e "and the default serves again" "$DEFAULT false" \
  "$(printf '%s' "$LAST_BODY" | jq -r --arg k "$KEY" '.[] | select(.key == $k) | "\(.value) \(.changed)"')"
get "/platform/settings/$KEY/history" -H "$SUPER"
check 22.2f "both acts are in the variable's history" "true" "$(json 'length >= 2')"

# --- a prompt -------------------------------------------------------------------
section "22.3  a prompt version is saved, put live and rolled back"

PROMPT=/platform/prompts/recruiter-shortlist
get "$PROMPT" -H "$SUPER"
SHIPPED=$(json '.prompt.liveVersion')

mutate POST "$PROMPT/versions" '{"body":"Weigh the candidate against the brief, briefly.","changeNote":"e2e: shorter"}'
check_status 22.3a "a save is a new version" 201
EDITED=$(json '.versions[0].version')
check 22.3b "which serves nothing yet" "$SHIPPED" "$(json '.prompt.liveVersion')"

mutate PUT "$PROMPT/production" "$(jq -nc --argjson v "$EDITED" '{version:$v, reason:"e2e: no eval run for this prompt"}')"
check_status 22.3c "it is put live with a written reason" 200
check 22.3d "and is the version that serves" "$EDITED EDITED" "$(json '"\(.prompt.liveVersion) \(.prompt.origin)"')"
get "$PROMPT/versions/$EDITED" -H "$SUPER"
check 22.3e "its text is the text saved" "Weigh the candidate against the brief, briefly." "$(json '.body')"
get "$PROMPT/usage" -H "$SUPER"
check_status 22.3f "its usage reads, with nothing counted on a stack that calls no model" 200

mutate PUT "$PROMPT/production" "$(jq -nc --argjson v "$SHIPPED" '{version:$v, reason:"e2e: rolled back"}')"
check_status 22.3g "the shipped version is put back" 200
check 22.3h "and serves again" "$SHIPPED" "$(json '.prompt.liveVersion')"
check 22.3i "the two moves are audited, never the prompt's text" "2 0" \
  "$(sql "SELECT count(*) || ' ' || count(*) FILTER (WHERE metadata::text LIKE '%Weigh the candidate%')
          FROM app_lm_audit_event WHERE event_type IN ('PROMPT_PUT_LIVE', 'PROMPT_ROLLED_BACK')
            AND actor_user_id = (SELECT id FROM app_lm_user WHERE email = '$SUPER_EMAIL')")"

# --- everyone else --------------------------------------------------------------
section "22.4  a workspace admin, a member and a client are told none of it exists"

# A few of each kind, against the built image. The exhaustive check is
# PlatformAuthorizationIntegrationTest.noPlatformRouteAdmitsToExisting, which reads the routes off the handler
# mappings and tries every method on each; a list kept here would drift from them.
ROUTES="
GET /platform/prompts
PUT /platform/settings/$KEY
POST /platform/workspaces/$(new_uuid)/credit-grants
DELETE /platform/settings
OPTIONS /platform/settings
GET /platform/no-such-route
"
sweep() { # sweep ID WHO AUTH_HEADER: a read, two writes with a body their route would refuse, two wrong methods, no route
  local admitted="" method path
  while read -r method path; do
    [ -z "$method" ] && continue
    if [ "$method" = GET ]; then get "$path" -H "$3"; else http "$method" "$path" -H 'Content-Type: application/json' -d '{}' -H "$3"; fi
    [ "$LAST_STATUS" = 404 ] || admitted="$admitted $method $path=$LAST_STATUS"
  done <<<"$ROUTES"
  check "$1" "$2 gets 404 on a platform route, a wrong method and a bad body included" "" "$admitted"
}
sweep 22.4a "a workspace admin" "$ADMIN"
sweep 22.4b "a member" "$MEMBER"
sweep 22.4c "a client" "$CLIENT"

holds_nothing() { # holds_nothing ID WHO AUTH_HEADER
  get /auth/me -H "$3"
  check "$1" "$2 holds no platform action, so the SPA offers no Platform item" "0" "$(json '.platformActions | length')"
}
holds_nothing 22.4d "a workspace admin" "$ADMIN"
holds_nothing 22.4e "a member" "$MEMBER"
holds_nothing 22.4f "a client" "$CLIENT"

get /platform/prompts -H "$UNENROLLED"
check_code 22.4g "a super admin who has not set up two-factor is told to, not shown the console" 403 \
  PLATFORM_TWO_FACTOR_REQUIRED

summary
