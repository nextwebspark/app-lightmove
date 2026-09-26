#!/usr/bin/env bash
# Strategy's other half: the Companies grid. A mandate's triaged companies (typed in, taken from the
# market one or many at a time, moved between stages, removed), the executives mapped at them, each
# executive's contact ledger, and the custom columns a mandate adds to its own grid.
#
# No paid or AI route is touched: no contact lookup, no AI enrichment. The one thing the e2e stack
# lacks is the Apollo universe, so the market-backed happy paths skip themselves when it is empty and
# only the refusals that need no universe row are asserted there.
#
# Sources cast.env for the people and the client, but builds its own mandate so its seats are known:
# MEMBER leads it, MEMBER2 researches on it, the fixture representative is its client seat.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
. "$RUN_DIR/cast.env"

login_as() {
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
  json '.accessToken'
}
member_id_of() {
  sql "SELECT m.id FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
       WHERE u.email = '$1' AND m.status = 'ACTIVE'"
}

# Tokens in cast.env are as old as the fixtures run, and an access token lives fifteen minutes.
LEAD_AUTH="$(auth_header "$(login_as "$MEMBER_EMAIL")")"
RESEARCHER_AUTH="$(auth_header "$(login_as "$MEMBER2_EMAIL")")"
CLIENT_AUTH="$(auth_header "$(login_as "$CLIENT_EMAIL")")"
OUTSIDER_AUTH="$(auth_header "$(login_as "$OUTSIDER_EMAIL")")"

put_json()   { local path="$1" data="$2"; shift 2; http PUT "$path" -H 'Content-Type: application/json' -d "$data" "$@"; }
patch_json() { local path="$1" data="$2"; shift 2; http PATCH "$path" -H 'Content-Type: application/json' -d "$data" "$@"; }

RUN_TAG="$(date +%s)$RANDOM"

section "N17.0  the mandate"

post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Chief Operating Officer"}')" \
  -H "$LEAD_AUTH"
check_status N17.0.1 "the lead creates a mandate" 201
P=$(json '.id')
TRIAGE="/projects/$P/triage"
PEOPLE="/projects/$P/candidates"
COLUMNS="/projects/$P/custom-columns"

http PUT "/projects/$P/members/$(member_id_of "$MEMBER2_EMAIL")" -H 'Content-Type: application/json' \
  -d '{"role":"RESEARCHER"}' -H "$LEAD_AUTH"
check_status N17.0.2 "and seats MEMBER2 as a researcher" 200
post_json "/projects/$P/representatives" "$(jq -nc --arg r "$REPRESENTATIVE_ID" '{representativeId:$r}')" \
  -H "$LEAD_AUTH"
check_status N17.0.3 "and gives the client representative a seat" 200

section "N17.1  a company typed in by hand"

capture() { # capture AUTH NAME [EXTRA_JQ]
  post_json "$TRIAGE/capture" "$(jq -nc --arg n "$2" "{companyName:\$n, source:\"manual\", industry:\"Oil & Energy\",
    companyCountry:\"Qatar\", companyCity:\"Doha\", numEmployees:1200, website:\"https://example.com\"} | ${3:-.}")" \
    -H "$1"
}

ALPHA="Zephyr Energy Holding $RUN_TAG"
capture "$LEAD_AUTH" "$ALPHA"
check_status N17.1.1 "the lead types a company in" 201
ALPHA_ID=$(json '.id')
check N17.1.2 "it is filed as a hand-typed row, with no universe id" "manual|" "$(json '.source')|$(json '.apolloAccountId // empty')"
check N17.1.3 "it lands in universe by default" "inUniverse" "$(json '.status')"
check N17.1.4 "the typed facts are kept" "Doha|Qatar|1200" \
  "$(json '.companyCity')|$(json '.companyCountry')|$(json '.numEmployees')"

capture "$LEAD_AUTH" "$(printf '%s' "$ALPHA" | tr 'a-z' 'A-Z')"
check_code N17.1.5 "the same name again, case aside" 409 TRIAGE_COMPANY_ALREADY_HELD
capture "$LEAD_AUTH" "Market Claim $RUN_TAG" '.source = "strategy"'
check_code N17.1.6 "a hand-typed row cannot claim to come from the market" 400 VALIDATION_FAILED
capture "$LEAD_AUTH" "Bad Stage $RUN_TAG" '.status = "archived"'
check_code N17.1.7 "an unknown landing stage" 400 VALIDATION_FAILED
capture "$LEAD_AUTH" "   "
check_code N17.1.8 "a company with no name" 400 VALIDATION_FAILED

BETA="Sahara Logistics $RUN_TAG"
capture "$RESEARCHER_AUTH" "$BETA" '.status = "shortlisted"'
check_status N17.1.9 "a researcher may type one in too — WORK_EXECUTE" 201
BETA_ID=$(json '.id')
check N17.1.10 "…at the stage they named" "shortlisted" "$(json '.status')"

