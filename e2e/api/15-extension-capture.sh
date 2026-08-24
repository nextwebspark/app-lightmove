#!/usr/bin/env bash
# The browser extension: pairing a session, resolving a page against the Apollo universe, and writing
# the company into a mandate's triage.
#
# Like 14, this builds its own cast rather than sourcing cast.env, and nothing it does is destructive,
# so it is idempotent and can run anywhere in the matrix.
#
# Two halves, and they fail for different reasons, so they are kept apart:
#
#   - Pairing (15.1-15.12) needs nothing but an account. It runs everywhere, CI included.
#   - Capture (15.20+) splits again: a company the universe does NOT publish needs no universe at all
#     and always runs, while the cases that assert an Apollo *match* need the table populated and skip
#     themselves when it is empty — the same rule 14 follows, since the universe is pulled with gcloud
#     and a runner has no path to it.
#
# The property worth the most here is the fork: a captured page the universe publishes must produce a
# row indistinguishable from one the Strategy screen wrote, or the same company captured here and added
# there becomes two rows in one mandate.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "15 — LightMove Capture: pairing the extension and capturing a company"

# --- the cast: one admin, one client, one mandate ----------------------------

LEAD_EMAIL=$(new_email extlead)
post_json /auth/signup "$(jq -nc --arg e "$LEAD_EMAIL" --arg p "$PASSWORD" \
  '{fullName:"Cara Capture", email:$e, password:$p, termsAccepted:true}')" >/dev/null
post_json /auth/verify "$(jq -nc --arg t "$(token_for "$LEAD_EMAIL" verify)" '{token:$t}')" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$LEAD_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" >/dev/null
TOKEN=$(json '.accessToken')
post_json /onboarding/workspace "$(jq -nc --arg n "Capture UAT $(date +%s)$RANDOM" \
  '{name:$n, companySize:"11-50 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$TOKEN")" >/dev/null
# A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
post_json /auth/login "$(jq -nc --arg e "$LEAD_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" >/dev/null
TOKEN=$(json '.accessToken')
AUTH="$(auth_header "$TOKEN")"

post_json /clients '{"customName":"Capture Holding","customDomain":"captureholding.example"}' -H "$AUTH" >/dev/null
CLIENT_ID=$(json '.id')
post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Chief Operating Officer"}')" -H "$AUTH" >/dev/null
PROJECT=$(json '.id')
CAPTURES="/projects/$PROJECT/triage/captures"

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

# Minting a credential requires proving you hold the account. Spending one does not — the body token
# IS the credential, which is also why these two routes are CSRF-exempt: no cookie, nothing to forge.
http POST /auth/extension/tokens
check_status 15.5 "minting a token without a session is refused" 401

post_json /auth/extension/refresh "$(jq -nc --arg t "$EXT_REFRESH" '{refreshToken:$t}')"
check_status 15.6 "the extension refreshes with no bearer and no CSRF token" 200
EXT_ACCESS=$(json '.accessToken')
EXT_ROTATED=$(json '.refreshToken')
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

# Pair again for the capture cases below, and check the session is nameable in the sessions list.
http POST /auth/extension/tokens -H "$AUTH"
EXT_REFRESH=$(json '.refreshToken')
EXT_AUTH="$(auth_header "$(json '.accessToken')")"
get /auth/sessions -H "$AUTH"
check 15.11 "the extension is listed as its own device, so it can be revoked from the web" "1" \
  "$(json '[.[] | select(.deviceKind == "EXTENSION")] | length')"

# --- capturing a company the universe does not publish -----------------------
#
# The ordinary case, and the reason the extension exists: most of what a consultant browses in the GCC
# is not in the 71,822 rows. Runs with or without the universe loaded.

CAPTURED_DOMAIN="desertfoods-$(date +%s)$RANDOM.example"
capture_body() { # capture_body STATUS DOMAIN [NAME]
  jq -nc --arg s "$1" --arg d "$2" --arg n "${3:-Desert Foods LLC}" \
    '{status:$s, companyName:$n, website:("https://www." + $d + "/about"),
      companyCity:"Doha", companyCountry:"Qatar", numEmployees:140,
      tags:["Family owned","family owned "], note:"Met at Gulfood.",
      sourceUrl:("https://www." + $d + "/about")}'
}

