#!/usr/bin/env bash
# A workspace that requires two-factor authentication (#1082): an admin turns it on once they hold a factor, a
# member without one reads nothing of the workspace until they set one up, switching into such a workspace lands
# on enrolment, and an admin resets a member's lost factor.
#
# Needs the stack booted with two-factor offered (MFA_ENABLED and a keyset — e2e/stack/up.sh supplies both). Builds
# its own cast, does nothing destructive, and needs no Apollo universe.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

TOTP="$(dirname "${BASH_SOURCE[0]}")/totp.mjs"

section "22 — a workspace that requires two-factor authentication"

signup_verified() { # signup_verified EMAIL FULLNAME
  post_json /auth/signup "$(jq -nc --arg e "$1" --arg p "$PASSWORD" --arg n "$2" \
    '{fullName:$n, email:$e, password:$p, termsAccepted:true}')" >/dev/null
  post_json /auth/verify "$(jq -nc --arg t "$(token_for "$1" verify)" '{token:$t}')" >/dev/null
}
login_as() { # login_as EMAIL JAR -> a fresh session; echoes its access token
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" -c "$(jar "$2")" >/dev/null
  json '.accessToken'
}
as_session() { # as_session METHOD PATH TOKEN JAR [BODY]: a call under /auth that needs the refresh cookie and CSRF
  if [ -n "${5:-}" ]; then
    http "$1" "$2" -H "$(auth_header "$3")" -b "$(jar "$4")" -c "$(jar "$4")" -H "$(csrf_header "$4")" \
      -H 'Content-Type: application/json' -d "$5"
  else
    http "$1" "$2" -H "$(auth_header "$3")" -b "$(jar "$4")" -c "$(jar "$4")" -H "$(csrf_header "$4")"
  fi
}
enrol() { # enrol TOKEN JAR -> turns two-factor on; echoes nothing, leaves the answer in LAST_*
  as_session POST /auth/mfa/setup "$1" "$2"
  local secret
  secret=$(json '.secret')
  as_session POST /auth/mfa/enable "$1" "$2" \
    "$(jq -nc --arg p "$PASSWORD" --arg c "$(node "$TOTP" "$secret")" '{currentPassword:$p, code:$c}')"
}
invite_existing() { # invite_existing ADMIN_TOKEN EMAIL INVITEE_JAR
  post_json /invitations "$(jq -nc --arg e "$2" '[{email:$e, role:"MEMBER"}]')" -H "$(auth_header "$1")" >/dev/null
  local invitee
  invitee=$(login_as "$2" "$3")
  post_json /onboarding/invitations/accept "$(jq -nc --arg t "$(token_for "$2")" '{token:$t}')" \
    -H "$(auth_header "$invitee")" >/dev/null
}

# --- the cast: Sara runs the strict firm, Alok also has a firm of his own, Omar is in Sara's alone ----------

SARA=$(new_email sara); ALOK=$(new_email alok); OMAR=$(new_email omar)
signup_verified "$SARA" "Sara Ghanem"
SARA_TOKEN=$(login_as "$SARA" sara)
post_json /onboarding/workspace "$(jq -nc --arg n "Strict Firm $(date +%s)$RANDOM" '{mode:"COMPANY", name:$n}')" \
  -H "$(auth_header "$SARA_TOKEN")" >/dev/null
STRICT_ID=$(json '.workspace.id')
SARA_TOKEN=$(login_as "$SARA" sara)

signup_verified "$ALOK" "Alok Kumar"
ALOK_TOKEN=$(login_as "$ALOK" alok)
post_json /onboarding/workspace "$(jq -nc --arg n "Alok Firm $(date +%s)$RANDOM" '{mode:"COMPANY", name:$n}')" \
  -H "$(auth_header "$ALOK_TOKEN")" >/dev/null
ALOK_OWN_ID=$(json '.workspace.id')
invite_existing "$SARA_TOKEN" "$ALOK" alok

signup_verified "$OMAR" "Omar Khalil"
invite_existing "$SARA_TOKEN" "$OMAR" omar

# --- M1: turning the requirement on -------------------------------------------------------------------------

section "M1  an admin turns the requirement on only once they hold a factor"
http PUT /workspace/require-mfa -H "$(auth_header "$SARA_TOKEN")" -H 'Content-Type: application/json' -d '{"required":true}'
check_code M1.1 "PUT /workspace/require-mfa without a factor of your own" 409 MFA_ENABLE_YOURS_FIRST

enrol "$SARA_TOKEN" sara
check_status M1.2 "the admin turns two-factor on for themselves" 200

