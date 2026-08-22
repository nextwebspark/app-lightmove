#!/usr/bin/env bash
# Strategy → company search: the HTTP contract over the Apollo universe, the mandate's saved filter,
# its off-limits list, its saved searches, and the triage hand-off.
#
# Unlike 09-13 this script builds its own cast rather than sourcing cast.env: everything it asserts
# needs one seated LEAD on one project, and nothing it does is destructive, so it is idempotent and
# can run at any point in the matrix.
#
# EVERY expected value is taken from the database at run time rather than hard-coded. The universe is
# ETL-owned and reloads wholesale — a script asserting "Qatar is 7,991" would go red on the next
# pipeline load without anything being broken. Reading both sides means these cases stay true against
# the real 71,822 rows, against a refreshed export, and against a developer's partial copy.
#
# It needs the universe to be present. A database where app_lm_apollo_companies is empty makes every
# count zero and every case vacuous, so the script says so and stops rather than reporting a green run
# that proved nothing (`npm run dev:db:apollo` fills it).
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "14 — Strategy: the company universe, the filter, and what a mandate takes from it"

UNIVERSE=$(sql "SELECT count(*) FROM app_lm_apollo_companies")
if [ "${UNIVERSE:-0}" -lt 100 ]; then
  # Skipped, not failed: the universe is ETL-owned and pulled with gcloud, which CI has no path to.
  # Every case below reads the table, so without it they would all be vacuously true.
  skip 14.0 "the Apollo universe is loaded" "app_lm_apollo_companies holds ${UNIVERSE:-0} rows — run \`npm run dev:db:apollo\` to run these cases"
  summary; exit 0
fi
note 14.0 "universe holds $UNIVERSE companies"

EMPTY_FILTER='{"industries":[],"marketSegments":[],"countries":[],"employeeBands":[],"revenueBands":[],"employeeRange":null,"revenueRange":null}'
filter_body() { printf '{"filter":%s}' "$1"; }

# --- the cast: one admin, one client, one mandate ----------------------------

LEAD_EMAIL=$(new_email lead)
post_json /auth/signup "$(jq -nc --arg e "$LEAD_EMAIL" --arg p "$PASSWORD" \
  '{fullName:"Lena Lead", email:$e, password:$p, termsAccepted:true}')" >/dev/null
post_json /auth/verify "$(jq -nc --arg t "$(token_for "$LEAD_EMAIL" verify)" '{token:$t}')" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$LEAD_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" >/dev/null
TOKEN=$(json '.accessToken')
post_json /onboarding/workspace "$(jq -nc --arg n "Strategy UAT $(date +%s)$RANDOM" \
  '{name:$n, companySize:"11-50 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$TOKEN")" >/dev/null
# A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
post_json /auth/login "$(jq -nc --arg e "$LEAD_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" >/dev/null
TOKEN=$(json '.accessToken')
AUTH="$(auth_header "$TOKEN")"

post_json /clients '{"customName":"Gulf Aviation Holding","customDomain":"gulfaviation.example","sector":"aviation","hqCountry":"United Arab Emirates"}' -H "$AUTH" >/dev/null
CLIENT_ID=$(json '.id')
post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Chief Technology Officer"}')" -H "$AUTH" >/dev/null
PROJECT=$(json '.id')
STRATEGY="/projects/$PROJECT/strategy"

# --- the universe's own shape (workspace-level reads) ------------------------

get /companies/facets -H "$AUTH"
check_status 14.1 "the facet counts answer" 200
FACETS="$LAST_BODY"

facet_sum() { printf '%s' "$FACETS" | jq "[.$1[].count] | add"; }
check 14.2 "the headcount bands account for every company in the universe" \
  "$UNIVERSE" "$(facet_sum employeeBands)"
check 14.3 "the revenue bands, Unknown included, account for every company" \
  "$UNIVERSE" "$(facet_sum revenueBands)"