post_json "$CAPTURES" "$(capture_body inUniverse "$CAPTURED_DOMAIN")" -H "$EXT_AUTH"
check_status 15.20 "a company the universe does not publish is captured anyway" 201
CAPTURED_ID=$(json '.id')
check 15.21 "…marked as captured, so the team knows the figures came off a page" "CAPTURE" "$(json '.origin')"
check 15.22 "…with no Apollo id, because the universe has none for it" "null" "$(json '.apolloAccountId')"
# The API de-duplicates tags case-insensitively, so the popup cannot produce two chips that come back
# as one row. Both spellings above are the same tag.
check 15.23 "tags are de-duplicated case-insensitively" "1" "$(json '.tags | length')"
check 15.24 "the consultant's note is kept" "Met at Gulfood." "$(json '.note')"
check 15.25 "it is keyed on the normalised domain, www and path stripped" "$CAPTURED_DOMAIN" \
  "$(sql "SELECT capture_key FROM app_lm_project_triage_company WHERE id = '$CAPTURED_ID'")"

# Same page again, from the other button. One row, promoted — a second click means what the first did.
post_json "$CAPTURES" "$(capture_body shortlisted "$CAPTURED_DOMAIN")" -H "$EXT_AUTH"
check_status 15.26 "capturing the same page again is not an error" 201
check 15.27 "…it is the same row" "$CAPTURED_ID" "$(json '.id')"
check 15.28 "…promoted to the shortlist" "shortlisted" "$(json '.status')"

# And never demoted. A capture is a coarse signal from a popup; letting it move a company back down
# would let a stray click undo a triage decision taken with the whole mandate in view.
post_json "$CAPTURES" "$(capture_body inUniverse "$CAPTURED_DOMAIN")" -H "$EXT_AUTH"
check 15.29 "…and never demoted back to the universe" "shortlisted" "$(json '.status')"

# Declined is a decision, not a stage to be overwritten. V32 keeps declined rows precisely so a later
# add cannot resurrect one, and a capture is a later add like any other.
http PATCH "/projects/$PROJECT/triage/$CAPTURED_ID" -H 'Content-Type: application/json' -H "$EXT_AUTH" \
  -d '{"status":"declined"}' >/dev/null
post_json "$CAPTURES" "$(capture_body inUniverse "$CAPTURED_DOMAIN")" -H "$EXT_AUTH"
check_code 15.30 "a company the mandate declined is refused, not quietly revived" 409 TRIAGE_COMPANY_DECLINED

# Guards.
post_json "$CAPTURES" "$(jq -nc '{status:"inUniverse", companyName:"Nameless Holding"}')" -H "$EXT_AUTH"
check_status 15.31 "a capture with no domain is refused — there is nothing to key the row on" 400

# A note somebody wrote on the triage screen survives the ordinary "capture it again to shortlist it"
# gesture, whose note box is empty. Omitting a field is not asking to erase it.
NOTED_DOMAIN="noted-$(date +%s)$RANDOM.example"
post_json "$CAPTURES" "$(capture_body inUniverse "$NOTED_DOMAIN")" -H "$EXT_AUTH" >/dev/null
NOTED_ID=$(json '.id')
http PATCH "/projects/$PROJECT/triage/$NOTED_ID" -H 'Content-Type: application/json' -H "$EXT_AUTH" \
  -d '{"note":"CFO retiring Q3."}' >/dev/null
post_json "$CAPTURES" "$(jq -nc --arg d "$NOTED_DOMAIN" \
  '{status:"shortlisted", companyName:"Desert Foods LLC", website:("https://www." + $d)}')" -H "$EXT_AUTH"
check 15.34 "a re-capture leaves an existing note alone" "CFO retiring Q3." "$(json '.note')"
post_json "$CAPTURES" "$(capture_body declined "$CAPTURED_DOMAIN")" -H "$EXT_AUTH"
check_status 15.32 "declined is not a destination a capture may name" 400
post_json "$CAPTURES" "$(capture_body inUniverse "$CAPTURED_DOMAIN")"
check_status 15.33 "capturing without a session is refused" 401

# --- resolving against the universe ------------------------------------------

UNIVERSE=$(sql "SELECT count(*) FROM app_lm_apollo_companies")
if [ "${UNIVERSE:-0}" -lt 100 ]; then
  # Skipped, not failed, exactly as 14 does: the universe is ETL-owned and pulled with gcloud, which CI
  # has no path to. Every case below reads it, so without it they would be vacuously true.
  skip 15.40 "the Apollo universe is loaded" \
    "app_lm_apollo_companies holds ${UNIVERSE:-0} rows — run \`npm run dev:db:apollo\` to run these cases"
  summary; exit 0
fi
note 15.40 "universe holds $UNIVERSE companies"