put_json "$TRIAGE/$ALPHA_ID" "$(jq -nc --arg n "$ALPHA Group" '{companyName:$n, companyCountry:"Qatar"}')" -H "$LEAD_AUTH"
check_status N17.1.11 "a hand-typed company's facts can be edited" 200
check N17.1.12 "…and an omitted field is a cleared one" "$ALPHA Group|" "$(json '.companyName')|$(json '.companyCity // empty')"
put_json "$TRIAGE/$ALPHA_ID" "$(jq -nc --arg n "$BETA" '{companyName:$n}')" -H "$LEAD_AUTH"
check_code N17.1.13 "renaming onto a name the mandate already holds" 409 TRIAGE_COMPANY_ALREADY_HELD
ALPHA="$ALPHA Group"

section "N17.2  taking companies from the market"

post_json "$TRIAGE" '{"apolloAccountId":"e2e-no-such-account"}' -H "$LEAD_AUTH"
check_code N17.2.1 "one company by an id the universe does not carry" 400 VALIDATION_FAILED
post_json "$TRIAGE" '{"note":"no id"}' -H "$LEAD_AUTH"
check_code N17.2.2 "one company with no id at all" 400 VALIDATION_FAILED

post_json "$TRIAGE/bulk" '{"apolloAccountIds":["e2e-ghost-1","e2e-ghost-2","e2e-ghost-1"],"status":"declined"}' -H "$LEAD_AUTH"
check_status N17.2.3 "a selection of ids the universe does not carry is answered, not refused" 200
check N17.2.4 "…nothing added, every distinct id skipped" "0/2" "$(json '.added')/$(json '.skipped')"
post_json "$TRIAGE/bulk" '{"apolloAccountIds":["e2e-ghost-1"],"status":"archived"}' -H "$LEAD_AUTH"
check_code N17.2.5 "a selection filed at an unknown stage" 400 VALIDATION_FAILED
post_json "$TRIAGE/bulk" '{"apolloAccountIds":[]}' -H "$LEAD_AUTH"
check_code N17.2.6 "an empty selection" 400 VALIDATION_FAILED

MARKET_IDS=$(sql "SELECT apollo_account_id FROM app_lm_apollo_companies ORDER BY apollo_account_id LIMIT 4")
if [ "$(printf '%s\n' "$MARKET_IDS" | grep -c .)" -lt 4 ]; then
  for id in N17.2.7 N17.2.8 N17.2.9 N17.2.10 N17.2.11 N17.2.12; do
    skip "$id" "bulk and single add from the market" \
      "app_lm_apollo_companies holds fewer than 4 rows — run \`npm run dev:db:apollo\` to run these cases"
  done
else
  M1=$(printf '%s\n' "$MARKET_IDS" | sed -n 1p); M2=$(printf '%s\n' "$MARKET_IDS" | sed -n 2p)
  M3=$(printf '%s\n' "$MARKET_IDS" | sed -n 3p); M4=$(printf '%s\n' "$MARKET_IDS" | sed -n 4p)
  bulk_at() { post_json "$TRIAGE/bulk" "$(jq -nc --arg a "$1" --arg s "$2" '{apolloAccountIds:[$a], status:$s}')" -H "$LEAD_AUTH"; }

  bulk_at "$M1" inUniverse;  R1="$(json '.added')"
  bulk_at "$M2" shortlisted; R2="$(json '.added')"
  bulk_at "$M3" declined;    R3="$(json '.added')"
  check N17.2.7 "one bulk request per stage files each company where it was sent" "1 1 1" "$R1 $R2 $R3"
  get "$TRIAGE?status=declined" -H "$LEAD_AUTH"
  check N17.2.8 "the declined one is on the declined page, marked as the market's" "strategy|$M3" \
    "$(json ".companies[] | select(.apolloAccountId == \"$M3\") | .source")|$(json ".companies[] | select(.apolloAccountId == \"$M3\") | .apolloAccountId")"
  bulk_at "$M3" inUniverse
  check N17.2.9 "re-adding a declined company does not resurrect it" "0/1" "$(json '.added')/$(json '.skipped')"

  post_json "$TRIAGE" "$(jq -nc --arg a "$M4" '{apolloAccountId:$a, status:"shortlisted", note:"From the market"}')" -H "$LEAD_AUTH"
  check_status N17.2.10 "one market company taken in by id" 201
  MARKET_ROW=$(json '.id')
  post_json "$TRIAGE" "$(jq -nc --arg a "$M4" '{apolloAccountId:$a, status:"declined"}')" -H "$LEAD_AUTH"
  check N17.2.11 "a second click answers the held row, stage untouched" "$MARKET_ROW shortlisted" "$(json '.id') $(json '.status')"
  put_json "$TRIAGE/$MARKET_ROW" '{"companyName":"Renamed Market Co"}' -H "$LEAD_AUTH"
  check_code N17.2.12 "a market company's facts are not the mandate's to edit" 409 TRIAGE_COMPANY_NOT_EDITABLE
fi

section "N17.3  moving between stages"