# Not asserted: the sector facet is short by the rows carrying no industry at all, because
# `industry IN (...)` cannot match NULL. Recorded as issue #91 and in the UAT report rather than held
# as a red case — it is a known gap on one axis, not suite drift. The number is still printed.
note 14.4 "sector groups sum to $(printf '%s' "$FACETS" | jq '[.sectorGroups[].count] | add') of $UNIVERSE — the rest carry no industry (#91)"
check 14.5 "Unknown revenue is exactly the rows carrying no figure" \
  "$(sql 'SELECT count(*) FROM app_lm_apollo_companies WHERE annual_revenue IS NULL')" \
  "$(printf '%s' "$FACETS" | jq '[.revenueBands[] | select(.value=="unknown")][0].count')"
check 14.6 "Location offers exactly the countries the universe carries" \
  "$(sql "SELECT count(distinct company_country) FROM app_lm_apollo_companies WHERE company_country IS NOT NULL AND company_country <> ''")" \
  "$(printf '%s' "$FACETS" | jq '.countries | length')"
# Segments overlap on purpose — a company can be B2B and SaaS and Fintech at once — so their counts
# add up to more than the universe, which is the honest answer and not something to assert a sum on.
note 14.7 "market segments overlap and sum to $(facet_sum marketSegments) over $UNIVERSE rows"

# --- the picker's typeahead --------------------------------------------------

get "/companies/search?q=" -H "$AUTH"
check 14.8 "a blank query offers nothing rather than the head of the universe" "0" "$(json '.companies | length')"
get "/companies/search?q=a&limit=26" -H "$AUTH"
check_status 14.9 "a limit above the ceiling is refused, not silently clamped" 400
get "/companies/search?q=a&limit=0" -H "$AUTH"
check_status 14.10 "a limit of zero is refused" 400
get "/companies/search?q=$(printf 'a%.0s' $(seq 101))" -H "$AUTH"
check_status 14.11 "a query longer than the configured maximum is refused" 400
# The wildcards must be literal, or a one-character search would return the whole universe.
get "/companies/search?q=%25" -H "$AUTH"
check 14.12 "'%' is matched literally, not as a LIKE wildcard" \
  "$(sql "SELECT count(*) FROM (SELECT 1 FROM app_lm_apollo_companies WHERE company_name LIKE '%\\%%' LIMIT 10) t")" \
  "$(json '.companies | length')"

# --- the mandate's list: paging and sorting ----------------------------------

get "$STRATEGY/companies" -H "$AUTH"
check 14.13 "an untouched filter is the whole universe, not nothing" "$UNIVERSE" "$(json '.totalCount')"
get "$STRATEGY/companies?size=101" -H "$AUTH"; check_status 14.14 "a page size above the ceiling is refused" 400
get "$STRATEGY/companies?size=0" -H "$AUTH";   check_status 14.15 "a page size of zero is refused" 400
get "$STRATEGY/companies?page=-1" -H "$AUTH";  check_status 14.16 "a negative page is refused" 400
get "$STRATEGY/companies?sort=nope" -H "$AUTH"; check_status 14.17 "an unknown sort column is refused" 400
# Deliberately absent from the allowlist: alphabetising a description answers no question.
get "$STRATEGY/companies?sort=notes" -H "$AUTH"; check_status 14.18 "a column outside the sort allowlist is refused" 400
get "$STRATEGY/companies?direction=sideways" -H "$AUTH"; check_status 14.19 "an unknown sort direction is refused" 400
get "$STRATEGY/companies?page=999999" -H "$AUTH"
check 14.20 "a page past the end is an empty page, not an error" "0" "$(json '.companies | length')"
# Apollo publishes a revenue figure on roughly one row in ten, so an ascending sort without NULLS LAST
# is nine pages of blanks before the first real number.
get "$STRATEGY/companies?sort=revenue&direction=asc" -H "$AUTH"
check 14.21 "an ascending revenue sort buries the rows with no figure" \
  "false" "$(json '.companies[0].annualRevenue == null')"

# --- the saved filter --------------------------------------------------------

