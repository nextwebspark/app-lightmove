#!/usr/bin/env bash
# LightMove Capture: pairing the browser extension, and capturing through the session it yields.
#
# Deliberately narrow. The capture endpoint itself is main's and is covered thoroughly by
# TriageFlowIntegrationTest — re-asserting its validation here would be duplication. What no other
# suite exercises is the seam this branch adds: a token minted for the extension, rotated on its own
# route, and then used to write a company into a mandate. That is what this covers.
#
# Builds its own cast rather than sourcing cast.env, does nothing destructive, and needs no Apollo
# universe — an extension capture files a company by name and never names a universe id.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "15 — LightMove Capture: pairing the extension, and capturing through it"

# --- the cast: one admin, one client, one mandate ----------------------------

LEAD_EMAIL=$(new_email extlead)
post_json /auth/signup "$(jq -nc --arg e "$LEAD_EMAIL" --arg p "$PASSWORD" \
  '{fullName:"Cara Capture", email:$e, password:$p, termsAccepted:true}')" >/dev/null
post_json /auth/verify "$(jq -nc --arg t "$(token_for "$LEAD_EMAIL" verify)" '{token:$t}')" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$LEAD_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" >/dev/null
post_json /onboarding/workspace "$(jq -nc --arg n "Capture UAT $(date +%s)$RANDOM" \
  '{name:$n, companySize:"11-50 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$(json '.accessToken')")" >/dev/null
# A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
post_json /auth/login "$(jq -nc --arg e "$LEAD_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" >/dev/null
AUTH="$(auth_header "$(json '.accessToken')")"

post_json /clients '{"customName":"Capture Holding","customDomain":"captureholding.example"}' -H "$AUTH" >/dev/null
CLIENT_ID=$(json '.id')
post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Chief Operating Officer"}')" -H "$AUTH" >/dev/null
PROJECT=$(json '.id')
CAPTURE="/projects/$PROJECT/triage/capture"

# --- pairing -----------------------------------------------------------------
#
# The extension runs on a chrome-extension:// origin and cannot be given the refresh cookie, which is
# SameSite=Strict, host-only and path-scoped. It gets a refresh token of its own, in the body.

http POST /auth/extension/tokens -H "$AUTH"
check_status 15.1 "a signed-in user pairs the extension" 201
EXT_REFRESH=$(json '.refreshToken')
check 15.2 "the refresh token travels in the body, not a cookie" "true" \
  "$([ -n "$EXT_REFRESH" ] && [ "$EXT_REFRESH" != "null" ] && echo true || echo false)"
# An access token comes with it, so the popup can act at once rather than immediately spending its
# brand-new refresh token to get one.
check 15.3 "an access token comes with it" "true" \
  "$([ "$(json '.accessToken')" != "null" ] && echo true || echo false)"
check 15.4 "…and it names the account it was paired to" "$LEAD_EMAIL" "$(json '.user.email')"

# Minting a credential requires proving you hold the account; spending one does not, because the body
# token IS the credential — which is also why those two routes are CSRF-exempt.
http POST /auth/extension/tokens
check 15.5 "minting a token without a session mints nothing" "" \
  "$(json '.refreshToken // empty')"

post_json /auth/extension/refresh "$(jq -nc --arg t "$EXT_REFRESH" '{refreshToken:$t}')"
check_status 15.6 "the extension refreshes with no bearer and no CSRF token" 200
EXT_ROTATED=$(json '.refreshToken')
EXT_AUTH="$(auth_header "$(json '.accessToken')")"
check 15.7 "the refresh token rotates on use" "true" \
  "$([ "$EXT_ROTATED" != "$EXT_REFRESH" ] && echo true || echo false)"

# Replaying a spent token is what a thief with a copied one does, and it is read as exactly that.
post_json /auth/extension/refresh "$(jq -nc --arg t "$EXT_REFRESH" '{refreshToken:$t}')"
check_status 15.8 "replaying a spent token is refused" 401
post_json /auth/extension/refresh "$(jq -nc --arg t "$EXT_ROTATED" '{refreshToken:$t}')"
check_status 15.9 "…and the whole extension family goes with it" 401
# The browser session is untouched. Separate families is the point: a stolen extension token must not
# sign a consultant out of the app they are working in.
get /auth/me -H "$AUTH"
check_status 15.10 "…while the browser session is untouched" 200