patch_json "$TRIAGE/$ALPHA_ID" '{"note":"Strong finance bench"}' -H "$LEAD_AUTH"
check_status N17.3.1 "a note is added" 200
patch_json "$TRIAGE/$ALPHA_ID" '{"status":"shortlisted"}' -H "$LEAD_AUTH"
check N17.3.2 "the company moves to shortlisted" "shortlisted" "$(json '.status')"
check N17.3.3 "…and the note stays — a null leaves it be" "Strong finance bench" "$(json '.note')"
patch_json "$TRIAGE/$ALPHA_ID" '{"noExecutiveFound":true}' -H "$LEAD_AUTH"
check N17.3.4 "flagging no executive found leaves the stage" "true shortlisted" "$(json '.noExecutiveFound') $(json '.status')"
patch_json "$TRIAGE/$ALPHA_ID" '{"note":""}' -H "$LEAD_AUTH"
check N17.3.5 "an empty note is how a note is cleared" "" "$(json '.note // empty')"
patch_json "$TRIAGE/$ALPHA_ID" '{"status":"archived"}' -H "$LEAD_AUTH"
check_code N17.3.6 "an unknown stage" 400 VALIDATION_FAILED
patch_json "$TRIAGE/$(new_uuid)" '{"status":"declined"}' -H "$LEAD_AUTH"
check_code N17.3.7 "a company this mandate does not hold" 404 NOT_FOUND

get "$TRIAGE?status=shortlisted" -H "$LEAD_AUTH"
check_status N17.3.8 "the shortlisted page reads" 200
check N17.3.9 "…holding both hand-typed companies" "2" \
  "$(json "[.companies[] | select(.id == \"$ALPHA_ID\" or .id == \"$BETA_ID\")] | length")"
check N17.3.10 "…and its badge counts the page's stage" "true" "$(json '.counts.shortlisted == .totalCount')"
get "$TRIAGE?status=inUniverse" -H "$LEAD_AUTH"
check N17.3.11 "the in-universe page no longer lists them" "0" \
  "$(json "[.companies[] | select(.id == \"$ALPHA_ID\" or .id == \"$BETA_ID\")] | length")"
get "$TRIAGE?status=everything" -H "$LEAD_AUTH"
check_code N17.3.12 "listing an unknown stage" 400 VALIDATION_FAILED
get "$TRIAGE?sort=vibes" -H "$LEAD_AUTH"
check_code N17.3.13 "sorting by an unknown field" 400 VALIDATION_FAILED

section "N17.4  executives"

person() { # person NAME [EXTRA_JQ] -> a SaveCandidateRequest body
  jq -nc --arg n "$1" "{fullName:\$n, title:\"Chief Financial Officer\", seniority:\"C-Suite\",
    locationCity:\"Doha\", locationCountry:\"Qatar\"} | ${2:-.}"
}

post_json "$PEOPLE" "$(person "Layla Haddad $RUN_TAG" ".triageCompanyId = \"$ALPHA_ID\" | .gender = \"other\" | .nationality = \"Egyptian\"")" \
  -H "$LEAD_AUTH"
check_status N17.4.1 "the lead maps an executive at a company" 201
LAYLA=$(json '.id')
check N17.4.2 "she carries the company's name as a snapshot" "$ALPHA_ID|$ALPHA" "$(json '.triageCompanyId')|$(json '.companyName')"
check N17.4.3 "a status nobody set starts as identified" "identified" "$(json '.status')"
check N17.4.4 "a typed profile is manual" "manual" "$(json '.source')"
check N17.4.5 "seniority speaks the candidate wire token" "C-Suite" "$(json '.seniority')"
check N17.4.6 "gender recorded as other reads back as other" "other" "$(json '.gender')"
check N17.4.7 "nationality is stored as typed, never folded into its group on write" "Egyptian" "$(json '.nationality')"

post_json "$PEOPLE" "$(person "Omar Nasser $RUN_TAG")" -H "$LEAD_AUTH"
check_status N17.4.8 "an executive with nobody's view of their gender" 201
OMAR=$(json '.id')
check N17.4.9 "reads back with none" "null" "$(json '.gender')"
check N17.4.10 "…and the rows keep not-recorded apart from other" "OTHER|" \
  "$(sql "SELECT coalesce(gender, '') FROM app_lm_project_candidate WHERE id = '$LAYLA'")|$(sql "SELECT coalesce(gender, '') FROM app_lm_project_candidate WHERE id = '$OMAR'")"
check N17.4.11 "an unmapped executive has no company row" "" "$(json '.triageCompanyId // empty')"

post_json "$PEOPLE" "$(person "Omar Nasser $RUN_TAG")" -H "$LEAD_AUTH"
check_code N17.4.12 "the same name unmapped twice" 409 CANDIDATE_ALREADY_MAPPED
post_json "$PEOPLE" "$(person "Omar Nasser $RUN_TAG" ".triageCompanyId = \"$BETA_ID\"")" -H "$LEAD_AUTH"
check_status N17.4.13 "the same name at a company is a different scope" 201
post_json "$PEOPLE" "$(person "Nadia Q $RUN_TAG" '.gender = "unknown"')" -H "$LEAD_AUTH"
check_code N17.4.14 "an unknown gender" 400 VALIDATION_FAILED
post_json "$PEOPLE" "$(person "Nadia Q $RUN_TAG" '.seniority = "C_SUITE"')" -H "$LEAD_AUTH"
check_code N17.4.15 "the enum name is not the candidate wire token" 400 VALIDATION_FAILED
post_json "$PEOPLE" "$(person "Nadia Q $RUN_TAG" ".triageCompanyId = \"$(new_uuid)\"")" -H "$LEAD_AUTH"
check_code N17.4.16 "a company this mandate does not hold" 404 NOT_FOUND