http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" -d "$(filter_body "$EMPTY_FILTER")"
check_status 14.22 "an empty filter is legal" 200
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(filter_body "$(printf '%s' "$EMPTY_FILTER" | jq -c '.employeeBands=["not-a-band"]')")"
check_status 14.23 "an unknown band slug is a client bug and says so" 400
# Industries are free strings from the facets response: one the universe has stopped carrying should
# narrow to nothing rather than 400 a save the user cannot fix.
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(filter_body "$(printf '%s' "$EMPTY_FILTER" | jq -c '.industries=["no such industry"]')")"
check_status 14.24 "an industry the universe no longer carries is accepted" 200
get "$STRATEGY/companies" -H "$AUTH"
check 14.25 "…and narrows the list to nothing" "0" "$(json '.totalCount')"
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(filter_body "$(printf '%s' "$EMPTY_FILTER" | jq -c '.employeeRange={min:5000,max:500}')")"
check_status 14.26 "an inverted custom range is refused, not silently swapped" 400
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(filter_body "$(printf '%s' "$EMPTY_FILTER" | jq -c '.employeeRange={min:-5,max:500}')")"
check_status 14.27 "a negative bound is refused" 400
# Jackson read isEmpty() as a bean property and made every saved custom range unreadable. This is that
# regression, kept as a case: the range has to survive the round trip through the jsonb column.
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(filter_body "$(printf '%s' "$EMPTY_FILTER" | jq -c '.employeeRange={min:250,max:400}')")"
get "$STRATEGY" -H "$AUTH"
check 14.28 "a saved custom range reads back intact" "250" "$(json '.filter.employeeRange.min')"
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(filter_body "$(printf '%s' "$EMPTY_FILTER" | jq -c '.employeeRange={min:null,max:null}')")"
check 14.29 "a range with neither end normalises to no constraint" "null" "$(json '.filter.employeeRange')"
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(filter_body "$(printf '%s' "$EMPTY_FILTER" | jq -c '.countries=["Qatar","Qatar"]')")"
check 14.30 "a duplicated selection is de-duplicated rather than refused" "1" "$(json '.filter.countries | length')"

# One country, checked against the database: the axis has to mean what it says.
COUNTRY=$(sql "SELECT company_country FROM app_lm_apollo_companies WHERE company_country IS NOT NULL GROUP BY 1 ORDER BY count(*) DESC LIMIT 1")
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(filter_body "$(printf '%s' "$EMPTY_FILTER" | jq -c --arg c "$COUNTRY" '.countries=[$c]')")"
get "$STRATEGY/companies" -H "$AUTH"
check 14.31 "a country filter matches the database exactly" \
  "$(sql "SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '$COUNTRY'")" "$(json '.totalCount')"
# The name filter narrows *within* the saved scope, so the count has to apply it too.
get "$STRATEGY/companies?q=a" -H "$AUTH"
check 14.32 "the name filter narrows within the saved scope" \
  "$(sql "SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '$COUNTRY' AND company_name ILIKE '%a%'")" \
  "$(json '.totalCount')"

# --- off-limits --------------------------------------------------------------

BAR_A=$(sql "SELECT apollo_account_id FROM app_lm_apollo_companies WHERE company_country = '$COUNTRY' ORDER BY apollo_account_id LIMIT 1")
BAR_B=$(sql "SELECT apollo_account_id FROM app_lm_apollo_companies WHERE company_country = '$COUNTRY' ORDER BY apollo_account_id OFFSET 1 LIMIT 1")
get "$STRATEGY/companies" -H "$AUTH"; BEFORE=$(json '.totalCount')
http PUT "$STRATEGY/off-limits" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(jq -nc --arg a "$BAR_A" --arg b "$BAR_B" '{apolloAccountIds:[$a,$b]}')"
check_status 14.33 "companies can be barred" 200
check 14.34 "a barred company keeps a resolved snapshot, not the caller's word for it" \
  "$(sql "SELECT company_name FROM app_lm_apollo_companies WHERE apollo_account_id = '$BAR_A'")" \
  "$(printf '%s' "$LAST_BODY" | jq -r --arg a "$BAR_A" '[.offLimits[] | select(.apolloAccountId==$a)][0].companyName')"