http PUT /workspace/require-mfa -H "$(auth_header "$SARA_TOKEN")" -H 'Content-Type: application/json' -d '{"required":true}'
check_status M1.3 "PUT /workspace/require-mfa" 200
check M1.4 "the workspace answers that it requires it" "true" "$(json '.requireMfa')"
check M1.5 "the change is audited as a settings section" "1" "$(await_sql "SELECT count(*) FROM app_lm_audit_event
  WHERE event_type = 'WORKSPACE_UPDATED' AND workspace_id = '$STRICT_ID' AND metadata ->> 'section' = 'requireMfa'" 1)"

# --- M2: a member without a factor is held at enrolment -----------------------------------------------------

section "M2  a member without a factor reads nothing of the workspace but the way to set one up"
OMAR_TOKEN=$(login_as "$OMAR" omar)
get /projects -H "$(auth_header "$OMAR_TOKEN")"
check_code M2.1 "GET /projects without a factor" 403 MFA_ENROLMENT_REQUIRED
get /auth/me -H "$(auth_header "$OMAR_TOKEN")"
check M2.2 "/auth/me says enrolment is owed" "true" "$(json '.twoFactorEnrolmentRequired')"
http POST /auth/extension/tokens -H "$(auth_header "$OMAR_TOKEN")" -b "$(jar omar)" -H "$(csrf_header omar)"
check_code M2.3 "pairing the extension while enrolment is owed" 403 MFA_ENROLMENT_REQUIRED

enrol "$OMAR_TOKEN" omar
check_status M2.4 "the member sets it up through /auth, which stays open" 200
get /projects -H "$(auth_header "$OMAR_TOKEN")"
check_status M2.5 "GET /projects once set up" 200

# --- M3: switching applies the target's requirement ---------------------------------------------------------

section "M3  switching into the strict workspace lands on enrolment; switching out of it does not"
ALOK_TOKEN=$(login_as "$ALOK" alok)
switch_body() { jq -nc --arg w "$1" '{workspaceId:$w}'; }
as_session POST /auth/switch-workspace "$ALOK_TOKEN" alok "$(switch_body "$ALOK_OWN_ID")"
check M3.1 "in his own firm nothing is owed" "false" "$(json '.user.twoFactorEnrolmentRequired')"
ALOK_TOKEN=$(json '.accessToken')
as_session POST /auth/switch-workspace "$ALOK_TOKEN" alok "$(switch_body "$STRICT_ID")"
check M3.2 "switched into the strict firm, enrolment is owed" "true" "$(json '.user.twoFactorEnrolmentRequired')"
ALOK_TOKEN=$(json '.accessToken')
get /projects -H "$(auth_header "$ALOK_TOKEN")"
check_code M3.3 "GET /projects in the strict firm" 403 MFA_ENROLMENT_REQUIRED

# --- M4: an admin resets a member's factor ------------------------------------------------------------------

section "M4  an admin resets a member's lost factor, never one in another workspace"
get /workspace/two-factor -H "$(auth_header "$SARA_TOKEN")"
check_status M4.1 "GET /workspace/two-factor as the admin" 200
OMAR_MEMBER=$(json ".members[] | select(.email == \"$OMAR\") | .memberId")
ALOK_MEMBER=$(json ".members[] | select(.email == \"$ALOK\") | .memberId")

http DELETE "/members/$OMAR_MEMBER/two-factor" -H "$(auth_header "$OMAR_TOKEN")"
check_code M4.2 "a member resetting a colleague" 403 FORBIDDEN

as_session POST /auth/mfa/setup "$ALOK_TOKEN" alok
as_session POST /auth/mfa/enable "$ALOK_TOKEN" alok \
  "$(jq -nc --arg p "$PASSWORD" --arg c "$(node "$TOTP" "$(json '.secret')")" '{currentPassword:$p, code:$c}')"
http DELETE "/members/$ALOK_MEMBER/two-factor" -H "$(auth_header "$SARA_TOKEN")"
check_code M4.3 "resetting a member who is also in another workspace" 409 MFA_RESET_OTHER_WORKSPACES

http DELETE "/members/$OMAR_MEMBER/two-factor" -H "$(auth_header "$SARA_TOKEN")"
check_status M4.4 "the admin resets Omar's factor" 204
http POST /auth/refresh -b "$(jar omar)" -H "$(csrf_header omar)"
check_status M4.5 "Omar's session ended with the reset" 401
OMAR_TOKEN=$(login_as "$OMAR" omar)
get /projects -H "$(auth_header "$OMAR_TOKEN")"
check_code M4.6 "signed in again, Omar is held at enrolment" 403 MFA_ENROLMENT_REQUIRED

summary