get "$PEOPLE/$LAYLA" -H "$LEAD_AUTH"
check_status N17.4.17 "one executive reads whole" 200
get "$PEOPLE?triageCompanyId=$ALPHA_ID" -H "$LEAD_AUTH"
check N17.4.18 "the grid reads the people at a page of companies" "$LAYLA" "$(json '[.candidates[].id] | join(",")')"
get "$PEOPLE?unmapped=true" -H "$LEAD_AUTH"
check N17.4.19 "…and the unmapped ones apart" "1" "$(json "[.candidates[] | select(.id == \"$OMAR\")] | length")"

# A notice period is one of five on the drawer, but the column is not narrowed to them: an import's
# "negotiable" must stay offered as recorded rather than being cleared, so the server stores free text.
put_json "$PEOPLE/$LAYLA" "$(person "Layla Haddad $RUN_TAG" ".triageCompanyId = \"$ALPHA_ID\" | .gender = \"female\" |
  .nationality = \"Qatari\" | .compensation = {currency:\"QAR\", baseSalary:1500000, noticePeriod:\"negotiable\"}")" \
  -H "$LEAD_AUTH"
check_status N17.4.20 "the drawer replaces a profile" 200
check N17.4.21 "gender and nationality are rewritten" "female|Qatari" "$(json '.gender')|$(json '.nationality')"
check N17.4.22 "a notice period outside the five options is kept as recorded" "negotiable" "$(json '.compensation.noticePeriod')"
put_json "$PEOPLE/$LAYLA" "$(person "Layla Haddad $RUN_TAG" ".triageCompanyId = \"$ALPHA_ID\"")" -H "$LEAD_AUTH"
check N17.4.23 "a PUT is the whole form: an omitted gender is a cleared one" "null" "$(json '.gender')"

patch_json "$PEOPLE/$LAYLA" '{"status":"contacted"}' -H "$LEAD_AUTH"
check_status N17.4.24 "the status pill moves on its own" 200
check N17.4.25 "…to contacted, and nothing else changes" "contacted|$ALPHA" "$(json '.status')|$(json '.companyName')"
patch_json "$PEOPLE/$LAYLA" '{"status":"hired"}' -H "$LEAD_AUTH"
check_code N17.4.26 "a status that is not on the line" 400 VALIDATION_FAILED
patch_json "$PEOPLE/$LAYLA" '{"status":"engaged"}' -H "$RESEARCHER_AUTH"
check N17.4.27 "a researcher moves it too — WORK_EXECUTE" "200 engaged" "$LAST_STATUS $(json '.status')"

section "N17.5  the contact ledger"

post_json "$PEOPLE" "$(person "Rania Saleh $RUN_TAG" '.emails = [{value:"rania@example.com", kind:"work"}] |
  .phones = [{value:"+971 50 123 4567"}]')" -H "$LEAD_AUTH"
check_status N17.5.1 "an executive is added with an email and a phone" 201
RANIA=$(json '.id')
CONTACTS="$PEOPLE/$RANIA/contacts"
check N17.5.2 "both land in the ledger as typed" "1 1 manual work" \
  "$(json '.contacts.emails | length') $(json '.contacts.phones | length') $(json '.contacts.emails[0].source') $(json '.contacts.emails[0].kind')"

put_json "$CONTACTS" '{"emails":[{"value":"rania@example.com","kind":"personal","verified":true},{"value":"r.saleh@example.org"}],
                       "phones":[{"value":"+971501234567"}]}' -H "$LEAD_AUTH"
check_status N17.5.3 "the Contact section saves both channels" 200
check N17.5.4 "two emails now" "2" "$(json '.contacts.emails | length')"
check N17.5.5 "the held address took the new kind and the researcher's verification" "personal|true|Verified by researcher" \
  "$(json '.contacts.emails[] | select(.address == "rania@example.com") | "\(.kind)|\(.verified)|\(.status)"')"
check N17.5.6 "the same number spelt without spaces is the same row, respelt" "1 +971501234567" \
  "$(json '.contacts.phones | length') $(json '.contacts.phones[0].number')"
check N17.5.7 "…one row in the ledger, keyed on its digits" "1|971501234567" \
  "$(sql "SELECT count(*) || '|' || max(value_key) FROM app_lm_candidate_contact WHERE candidate_id = '$RANIA' AND upper(channel) = 'PHONE'")"