# A real row, read out of the table rather than hard-coded: the universe reloads wholesale, and a
# script naming a company would go red on the next pipeline load without anything being broken.
KNOWN=$(sql "SELECT apollo_account_id || '|' || company_name || '|' || website
             FROM app_lm_apollo_companies
             WHERE website IS NOT NULL AND website <> '' AND company_name IS NOT NULL
             ORDER BY num_employees DESC NULLS LAST LIMIT 1")
KNOWN_ID=${KNOWN%%|*}
KNOWN_REST=${KNOWN#*|}
KNOWN_NAME=${KNOWN_REST%%|*}
KNOWN_SITE=${KNOWN_REST#*|}

get "/companies/resolve?domain=$(printf '%s' "$KNOWN_SITE" | jq -sRr @uri)" -H "$EXT_AUTH"
check_status 15.41 "the resolve endpoint answers" 200
check 15.42 "a company's own domain resolves to its universe row" "$KNOWN_ID" "$(json '.company.apolloAccountId')"
check 15.43 "…and says so" "true" "$(json '.matched')"

# A miss is an answer, not an error: the capture goes ahead carrying the page's own fields.
get "/companies/resolve?domain=https://nowhere-$RANDOM.invalid" -H "$EXT_AUTH"
check_status 15.44 "a company the universe does not publish still answers 200" 200
check 15.45 "…saying plainly that it did not match" "false" "$(json '.matched')"

# The whole point of resolving. A captured page the universe publishes must produce a row that is
# indistinguishable from one "Add to Universe" wrote — snapshot from Apollo, not from the page.
post_json "$CAPTURES" "$(jq -nc --arg w "$KNOWN_SITE" \
  '{status:"inUniverse", companyName:"Whatever The Page Said", website:$w,
    companyCity:"Nowhere", numEmployees:9, sourceUrl:$w}')" -H "$EXT_AUTH"
check_status 15.46 "a captured page the universe publishes is captured" 201
check 15.47 "…filed under its Apollo identity" "$KNOWN_ID" "$(json '.apolloAccountId')"
check 15.48 "…indistinguishable from a row Strategy wrote" "STRATEGY" "$(json '.origin')"
# The page's own claims are discarded. This is the invariant that a client cannot file a known company
# under a name of its own choosing, and it has to hold on the capture path too.
check 15.49 "…carrying Apollo's name, not the page's" "$KNOWN_NAME" "$(json '.companyName')"
check 15.50 "…and Apollo's headcount, not the page's 9" \
  "$(sql "SELECT coalesce(num_employees::text,'null') FROM app_lm_apollo_companies WHERE apollo_account_id = '$KNOWN_ID'")" \
  "$(json '.numEmployees // "null"')"

# Off-limits is keyed to Apollo ids, so it can only speak to a company the universe publishes — and
# there it must hold however the page describes it.
http PUT "/projects/$PROJECT/strategy/off-limits" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(jq -nc --arg a "$KNOWN_ID" '{apolloAccountIds:[$a]}')" >/dev/null
BARRED=$(sql "SELECT apollo_account_id || '|' || website FROM app_lm_apollo_companies
              WHERE website IS NOT NULL AND website <> '' AND apollo_account_id <> '$KNOWN_ID'
              ORDER BY num_employees DESC NULLS LAST LIMIT 1")
BARRED_ID=${BARRED%%|*}
BARRED_SITE=${BARRED#*|}
http PUT "/projects/$PROJECT/strategy/off-limits" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(jq -nc --arg a "$BARRED_ID" '{apolloAccountIds:[$a]}')" >/dev/null
post_json "$CAPTURES" "$(jq -nc --arg w "$BARRED_SITE" \
  '{status:"inUniverse", companyName:"Barred By Another Name", website:$w, sourceUrl:$w}')" -H "$EXT_AUTH"
check_status 15.51 "an off-limits company cannot be captured, however the page describes it" 400

# The hole this closes: an unresolvable id used to short-circuit the web-identity lookup entirely, so
# a barred company was filed as a capture under whatever name the request chose.
post_json "$CAPTURES" "$(jq -nc --arg w "$BARRED_SITE" \
  '{status:"inUniverse", apolloAccountId:"no-longer-published",
    companyName:"Barred By Another Name", website:$w, sourceUrl:$w}')" -H "$EXT_AUTH"
check_status 15.55 "…and a stale Apollo id is not a way round the bar" 400

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
check_status 15.52 "a client representative may read the mandate's triage" 200
post_json "$CAPTURES" "$(capture_body inUniverse "rep-attempt-$RANDOM.example")" -H "$REP_AUTH"
check_status 15.53 "…but may never capture into it" 403

# A representative may still pair the extension — pairing is about an account, not a mandate. What it
# buys them is exactly their seats, which is nothing they could not already do.
http POST /auth/extension/tokens -H "$REP_AUTH"
check_status 15.54 "pairing is about an account, not a seat, so a representative may pair too" 201

summary