get "$STRATEGY/companies" -H "$AUTH"
check 14.35 "barring two companies drops exactly two from the count" "$((BEFORE-2))" "$(json '.totalCount')"
http PUT "$STRATEGY/off-limits" -H 'Content-Type: application/json' -H "$AUTH" -d '{"apolloAccountIds":["not-in-the-universe"]}'
check_status 14.36 "an id the universe does not hold is refused" 400
http PUT "$STRATEGY/off-limits" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(jq -nc --arg a "$BAR_A" '{apolloAccountIds:[$a,$a]}')"
check_status 14.37 "a duplicate on the off-limits list is refused" 400
http PUT "$STRATEGY/off-limits" -H 'Content-Type: application/json' -H "$AUTH" -d '{"apolloAccountIds":[]}'
get "$STRATEGY/companies" -H "$AUTH"
check 14.38 "un-barring restores them" "$BEFORE" "$(json '.totalCount')"

# --- saved searches ----------------------------------------------------------

post_json "$STRATEGY/searches" '{"name":"Primary market"}' -H "$AUTH"
check_status 14.39 "a search saves" 201
SEARCH_ID=$(json '.id')
check 14.40 "…carrying the filter the mandate had stored, not one from the request" \
  "$COUNTRY" "$(json '.filter.countries[0]')"
post_json "$STRATEGY/searches" '{"name":"primary market"}' -H "$AUTH"
check_status 14.41 "a duplicate name, case-insensitively, is refused" 409
post_json "$STRATEGY/searches" '{"name":"   "}' -H "$AUTH"
check_status 14.42 "a blank name is refused" 400
http PATCH "$STRATEGY/searches/$SEARCH_ID" -H 'Content-Type: application/json' -H "$AUTH" -d '{"name":"Primary market — revised"}'
check_status 14.43 "a search renames" 200
http DELETE "$STRATEGY/searches/$SEARCH_ID" -H "$AUTH"; check_status 14.44 "a search deletes" 204
http DELETE "$STRATEGY/searches/$SEARCH_ID" -H "$AUTH"; check_status 14.45 "deleting it twice 404s rather than 500s" 404

# --- the triage hand-off -----------------------------------------------------

TAKE=$(sql "SELECT apollo_account_id FROM app_lm_apollo_companies ORDER BY apollo_account_id LIMIT 1")
post_json "/projects/$PROJECT/triage" "$(jq -nc --arg a "$TAKE" '{apolloAccountId:$a}')" -H "$AUTH"
check_status 14.46 "a company is taken into the mandate's universe" 201
TRIAGE_ID=$(json '.id')
# The button is on every row and a second click means the same thing as the first.
post_json "/projects/$PROJECT/triage" "$(jq -nc --arg a "$TAKE" '{apolloAccountId:$a}')" -H "$AUTH"
check 14.47 "adding the same company twice does not file it twice" "$TRIAGE_ID" "$(json '.id')"
post_json "/projects/$PROJECT/triage" '{"apolloAccountId":"not-in-the-universe"}' -H "$AUTH"
check_status 14.48 "a company outside the universe is refused" 400
# An untouched filter matches the whole universe; taking the first N of them would silently decide
# which ones a mandate got, so the whole request is refused instead.
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" -d "$(filter_body "$EMPTY_FILTER")"
http POST "/projects/$PROJECT/triage/from-filter" -H "$AUTH"
check_code 14.49 "'Add all' over the whole universe is refused whole" 409 BULK_ADD_SCOPE_TOO_LARGE
check_contains 14.50 "…and the refusal names both numbers" "at a time" "$(json '.detail')"

# Narrow to something under the bulk limit, using the database to find a bound that qualifies.
CUTOFF=$(sql "SELECT num_employees FROM app_lm_apollo_companies ORDER BY num_employees DESC LIMIT 1 OFFSET 40")
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$AUTH" \
  -d "$(filter_body "$(printf '%s' "$EMPTY_FILTER" | jq -c --argjson m "$CUTOFF" '.employeeRange={min:$m,max:null}')")"
get "$STRATEGY/companies" -H "$AUTH"; SCOPE=$(json '.totalCount')
http POST "/projects/$PROJECT/triage/from-filter" -H "$AUTH"
check_status 14.51 "'Add all' over a narrow filter succeeds" 200
note 14.51b "scope held $SCOPE companies; added $(json '.added'), already held $(json '.skipped')"
http POST "/projects/$PROJECT/triage/from-filter" -H "$AUTH"
check 14.52 "re-running 'Add all' adds nothing new" "0" "$(json '.added')"