put_json "$CONTACTS" '{"emails":[{"value":"r.saleh@example.org"}],"phones":[{"value":"+971501234567"}]}' -H "$LEAD_AUTH"
check N17.5.8 "an address left out of the list is removed" "r.saleh@example.org" "$(json '[.contacts.emails[].address] | join(",")')"

put_json "$CONTACTS" '{"emails":[],"phones":[{"value":"+971 50 123 4567"},{"value":"+971-50-123-4567"}]}' -H "$LEAD_AUTH"
check_code N17.5.9 "one number listed twice in two spellings is refused, not silently merged" 400 VALIDATION_FAILED
put_json "$CONTACTS" '{"emails":[{"value":"not-an-address"}],"phones":[]}' -H "$LEAD_AUTH"
check_code N17.5.10 "an address that is not one" 400 VALIDATION_FAILED

TEN_EMAILS=$(jq -nc '[range(1; 11) | {value: "exec\(.)@example.com"}]')
ELEVEN_EMAILS=$(jq -nc '[range(1; 12) | {value: "exec\(.)@example.com"}]')
put_json "$CONTACTS" "$(jq -nc --argjson e "$ELEVEN_EMAILS" '{emails:$e, phones:[]}')" -H "$LEAD_AUTH"
check_code N17.5.11 "an eleventh email in one list is refused" 400 VALIDATION_FAILED
put_json "$CONTACTS" "$(jq -nc --argjson e "$TEN_EMAILS" '{emails:$e, phones:[{value:"+971501234567"}]}')" -H "$LEAD_AUTH"
check_status N17.5.12 "ten is the ceiling, and ten is accepted" 200
check N17.5.13 "…all ten held" "10" "$(json '.contacts.emails | length')"

RANIA_PROFILE="$(person "Rania Saleh $RUN_TAG")"
put_json "$PEOPLE/$RANIA" "$(printf '%s' "$RANIA_PROFILE" | jq -c '.email = "eleventh@example.com"')" -H "$LEAD_AUTH"
check_code N17.5.14 "a profile write that would take a channel to eleven" 409 CONTACT_LIMIT_REACHED
put_json "$PEOPLE/$RANIA" "$(printf '%s' "$RANIA_PROFILE" | jq -c '.email = "EXEC3@example.com" | .phone = "+971 (50) 123-4567"')" -H "$LEAD_AUTH"
check_status N17.5.15 "a profile write naming values already held is accepted at the ceiling" 200
check N17.5.16 "…and adds no row for either" "10 1" "$(json '.contacts.emails | length') $(json '.contacts.phones | length')"

put_json "$CONTACTS" '{"emails":[{"value":"kept@example.com"}],"phones":[{"value":"+971501234567"}]}' -H "$LEAD_AUTH"
put_json "$PEOPLE/$RANIA" "$(printf '%s' "$RANIA_PROFILE" | jq -c '.email = "added@example.com"')" -H "$LEAD_AUTH"
check_status N17.5.17 "a profile write supplying a new email" 200
check N17.5.18 "…adds it and removes nothing — the Contact section is the only way out" "added@example.com,kept@example.com" \
  "$(json '[.contacts.emails[].address] | sort | join(",")')"
check N17.5.19 "…and a profile write naming no phone leaves the phone alone" "1" "$(json '.contacts.phones | length')"

post_json "$PEOPLE" "$(person "Too Many $RUN_TAG" ".emails = $ELEVEN_EMAILS")" -H "$LEAD_AUTH"
check_code N17.5.20 "adding an executive with eleven emails" 400 VALIDATION_FAILED

section "N17.6  a profile the plugin captured"

SLUG="e2e-captured-$RUN_TAG"
CAPTURED_URL="https://www.linkedin.com/in/$SLUG"
post_json "$PEOPLE" "$(person "Karim Aziz $RUN_TAG" ".source = \"extension\" | .linkedinUrl = \"$CAPTURED_URL\" |
  .sourceUrl = \"$CAPTURED_URL\" | .email = \"karim@example.com\"")" -H "$LEAD_AUTH"
check_status N17.6.1 "an executive arrives through the plugin's door" 201
KARIM=$(json '.id')
check N17.6.2 "…recorded as the extension's" "extension|extension" "$(json '.source')|$(json '.contacts.emails[0].source')"
KARIM_PROFILE="$(person "Karim Aziz $RUN_TAG" ".source = \"extension\" | .linkedinUrl = \"$CAPTURED_URL\"")"

put_json "$PEOPLE/$KARIM" "$(printf '%s' "$KARIM_PROFILE" | jq -c '.linkedinUrl = "https://www.linkedin.com/in/someone-else"')" -H "$LEAD_AUTH"
check_code N17.6.3 "its LinkedIn URL cannot be retyped" 409 CANDIDATE_PROFILE_URL_LOCKED
put_json "$PEOPLE/$KARIM" "$(printf '%s' "$KARIM_PROFILE" | jq -c 'del(.linkedinUrl)')" -H "$LEAD_AUTH"
check_code N17.6.4 "…nor cleared" 409 CANDIDATE_PROFILE_URL_LOCKED
put_json "$PEOPLE/$KARIM" "$(printf '%s' "$KARIM_PROFILE" | jq -c '.title = "Group CFO"')" -H "$LEAD_AUTH"
check_status N17.6.5 "every other field stays editable" 200
check N17.6.6 "…and the URL is what the plugin read" "$CAPTURED_URL" "$(json '.linkedinUrl')"