# A web session's refresh token is not redeemable here. It exists only as an httpOnly SameSite=Strict
# cookie, and rotating it on this route would hand its successor back in a plaintext body.
post_json /auth/login "$(jq -nc --arg e "$LEAD_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')"
# Read off the response header rather than a cookie jar: `http` keeps no jar, and the value is what
# the browser would have stored.
WEB_REFRESH=$(header 'Set-Cookie' | sed -n 's/.*lm_refresh=\([^;]*\).*/\1/p' | head -1)
if [ -n "$WEB_REFRESH" ]; then
  post_json /auth/extension/refresh "$(jq -nc --arg t "$WEB_REFRESH" '{refreshToken:$t}')"
  check_status 15.11 "a browser session's token cannot be redeemed on the extension route" 401
else
  skip 15.11 "a browser session's token cannot be redeemed on the extension route" \
    "login set no lm_refresh cookie on this profile"
fi

# Pair again for the capture below.
http POST /auth/extension/tokens -H "$AUTH"
EXT_AUTH="$(auth_header "$(json '.accessToken')")"

# --- capturing through the paired session ------------------------------------
#
# The endpoint is main's and its rules are covered in TriageFlowIntegrationTest. What matters here is
# only that a paired extension session can reach it and is recorded as the extension.

CAPTURED_NAME="Desert Foods $(date +%s)$RANDOM"
capture_body() { # capture_body STATUS NAME
  jq -nc --arg s "$1" --arg n "$2" \
    '{source:"extension", status:$s, companyName:$n, website:"https://desertfoods.qa",
      companyCity:"Doha", companyCountry:"Qatar", numEmployees:140,
      note:"Met at Gulfood.", sourceUrl:"https://desertfoods.qa/about"}'
}

post_json "$CAPTURE" "$(capture_body inUniverse "$CAPTURED_NAME")" -H "$EXT_AUTH"
check_status 15.20 "a paired extension session can capture into the mandate" 201
check 15.21 "…and the row records the plugin as its provenance" "extension" "$(json '.source')"
check 15.22 "…landing at the stage the destination button named" "inUniverse" "$(json '.status')"
check 15.23 "…with no universe id, because the page is not the market" "" "$(json '.apolloAccountId // empty')"
check 15.24 "the consultant's note is kept" "Met at Gulfood." "$(json '.note')"

post_json "$CAPTURE" "$(capture_body shortlisted "Shortlisted $CAPTURED_NAME")" -H "$EXT_AUTH"
check 15.25 "the other destination button lands it shortlisted" "shortlisted" "$(json '.status')"

get "/projects/$PROJECT/triage?status=inUniverse" -H "$AUTH"
check 15.26 "and the mandate sees it on its own Companies screen" "1" "$(json '.totalCount')"

# --- the seat, not the login -------------------------------------------------
#
# A client representative holds WORK_VIEW without WORK_EXECUTE — the one role that proves capture is
# gated on the write action rather than on being able to see the mandate at all.

REP_EMAIL=$(new_email extrep)
post_json "/projects/$PROJECT/representatives/invitations" \
  "$(jq -nc --arg e "$REP_EMAIL" '{fullName:"Rita Rep", position:"Chair", email:$e}')" -H "$AUTH" >/dev/null
post_json /onboarding/accept-invitation-signup "$(jq -nc --arg t "$(token_for "$REP_EMAIL" accept-invite)" \
  --arg p "$PASSWORD" '{token:$t, fullName:"Rita Rep", password:$p}')" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$REP_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" >/dev/null
REP_AUTH="$(auth_header "$(json '.accessToken')")"

get "/projects/$PROJECT/triage" -H "$REP_AUTH"
check_status 15.30 "a client representative may read the mandate's companies" 200
post_json "$CAPTURE" "$(capture_body inUniverse "Rep Attempt $RANDOM")" -H "$REP_AUTH"
check_status 15.31 "…but may never capture into it" 403

# Pairing is about an account, not a seat, so a representative may pair too — what it buys them is
# exactly their seats, which is nothing they could not already do.
http POST /auth/extension/tokens -H "$REP_AUTH"
check_status 15.32 "pairing is about an account, not a seat" 201

summary