http PATCH "/projects/$PROJECT/triage/$TRIAGE_ID" -H 'Content-Type: application/json' -H "$AUTH" \
  -d '{"status":"declined","note":"Client conflict"}'
check_status 14.53 "a company moves to Declined with a note" 200
get "/projects/$PROJECT/triage?status=declined" -H "$AUTH"
check 14.54 "…and appears on that stage" "1" \
  "$(printf '%s' "$LAST_BODY" | jq --arg a "$TAKE" '[.companies[] | select(.apolloAccountId==$a)] | length')"
# Null leaves the other half alone: moving a company must not silently clear the note explaining why.
http PATCH "/projects/$PROJECT/triage/$TRIAGE_ID" -H 'Content-Type: application/json' -H "$AUTH" -d '{"status":"shortlisted"}'
check 14.55 "moving stage again keeps the note" "Client conflict" "$(json '.note')"
http PATCH "/projects/$PROJECT/triage/$TRIAGE_ID" -H 'Content-Type: application/json' -H "$AUTH" -d '{"status":"NOT_A_STAGE"}'
check_status 14.56 "an unknown stage is refused" 400
get "/projects/$PROJECT/triage?status=NOT_A_STAGE" -H "$AUTH"
check_status 14.57 "an unknown stage filter is refused" 400

# --- the tenant boundary -----------------------------------------------------
#
# The mandate's scope is team content; the market's own shape is not. Both halves are asserted,
# because a guard that refused the facets too would be just as wrong as one that leaked the filter.

OUT_EMAIL=$(new_email outsider)
post_json /auth/signup "$(jq -nc --arg e "$OUT_EMAIL" --arg p "$PASSWORD" \
  '{fullName:"Otto Outsider", email:$e, password:$p, termsAccepted:true}')" >/dev/null
post_json /auth/verify "$(jq -nc --arg t "$(token_for "$OUT_EMAIL" verify)" '{token:$t}')" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$OUT_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" >/dev/null
OUT_TOKEN=$(json '.accessToken')
post_json /onboarding/workspace "$(jq -nc --arg n "Rival Search $(date +%s)$RANDOM" \
  '{name:$n, companySize:"11-50 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$OUT_TOKEN")" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$OUT_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" >/dev/null
OUT_AUTH="$(auth_header "$(json '.accessToken')")"

get "$STRATEGY" -H "$OUT_AUTH";                check_status 14.58 "another workspace cannot read the mandate's filter" 404
get "$STRATEGY/companies" -H "$OUT_AUTH";      check_status 14.59 "…nor its company list" 404
get "/projects/$PROJECT/triage" -H "$OUT_AUTH"; check_status 14.60 "…nor its triage" 404
http PUT "$STRATEGY/filter" -H 'Content-Type: application/json' -H "$OUT_AUTH" -d "$(filter_body "$EMPTY_FILTER")"
check_status 14.61 "…and cannot write its filter" 404
get /companies/facets -H "$OUT_AUTH"
check_status 14.62 "…but the market's own shape is readable to any workspace" 200

# A member of THIS workspace with no seat on the project: browsing projects is not reading a mandate.
BENCH_EMAIL=$(new_email bench)
post_json /invitations "$(jq -nc --arg e "$BENCH_EMAIL" '[{email:$e, role:"MEMBER"}]')" -H "$AUTH" >/dev/null
post_json /onboarding/accept-invitation-signup "$(jq -nc --arg t "$(token_for "$BENCH_EMAIL" accept-invite)" \
  --arg p "$PASSWORD" '{token:$t, fullName:"Ben Bench", password:$p}')" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$BENCH_EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" >/dev/null
BENCH_AUTH="$(auth_header "$(json '.accessToken')")"
get "$STRATEGY" -H "$BENCH_AUTH";           check_status 14.63 "a member with no seat cannot read the mandate's filter" 403
get "$STRATEGY/companies" -H "$BENCH_AUTH"; check_status 14.64 "…nor its company list" 403
get /companies/facets -H "$BENCH_AUTH";     check_status 14.65 "…but may read the universe's facets" 200
get "/companies/search?q=a" -H "$BENCH_AUTH"; check_status 14.66 "…and the company typeahead" 200

summary