put_json "$PEOPLE/$KARIM/contacts" '{"emails":[{"value":"karim@example.com","kind":"work"}],"phones":[]}' -H "$LEAD_AUTH"
check N17.6.7 "a captured address re-saved unchanged keeps the plugin's door" "extension|work" \
  "$(json '.contacts.emails[0].source')|$(json '.contacts.emails[0].kind')"

post_json "$PEOPLE" "$(person "Karim A. $RUN_TAG" ".linkedinUrl = \"https://www.linkedin.com/in/$(printf '%s' "$SLUG" | tr 'a-z' 'A-Z')\"")" -H "$LEAD_AUTH"
check_code N17.6.8 "the same profile mapped twice, whatever the name or the slug's case" 409 CANDIDATE_ALREADY_MAPPED

put_json "$PEOPLE/$OMAR" "$(person "Omar Nasser $RUN_TAG" '.linkedinUrl = "https://www.linkedin.com/in/omar-typed"')" -H "$LEAD_AUTH"
check_status N17.6.9 "a hand-typed profile's URL is the researcher's to set" 200
put_json "$PEOPLE/$OMAR" "$(person "Omar Nasser $RUN_TAG" '.linkedinUrl = "https://www.linkedin.com/in/omar-corrected"')" -H "$LEAD_AUTH"
check N17.6.10 "…and to fix" "https://www.linkedin.com/in/omar-corrected" "$(json '.linkedinUrl')"

section "N17.7  custom columns"

define() { post_json "$COLUMNS" "$1" -H "$LEAD_AUTH"; }

define '{"target":"company","label":"Board Seat"}'
check_status N17.7.1 "a company column is defined" 201
COL_BOARD=$(json '.id')
check N17.7.2 "its key is slugged from the label, and it holds text by default" "boardSeat|text|0" \
  "$(json '.fieldKey')|$(json '.dataType')|$(json '.displayOrder')"
define '{"target":"company","label":"Target Headcount","dataType":"number"}'
COL_HEAD=$(json '.id')
check N17.7.3 "a second goes after it" "targetHeadcount|number|1" \
  "$(json '.fieldKey')|$(json '.dataType')|$(json '.displayOrder')"
define '{"target":"company","label":"Listed","dataType":"boolean"}'
COL_LISTED=$(json '.id')
define '{"target":"candidate","label":"Board Member","dataType":"boolean"}'
check_status N17.7.4 "a candidate column is defined beside them" 201

define '{"target":"company","label":"board seat"}'
check_code N17.7.5 "a label already used on that grid, case aside" 409 CUSTOM_COLUMN_NAME_TAKEN
define '{"target":"vendor","label":"Anything"}'
check_code N17.7.6 "an unknown target" 400 VALIDATION_FAILED
define '{"target":"company","label":"Anything","dataType":"currency"}'
check_code N17.7.7 "an unknown data type" 400 VALIDATION_FAILED

patch_json "$COLUMNS/$COL_BOARD" '{"label":"Board Seats"}' -H "$LEAD_AUTH"
check_status N17.7.8 "a column is renamed" 200
check N17.7.9 "…the header changes, the key it stores under does not" "Board Seats|boardSeat" "$(json '.label')|$(json '.fieldKey')"
patch_json "$COLUMNS/$COL_BOARD" '{"label":"Board Seats"}' -H "$LEAD_AUTH"
check_status N17.7.10 "re-saving under its own name is not a clash" 200
patch_json "$COLUMNS/$COL_BOARD" '{"label":"Listed"}' -H "$LEAD_AUTH"
check_code N17.7.11 "renaming onto a sibling's label" 409 CUSTOM_COLUMN_NAME_TAKEN

put_json "$COLUMNS/order" "$(jq -nc --arg a "$COL_LISTED" --arg b "$COL_HEAD" --arg c "$COL_BOARD" '{columnIds:[$a,$b,$c]}')" -H "$LEAD_AUTH"
check_status N17.7.12 "the company grid is reordered" 200
check N17.7.13 "…into exactly that order" "Listed,Target Headcount,Board Seats" \
  "$(json '[.columns[] | select(.target == "company")] | sort_by(.displayOrder) | map(.label) | join(",")')"
put_json "$COLUMNS/order" "$(jq -nc --arg a "$COL_LISTED" --arg b "$COL_HEAD" '{columnIds:[$a,$b]}')" -H "$LEAD_AUTH"
check_code N17.7.14 "a reorder leaving a column out" 400 VALIDATION_FAILED
put_json "$COLUMNS/order" "$(jq -nc --arg a "$COL_LISTED" --arg b "$COL_HEAD" --arg c "$COL_BOARD" '{columnIds:[$a,$b,$c,$c]}')" -H "$LEAD_AUTH"
check_code N17.7.15 "a reorder naming a column twice" 400 VALIDATION_FAILED

FIELDS="$TRIAGE/$ALPHA_ID/custom-fields"
patch_json "$FIELDS" '{"customFields":{"boardSeat":"Chair","targetHeadcount":"1,200","listed":"yes","ghostKey":"smuggled"}}' -H "$LEAD_AUTH"
check_status N17.7.16 "a company's custom cells are written" 200
check N17.7.17 "a defined key is stored" "Chair" "$(json '.customFields.boardSeat')"
check N17.7.18 "a number loses its grouping, a yes/no is canonical" "1200|true" \
  "$(json '.customFields.targetHeadcount')|$(json '.customFields.listed')"
# The bag is open, and applyTo is the only thing between it and caller-chosen keys: an undefined key is
# dropped, not refused, so an older client naming a deleted column still saves the rest.
check N17.7.19 "an undefined key is dropped, not stored" "false" "$(json '.customFields | has("ghostKey")')"
patch_json "$FIELDS" '{"customFields":{"targetHeadcount":"about forty"}}' -H "$LEAD_AUTH"
check_code N17.7.20 "a number column refuses a value that is not one" 400 VALIDATION_FAILED
patch_json "$FIELDS" '{"customFields":{"listed":""}}' -H "$LEAD_AUTH"
check N17.7.21 "a blank value clears that one column and leaves the others" "false|Chair" \
  "$(json '.customFields | has("listed")')|$(json '.customFields.boardSeat')"
patch_json "$FIELDS" '{"customFields":null}' -H "$LEAD_AUTH"
check N17.7.22 "a null map changes nothing" "Chair" "$(json '.customFields.boardSeat')"

post_json "$PEOPLE" "$(person "Sara Board $RUN_TAG" '.customFields = {boardMember:"Y"}')" -H "$LEAD_AUTH"
check N17.7.23 "a candidate's custom cell is written through the same rules" "201 true" "$LAST_STATUS $(json '.customFields.boardMember')"

http DELETE "$COLUMNS/$COL_BOARD" -H "$LEAD_AUTH"
check_status N17.7.24 "a column is deleted" 204
get "$COLUMNS" -H "$LEAD_AUTH"
check N17.7.25 "…and is gone from the grid" "0" "$(json "[.columns[] | select(.id == \"$COL_BOARD\")] | length")"
get "$TRIAGE?status=shortlisted" -H "$LEAD_AUTH"
check N17.7.26 "…while the rows keep their values: only the definition goes" "Chair" \
  "$(json ".companies[] | select(.id == \"$ALPHA_ID\") | .customFields.boardSeat")"
# The key was slugged from the ORIGINAL label and survived the rename, so it is that label which
# slugs back onto it; "Board Seats" would mint boardSeats.
define '{"target":"company","label":"Board Seat"}'
check N17.7.27 "redefining it under its original name takes the same key back" "boardSeat" "$(json '.fieldKey')"
http DELETE "$COLUMNS/$(new_uuid)" -H "$LEAD_AUTH"
check_code N17.7.28 "deleting a column this mandate does not hold" 404 NOT_FOUND

section "N17.8  removing a company unmaps its executives"

GAMMA="Delete Me Holdings $RUN_TAG"
capture "$LEAD_AUTH" "$GAMMA"
GAMMA_ID=$(json '.id')
post_json "$PEOPLE" "$(person "Faris Kanaan $RUN_TAG" ".triageCompanyId = \"$GAMMA_ID\" | .email = \"faris@example.com\"")" -H "$LEAD_AUTH"
FARIS=$(json '.id')
check N17.8.1 "an executive is mapped at the doomed company" "$GAMMA_ID" "$(json '.triageCompanyId')"

http DELETE "$TRIAGE/$GAMMA_ID" -H "$LEAD_AUTH"
check_status N17.8.2 "the lead removes the company from the mandate" 204
get "$PEOPLE/$FARIS" -H "$LEAD_AUTH"
check_status N17.8.3 "the executive survives it" 200
check N17.8.4 "…unmapped, with the employer's name kept" "|$GAMMA" "$(json '.triageCompanyId // empty')|$(json '.companyName')"
check N17.8.5 "…because the row's foreign key is ON DELETE SET NULL" "t" \
  "$(sql "SELECT triage_company_id IS NULL FROM app_lm_project_candidate WHERE id = '$FARIS'")"
get "$PEOPLE?unmapped=true" -H "$LEAD_AUTH"
check N17.8.6 "…and is listed among the unmapped" "1" "$(json "[.candidates[] | select(.id == \"$FARIS\")] | length")"
http DELETE "$TRIAGE/$GAMMA_ID" -H "$LEAD_AUTH"
check_code N17.8.7 "removing it twice" 404 NOT_FOUND
capture "$LEAD_AUTH" "$GAMMA"
check_status N17.8.8 "a removed company is not remembered — it can be typed in again" 201

http DELETE "$PEOPLE/$FARIS" -H "$LEAD_AUTH"
check_status N17.8.9 "an executive is removed" 204
get "$PEOPLE/$FARIS" -H "$LEAD_AUTH"
check_code N17.8.10 "…and is gone" 404 NOT_FOUND
check N17.8.11 "…taking their contact rows with them" "0" \
  "$(sql "SELECT count(*) FROM app_lm_candidate_contact WHERE candidate_id = '$FARIS'")"

section "N17.9  the client seat reads, and writes nothing"

get "$TRIAGE?status=shortlisted" -H "$CLIENT_AUTH"
check_status N17.9.1 "the client seat reads the companies" 200
check N17.9.2 "…the same page staff see" "true" "$(json "[.companies[].id] | index(\"$ALPHA_ID\") != null")"
get "$PEOPLE" -H "$CLIENT_AUTH"
check_status N17.9.3 "…and the executives" 200
get "$PEOPLE/$RANIA" -H "$CLIENT_AUTH"
check_status N17.9.4 "…one of them whole" 200
get "$COLUMNS" -H "$CLIENT_AUTH"
check_status N17.9.5 "…and the grid's custom columns" 200

# Well-formed bodies throughout: Bean Validation runs before method security, and a malformed body
# would answer 400 without ever reaching the gate.
capture "$CLIENT_AUTH" "Client Typed $RUN_TAG"
check_code N17.9.6 "typing a company in" 403 FORBIDDEN
post_json "$TRIAGE/bulk" '{"apolloAccountIds":["e2e-ghost-1"],"status":"declined"}' -H "$CLIENT_AUTH"
check_code N17.9.7 "a bulk add" 403 FORBIDDEN
patch_json "$TRIAGE/$ALPHA_ID" '{"status":"declined"}' -H "$CLIENT_AUTH"
check_code N17.9.8 "moving a company" 403 FORBIDDEN
put_json "$TRIAGE/$ALPHA_ID" "$(jq -nc --arg n "$ALPHA" '{companyName:$n}')" -H "$CLIENT_AUTH"
check_code N17.9.9 "editing a company" 403 FORBIDDEN
patch_json "$FIELDS" '{"customFields":{"boardSeat":"Client"}}' -H "$CLIENT_AUTH"
check_code N17.9.10 "writing a custom cell" 403 FORBIDDEN
http DELETE "$TRIAGE/$BETA_ID" -H "$CLIENT_AUTH"
check_code N17.9.11 "removing a company" 403 FORBIDDEN
post_json "$PEOPLE" "$(person "Client Added $RUN_TAG")" -H "$CLIENT_AUTH"
check_code N17.9.12 "adding an executive" 403 FORBIDDEN
put_json "$PEOPLE/$RANIA" "$RANIA_PROFILE" -H "$CLIENT_AUTH"
check_code N17.9.13 "editing an executive" 403 FORBIDDEN
patch_json "$PEOPLE/$RANIA" '{"status":"offLimits"}' -H "$CLIENT_AUTH"
check_code N17.9.14 "moving an executive's status" 403 FORBIDDEN
put_json "$PEOPLE/$RANIA/contacts" '{"emails":[],"phones":[]}' -H "$CLIENT_AUTH"
check_code N17.9.15 "replacing an executive's contacts" 403 FORBIDDEN
http DELETE "$PEOPLE/$RANIA" -H "$CLIENT_AUTH"
check_code N17.9.16 "removing an executive" 403 FORBIDDEN
define_as_client() { post_json "$COLUMNS" '{"target":"company","label":"Client Column"}' -H "$CLIENT_AUTH"; }
define_as_client
check_code N17.9.17 "defining a column" 403 FORBIDDEN
patch_json "$COLUMNS/$COL_HEAD" '{"label":"Client Renamed"}' -H "$CLIENT_AUTH"
check_code N17.9.18 "renaming a column" 403 FORBIDDEN
http DELETE "$COLUMNS/$COL_HEAD" -H "$CLIENT_AUTH"
check_code N17.9.19 "deleting a column" 403 FORBIDDEN

get "$PEOPLE/$RANIA" -H "$LEAD_AUTH"
check N17.9.20 "none of it landed" "2 1" "$(json '.contacts.emails | length') $(json '.contacts.phones | length')"

section "N17.10  another workspace"

get "$TRIAGE" -H "$OUTSIDER_AUTH"
check_code N17.10.1 "another workspace's admin cannot read the companies" 404 NOT_FOUND
get "$PEOPLE/$RANIA" -H "$OUTSIDER_AUTH"
check_code N17.10.2 "…nor an executive" 404 NOT_FOUND
capture "$OUTSIDER_AUTH" "Outsider Typed $RUN_TAG"
check_code N17.10.3 "…nor type a company in" 404 NOT_FOUND
put_json "$PEOPLE/$RANIA/contacts" '{"emails":[],"phones":[]}' -H "$OUTSIDER_AUTH"
check_code N17.10.4 "…nor touch a contact" 404 NOT_FOUND

summary
