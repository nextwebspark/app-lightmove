#!/usr/bin/env bash
# The reads a mandate is judged by: the talent mapping report and its staff-only researcher
# breakdown, the talent map, the position side panel's recent activity and the projects-list counts
# beside it — and Settings → Templates, the firm's own role-template library.
#
# No AI, and nothing here needs a vendor: the report is aggregated live from the rows, and with no
# Mapbox token the map answers from the geocoding cache alone and asks nobody.
#
# Builds its own cast — a lead, a researcher seated on the mandate, a client representative attached
# to it, and an admin of a second workspace — and seeds the mandate through the ordinary APIs, so
# every figure asserted below is derived from this script's own rows. Needs no Apollo universe: the
# companies are typed in by hand.
#
#   company            stage        executives (filed by)
#   Aurora Holdings    inUniverse   Fatima  F  Emirati             C-Suite  brief ccy 1.0M+0.1M allow+0.2M bonus  (lead)
#                                   Karim   M  Egyptian            N-1      brief ccy 500k                        (lead)
#   Basalt Mining      shortlisted  Rana    O  Arab expat, non-GCC C-Suite  OTHER ccy 300k                        (lead)
#                                   James   –  British             N-1      —                                     (researcher)
#   Cedar Retail       inUniverse   —
#   Delta Contracting  declined     Noura   F  Saudi               C-Suite  —                                     (researcher)
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "19 — report, talent map, activity, projects list, and Settings → Templates"

# --- helpers ------------------------------------------------------------------

login_as() { # login_as EMAIL -> access token
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
  json '.accessToken'
}

signup_verified() { # signup_verified EMAIL FULLNAME -> access token, verified, no workspace yet
  post_json /auth/signup "$(jq -nc --arg e "$1" --arg p "$PASSWORD" --arg n "$2" \
    '{fullName:$n, email:$e, password:$p, termsAccepted:true}')" >/dev/null
  post_json /auth/verify "$(jq -nc --arg t "$(token_for "$1" verify)" '{token:$t}')" >/dev/null
  login_as "$1"
}

make_workspace() { # make_workspace TOKEN NAME
  post_json /onboarding/workspace "$(jq -nc --arg n "$2" \
    '{name:$n, companySize:"11-50 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
    -H "$(auth_header "$1")" >/dev/null
}

accept_invite_signup() { # accept_invite_signup EMAIL FULLNAME
  post_json /onboarding/accept-invitation-signup \
    "$(jq -nc --arg t "$(token_for "$1" accept-invite)" --arg n "$2" --arg p "$PASSWORD" \
       '{token:$t, fullName:$n, password:$p}')" >/dev/null
}

member_id_of() {
  sql "SELECT m.id FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
       WHERE u.email = '$1' AND m.status = 'ACTIVE'"
}

capture_company() { # capture_company NAME STATUS INDUSTRY COUNTRY CITY -> id
  post_json "/projects/$PROJECT/triage/capture" "$(jq -nc --arg n "$1" --arg s "$2" --arg i "$3" \
    --arg c "$4" --arg t "$5" '{companyName:$n, status:$s, industry:$i, companyCountry:$c, companyCity:$t}')" \
    -H "$LEAD" >/dev/null
  json '.id'
}

add_candidate() { # add_candidate AUTH JSON -> id
  post_json "/projects/$PROJECT/candidates" "$2" -H "$1" >/dev/null
  json '.id'
}

# --- the cast -------------------------------------------------------------------

LEAD_NAME="Rhea Reporter"
RESEARCHER_NAME="Rex Researcher"
LEAD_EMAIL=$(new_email replead)
RESEARCHER_EMAIL=$(new_email represearcher)
REP_EMAIL=$(new_email reprep)
OUTSIDER_EMAIL=$(new_email repoutsider)

LEAD_TOKEN=$(signup_verified "$LEAD_EMAIL" "$LEAD_NAME")
make_workspace "$LEAD_TOKEN" "Report UAT $(date +%s)$RANDOM"
# A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
LEAD="$(auth_header "$(login_as "$LEAD_EMAIL")")"

post_json /invitations "$(jq -nc --arg e "$RESEARCHER_EMAIL" '[{email:$e, role:"MEMBER"}]')" -H "$LEAD" >/dev/null
accept_invite_signup "$RESEARCHER_EMAIL" "$RESEARCHER_NAME"
RESEARCHER="$(auth_header "$(login_as "$RESEARCHER_EMAIL")")"

post_json /clients '{"customName":"Report Holding","customDomain":"reportholding.example"}' -H "$LEAD" >/dev/null
CLIENT_ID=$(json '.id')
post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Chief Financial Officer"}')" -H "$LEAD"
check_status 19.0 "the lead creates the mandate" 201
PROJECT=$(json '.id')

http PUT "/projects/$PROJECT/members/$(member_id_of "$RESEARCHER_EMAIL")" \
  -H 'Content-Type: application/json' -d '{"role":"RESEARCHER"}' -H "$LEAD"
check_status 19.0a "…and seats a colleague on it as a RESEARCHER" 200

post_json "/projects/$PROJECT/representatives/invitations" \
  "$(jq -nc --arg e "$REP_EMAIL" '{fullName:"Cora Client", position:"Group CFO", email:$e}')" -H "$LEAD" >/dev/null
accept_invite_signup "$REP_EMAIL" "Cora Client"
REP="$(auth_header "$(login_as "$REP_EMAIL")")"
get "/projects/$PROJECT/triage" -H "$REP"
check_status 19.0b "…and a client representative holds a read seat on it" 200

OUTSIDER_TOKEN=$(signup_verified "$OUTSIDER_EMAIL" "Otis Outsider")
make_workspace "$OUTSIDER_TOKEN" "Report Rival $(date +%s)$RANDOM"
OUTSIDER="$(auth_header "$(login_as "$OUTSIDER_EMAIL")")"

# --- the seed -------------------------------------------------------------------

section "19.1  seeding the mandate through the ordinary APIs"

# The report compares every package against the brief's currency, read through the same seam the
# report uses (it drafts nothing). The "other" currency is anything that is not it.
get "/projects/$PROJECT/position/compensation" -H "$LEAD"
check_status 19.1 "the brief's compensation is readable before anything is drafted" 200
BRIEF_CCY=$(json '.currency // empty')
if [ "$BRIEF_CCY" = "USD" ]; then OTHER_CCY="EUR"; else OTHER_CCY="USD"; fi
note 19.2 "brief currency is '${BRIEF_CCY:-none}', the other currency seeded is $OTHER_CCY"

AURORA=$(capture_company "Aurora Holdings" inUniverse "Financial Services" "United Arab Emirates" "Dubai")
BASALT=$(capture_company "Basalt Mining" shortlisted "Mining & Metals" "Saudi Arabia" "Riyadh")
CEDAR=$(capture_company "Cedar Retail" inUniverse "Retail" "Kuwait" "Kuwait City")
DELTA=$(capture_company "Delta Contracting" declined "Construction" "Bahrain" "Manama")
check 19.3 "four companies are typed in, one per stage and a spare" "4" \
  "$(sql "SELECT count(*) FROM app_lm_project_triage_company WHERE project_id = '$PROJECT'")"

FATIMA=$(add_candidate "$LEAD" "$(jq -nc --arg c "$AURORA" --arg ccy "${BRIEF_CCY:-AED}" \
  '{triageCompanyId:$c, fullName:"Fatima Al Mansoori", title:"Group CFO", seniority:"C-Suite",
    gender:"female", nationality:"Emirati", locationCountry:"United Arab Emirates", locationCity:"Dubai",
    compensation:{currency:$ccy, baseSalary:1000000, allowances:100000, bonus:200000}, source:"manual"}')")
KARIM=$(add_candidate "$LEAD" "$(jq -nc --arg c "$AURORA" --arg ccy "${BRIEF_CCY:-AED}" \
  '{triageCompanyId:$c, fullName:"Karim Mostafa", title:"Financial Controller", seniority:"N-1",
    gender:"male", nationality:"Egyptian", locationCountry:"United Arab Emirates", locationCity:"Dubai",
    compensation:{currency:$ccy, baseSalary:500000}, source:"manual"}')")
RANA=$(add_candidate "$LEAD" "$(jq -nc --arg c "$BASALT" --arg ccy "$OTHER_CCY" \
  '{triageCompanyId:$c, fullName:"Rana Haddad", title:"Chief Financial Officer", seniority:"C-Suite",
    gender:"other", nationality:"Arab expat, non-GCC", locationCountry:"Saudi Arabia", locationCity:"Riyadh",
    compensation:{currency:$ccy, baseSalary:300000}, source:"manual"}')")
JAMES=$(add_candidate "$RESEARCHER" "$(jq -nc --arg c "$BASALT" \
  '{triageCompanyId:$c, fullName:"James Whitfield", title:"Head of Treasury", seniority:"N-1",
    nationality:"British", source:"manual"}')")
NOURA=$(add_candidate "$RESEARCHER" "$(jq -nc --arg c "$DELTA" \
  '{triageCompanyId:$c, fullName:"Noura Al Saud", title:"Chief Financial Officer", seniority:"C-Suite",
    gender:"female", nationality:"Saudi", source:"manual"}')")
check 19.4 "five executives are mapped, three by the lead and two by the researcher" "3/2" \
  "$(sql "SELECT count(*) FILTER (WHERE u.email = '$LEAD_EMAIL') || '/' || count(*) FILTER (WHERE u.email = '$RESEARCHER_EMAIL')
          FROM app_lm_project_candidate c JOIN app_lm_user u ON u.id = c.added_by WHERE c.project_id = '$PROJECT'")"
check 19.5 "gender is not recorded for one of them — NULL, not a fourth value" "1" \
  "$(sql "SELECT count(*) FROM app_lm_project_candidate WHERE project_id = '$PROJECT' AND gender IS NULL")"

http PATCH "/projects/$PROJECT/candidates/$FATIMA" -H 'Content-Type: application/json' \
  -d '{"status":"engaged"}' -H "$LEAD"
check_status 19.6 "one executive moves to engaged" 200

# Audit rows land asynchronously; the activity cases below read them, so wait for the last one.
await_sql "SELECT count(*) FROM app_lm_audit_event WHERE target_id = '$PROJECT'
           AND event_type = 'CANDIDATE_UPDATED' AND metadata->>'status' = 'engaged'" "1" >/dev/null

# --- the report -------------------------------------------------------------------

section "19.2  the talent mapping report — four chapters, from the rows"

get "/projects/$PROJECT/report" -H "$LEAD"
check_status 19.10 "the lead reads the report" 200
REPORT="$LAST_BODY"
rj() { printf '%s' "$REPORT" | jq -r "$1" 2>/dev/null; }

check 19.11 "it has a head and the four chapters" "head,progress,market,remuneration,diversity" \
  "$(rj '[keys_unsorted[]] | join(",")')"
check 19.12 "the universe is the in-universe and shortlisted companies, never the declined" "3" \
  "$(rj '.head.universeCount')"
check 19.13 "every executive is counted, whichever stage their company sits at" "5" "$(rj '.head.executivesMapped')"
check 19.14 "…and nothing was truncated" "false" "$(rj '.head.truncated')"

check 19.15 "progress: the target is the universe" "3" "$(rj '.progress.targetCompanies')"
check 19.16 "progress: two universe companies have someone mapped" "2" "$(rj '.progress.companiesCumulative | last')"
check 19.17 "progress: all five were identified today" "5" "$(rj '.progress.daily | add')"
check 19.18 "progress: the last one was mapped zero days ago" "0" "$(rj '.progress.daysSinceLastExecutive')"

check 19.20 "market: the level axis is the seniority ladder" "Board,C-Suite,N-1,N-2,N-3" "$(rj '.market.levels | join(",")')"
check 19.21 "market: two sectors carry executives" "2" "$(rj '.market.sectors | length')"
check 19.22 "market: the executive at the declined company has no sector in the universe" "1" \
  "$(rj '.market.withoutSector')"
check 19.23 "market: every executive has a level" "0" "$(rj '.market.withoutSeniority')"
check 19.24 "market: hubs are where the executives are — three located, two not" "3/2" \
  "$(rj '"\([.market.hubs[].count] | add)/\(.market.unlocated)"')"

check 19.30 "remuneration is stated in the brief's currency" "${BRIEF_CCY:-null}" "$(rj '.remuneration.currency')"
if [ -n "$BRIEF_CCY" ]; then
  check 19.31 "a package in another currency is counted, not converted" "2/1" \
    "$(rj '"\(.remuneration.disclosures | length)/\(.remuneration.otherCurrency)"')"
  check 19.32 "fixed is base plus allowances, the package adds the bonus" "1100000/1300000" \
    "$(rj '.remuneration.disclosures[] | select(.fullName == "Fatima Al Mansoori") | "\(.fixed)/\(.totalPackage)"')"
  check 19.33 "no disclosure is drawn for the package in the other currency" "0" \
    "$(rj '[.remuneration.disclosures[] | select(.fullName == "Rana Haddad")] | length')"
else
  skip 19.31 "a package in another currency is counted, not converted" "the brief states no currency"
fi
check 19.34 "an executive with no base salary is not a disclosure at all" "0" \
  "$(rj '[.remuneration.disclosures[] | select(.fullName == "James Whitfield")] | length')"

check 19.40 "diversity: one executive has no gender on file" "1" "$(rj '.diversity.genderUnrecorded')"
check 19.41 "diversity: the gender split covers only the four recorded" "4" \
  "$(rj '([.diversity.genderByLevel[] | .female + .male + .other] | add) + .diversity.genderWithoutLevel.female
         + .diversity.genderWithoutLevel.male + .diversity.genderWithoutLevel.other')"
check 19.42 "diversity: C-Suite is two women and one other" "2/0/1" \
  "$(rj '.diversity.genderByLevel[] | select(.level == "C-Suite") | "\(.female)/\(.male)/\(.other)"')"
check 19.43 "diversity: N-1 is one man — the unrecorded one is not counted as anything" "0/1/0" \
  "$(rj '.diversity.genderByLevel[] | select(.level == "N-1") | "\(.female)/\(.male)/\(.other)"')"
check 19.44 "diversity: \"Egyptian\" is folded into its group at read time" "2" \
  "$(rj '.diversity.nationalities[] | select(.nationality == "Arab expat, non-GCC") | .total')"
check 19.45 "…and never rewritten on the row" "Egyptian" \
  "$(sql "SELECT nationality FROM app_lm_project_candidate WHERE id = '$KARIM'")"
check 19.46 "diversity: British counts as Western expat" "1" \
  "$(rj '.diversity.nationalities[] | select(.nationality == "Western expat") | .total')"
check 19.47 "diversity: the Gulf nationals are Emirati and Saudi" "2" "$(rj '.diversity.gccNationals')"
check 19.48 "diversity: the GCC groups are flagged as such" "Emirati:true,Saudi:true" \
  "$(rj '[.diversity.nationalities[] | select(.nationality == "Emirati" or .nationality == "Saudi")
          | "\(.nationality):\(.gcc)"] | sort | join(",")')"
check 19.49 "diversity: nobody's nationality is unknown" "0" "$(rj '.diversity.unknownNationality')"

get "/projects/$PROJECT/report" -H "$REP"
check_status 19.50 "a client seat may read the report (WORK_VIEW)" 200
get "/projects/$PROJECT/report" -H "$OUTSIDER"
check_code 19.51 "another tenant's admin gets not-found" 404 NOT_FOUND

section "19.3  researcher performance — staff only, attributed to whoever filed each executive"

TODAY=$(date -u +%F)
get "/projects/$PROJECT/report/team?from=$TODAY&to=$TODAY" -H "$LEAD"
check_status 19.60 "the lead reads the team breakdown for today" 200
check 19.61 "the range is today, one day long" "$TODAY/$TODAY/1" "$(json '"\(.from)/\(.to)/\(.days)"')"
check 19.62 "all five executives fall in range" "5" "$(json '.kpis.executivesInRange')"
check 19.63 "the lead filed three, as LEAD, 60%" "3/LEAD/60" \
  "$(json '.researchers[] | select(.name == "'"$LEAD_NAME"'") | "\(.executives)/\(.role)/\(.sharePct)"')"
check 19.64 "the researcher filed two, as RESEARCHER, 40%" "2/RESEARCHER/40" \
  "$(json '.researchers[] | select(.name == "'"$RESEARCHER_NAME"'") | "\(.executives)/\(.role)/\(.sharePct)"')"
check 19.65 "the client seat is not a researcher" "2" "$(json '.researchers | length')"
check 19.66 "the latest executive is credited to who filed it" "$RESEARCHER_NAME" "$(json '.kpis.lastAddedBy')"
check 19.67 "a company is credited to whoever filed its first executive" "$LEAD_NAME:2" \
  "$(json '[.coverage[] | "\(.name):\(.companies)"] | join(",")')"
check 19.68 "coverage is over the universe: the declined company's executive covers nothing" "2" \
  "$(json '.kpis.coveredCompanies')"

get "/projects/$PROJECT/report/team" -H "$LEAD"
check_status 19.69 "both ends are optional" 200
get "/projects/$PROJECT/report/team?from=$TODAY&to=2000-01-01" -H "$LEAD"
check_code 19.70 "a start after the end is refused" 400 VALIDATION_FAILED
get "/projects/$PROJECT/report/team?from=26-09-2026" -H "$LEAD"
check_code 19.71 "a date not in ISO form is refused, not a 500" 400 VALIDATION_FAILED
get "/projects/$PROJECT/report/team" -H "$RESEARCHER"
check_status 19.72 "a seated researcher may read it (WORK_EXECUTE)" 200
get "/projects/$PROJECT/report/team" -H "$REP"
check_code 19.73 "a client seat never sees the firm's people ranked" 403 FORBIDDEN
get "/projects/$PROJECT/report/team" -H "$OUTSIDER"
check_code 19.74 "another tenant's admin gets not-found" 404 NOT_FOUND

# --- the talent map ---------------------------------------------------------------

section "19.4  the talent map"

get /talent-map/config -H "$REP"
check_status 19.80 "the map's config is readable by any signed-in seat, a pure client included" 200
MAP_ENABLED=$(json '.enabled')
if [ "$MAP_ENABLED" = "false" ]; then
  check 19.81 "with no Mapbox token the map is not offered, and no token is handed out" "false/null" \
    "$(json '"\(.enabled)/\(.publicToken)"')"
else
  skip 19.81 "with no Mapbox token the map is not offered" "this stack has a Mapbox public token configured"
fi

get "/projects/$PROJECT/talent-map" -H "$LEAD"
check_status 19.82 "the In universe stage reads as a map" 200
check 19.83 "…its two companies" "2/2" "$(json '"\(.companies | length)/\(.totalCompanies)"')"
check 19.84 "…the people at them, while the total counts the whole mandate" "2/5" \
  "$(json '"\(.candidates | length)/\(.totalCandidates)"')"
check 19.85 "…and a locations map beside them" "object" "$(json '.locations | type')"
if [ "$MAP_ENABLED" = "false" ]; then
  check 19.86 "with geocoding off nothing is asked, so nothing is pending" "0" "$(json '.geocodingPending')"
  if [ "$(sql "SELECT count(*) FROM app_lm_geocoded_place")" = "0" ]; then
    check 19.87 "…and with an empty cache no point is invented" "0" "$(json '.locations | length')"
  else
    skip 19.87 "with an empty cache no point is invented" "app_lm_geocoded_place is not empty on this database"
  fi
else
  skip 19.86 "with geocoding off nothing is pending" "Mapbox is configured on this stack"
fi

get "/projects/$PROJECT/talent-map?status=shortlisted" -H "$LEAD"
check 19.88 "the Shortlisted stage is its own map: one company, its two people" "1/2" \
  "$(json '"\(.companies | length)/\(.candidates | length)"')"
get "/projects/$PROJECT/talent-map?status=declined" -H "$LEAD"
check 19.89 "the Declined stage carries the declined company and its executive" "Delta Contracting/1" \
  "$(json '"\(.companies[0].companyName)/\(.candidates | length)"')"
get "/projects/$PROJECT/talent-map?status=nowhere" -H "$LEAD"
check_code 19.90 "an unknown stage is refused" 400 VALIDATION_FAILED

get "/projects/$PROJECT/talent-map/locations" -H "$LEAD"
check_status 19.91 "the points-only read answers for the poll" 200
check 19.92 "…with the same two keys and no rows" "geocodingPending,locations" "$(json 'keys | join(",")')"

get "/projects/$PROJECT/talent-map" -H "$REP"
check_status 19.93 "a client seat may read the map (WORK_VIEW)" 200
get "/projects/$PROJECT/talent-map" -H "$OUTSIDER"
check_code 19.94 "another tenant's admin gets not-found" 404 NOT_FOUND

# --- recent activity -----------------------------------------------------------------

section "19.5  recent activity — an allowlisted, cursor-paged read of the audit trail"

get "/projects/$PROJECT/activity?limit=1" -H "$LEAD"
check_status 19.100 "the lead reads the newest line" 200
check 19.101 "one line, and a cursor to the next" "1/true" \
  "$(json '"\(.entries | length)/\(.nextCursor != null)"')"
check 19.102 "the newest is the status move, phrased with its status and actor" "CANDIDATE_UPDATED/engaged/$LEAD_NAME" \
  "$(json '.entries[0] | "\(.type)/\(.details.status)/\(.actorName)"')"
CURSOR=$(json '.nextCursor')
check 19.103 "the cursor is that line's own id" "$(json '.entries[0].id')" "$CURSOR"

get "/projects/$PROJECT/activity?limit=1&before=$CURSOR" -H "$LEAD"
check 19.104 "the next page is strictly older" "true" "$(json '.entries[0].id < '"$CURSOR")"
check 19.105 "…the last executive filed, by the researcher" "CANDIDATE_ADDED/$RESEARCHER_NAME" \
  "$(json '.entries[0] | "\(.type)/\(.actorName)"')"

# Walk the whole trail two lines at a time, and compare with one page holding all of it.
PAGED=""
CURSOR=""
for page in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  get "/projects/$PROJECT/activity?limit=2${CURSOR:+&before=$CURSOR}" -H "$LEAD"
  PAGED="$PAGED$(json '[.entries[].id | tostring] | join(",")'),"
  CURSOR=$(json '.nextCursor // empty')
  [ -z "$CURSOR" ] && break
done
get "/projects/$PROJECT/activity?limit=50" -H "$LEAD"
WHOLE="$LAST_BODY"
check 19.106 "paging by cursor yields exactly the lines one page holds" \
  "$(printf '%s' "$WHOLE" | jq -r '[.entries[].id | tostring] | join(",")')" \
  "$(printf '%s' "$PAGED" | tr -s ',' | sed 's/^,//; s/,$//')"
check 19.107 "every company typed in is a line" "4" \
  "$(printf '%s' "$WHOLE" | jq '[.entries[] | select(.type == "TRIAGE_COMPANY_CAPTURED")] | length')"
check 19.108 "every executive filed is a line" "5" \
  "$(printf '%s' "$WHOLE" | jq '[.entries[] | select(.type == "CANDIDATE_ADDED")] | length')"
check 19.109 "the mandate's creation is a line" "1" \
  "$(printf '%s' "$WHOLE" | jq '[.entries[] | select(.type == "PROJECT_CREATED")] | length')"
check 19.110 "seating the researcher is on the audit trail" "true" \
  "$([ "$(sql "SELECT count(*) FROM app_lm_audit_event WHERE target_id = '$PROJECT' AND event_type = 'PROJECT_TEAM_CHANGED'")" -ge 1 ] \
     && echo true || echo false)"
check 19.111 "…but team changes are not progress, so the allowlist keeps them off the panel" "0" \
  "$(printf '%s' "$WHOLE" | jq '[.entries[] | select(.type == "PROJECT_TEAM_CHANGED")] | length')"
check 19.112 "only allowlisted detail keys reach the panel — never an IP or a candidate id" "0" \
  "$(printf '%s' "$WHOLE" | jq '[.entries[].details | keys[] | select(. == "candidateId" or . == "ip" or . == "triageCompanyId")] | length')"

get "/projects/$PROJECT/activity?limit=0" -H "$LEAD"
check 19.113 "a limit below one is clamped to one, not refused" "1" "$(json '.entries | length')"
get "/projects/$PROJECT/activity" -H "$RESEARCHER"
check_status 19.114 "a seated researcher may read it" 200
get "/projects/$PROJECT/activity" -H "$REP"
check_code 19.115 "a client seat never sees the firm's activity" 403 FORBIDDEN
get "/projects/$PROJECT/activity" -H "$OUTSIDER"
check_code 19.116 "another tenant's admin gets not-found" 404 NOT_FOUND

# --- the projects list ----------------------------------------------------------------

section "19.6  the projects list's counts"

get /projects -H "$LEAD"
check_status 19.120 "the lead lists the workspace's mandates" 200
ROW=$(json '.[] | select(.id == "'"$PROJECT"'")')
pj() { printf '%s' "$ROW" | jq -r "$1"; }
check 19.121 "companies is the live universe: declined left out" "3" "$(pj '.companies')"
check 19.122 "mappedCompanies counts live companies with someone mapped — not the declined one" "2" \
  "$(pj '.mappedCompanies')"
check 19.123 "every executive ever mapped" "5" "$(pj '.mappedCandidates')"
check 19.124 "the candidates still in the running" "5" "$(pj '.candidates')"
check 19.125 "the engaged ones" "1" "$(pj '.engagedCandidates')"

get /projects -H "$REP"
check 19.126 "the representative's list holds exactly the one mandate they are attached to" "$PROJECT" \
  "$(json '[.[].id] | join(",")')"

# --- Settings → Templates -------------------------------------------------------------

section "19.7  Settings → Templates — the firm's own role templates"

TEMPLATES=/workspace/position-templates

get "$TEMPLATES" -H "$RESEARCHER"
check_code 19.130 "a workspace MEMBER cannot open the firm's templates" 403 FORBIDDEN
get "$TEMPLATES" -H "$REP"
check_code 19.131 "nor can a client" 403 FORBIDDEN

get "$TEMPLATES" -H "$LEAD"
check_status 19.132 "the admin lists them" 200
check 19.133 "a fresh firm sees only the shared library" "LIBRARY" "$(json '[.[].origin] | unique | join(",")')"
LIB_CODE=$(json '[.[] | select(.origin == "LIBRARY" and .fallback == false)][0].code')
LIB2_CODE=$(json '[.[] | select(.origin == "LIBRARY" and .fallback == false)][1].code')
FALLBACK_CODE=$(json '[.[] | select(.fallback == true)][0].code // empty')
note 19.134 "library templates: $(json 'length'); hiding $LIB_CODE, customising $LIB2_CODE"

get "$TEMPLATES/$LIB_CODE" -H "$LEAD"
check_status 19.135 "a library template opens with its body" 200
check 19.136 "…read as the library's" "LIBRARY/true" "$(json '"\(.origin)/\(.body != null)"')"
LIB_DETAIL="$LAST_BODY"

OWN_TITLE="E2E Head of Sustainability"
OWN_CODE="e2e-head-of-sustainability"
OWN_BODY=$(printf '%s' "$LIB_DETAIL" | jq -c --arg t "$OWN_TITLE" \
  '{title:$t, discipline, seniority, summary, keywords, body}')
post_json "$TEMPLATES" "$OWN_BODY" -H "$LEAD"
check_status 19.137 "the admin adds a template of the firm's own" 201
check 19.138 "…its code is slugged from the title, and it is the firm's own" "$OWN_CODE/OWN" \
  "$(json '"\(.code)/\(.origin)"')"
OWN_VERSION=$(json '.version')
post_json "$TEMPLATES" "$OWN_BODY" -H "$RESEARCHER"
check_code 19.139 "a MEMBER cannot add one" 403 FORBIDDEN

get "$TEMPLATES/$OWN_CODE" -H "$LEAD"
check 19.140 "it reads back by its code" "$OWN_TITLE" "$(json '.title')"

http PUT "$TEMPLATES/$OWN_CODE" -H 'Content-Type: application/json' -H "$LEAD" \
  -d "$(printf '%s' "$OWN_BODY" | jq -c --argjson v "$OWN_VERSION" '. + {summary:"Edited by the e2e matrix", version:$v}')"
check_status 19.141 "the admin edits it at the version they read" 200
check 19.142 "…and the edit is kept" "Edited by the e2e matrix" "$(json '.summary')"
http PUT "$TEMPLATES/$OWN_CODE" -H 'Content-Type: application/json' -H "$LEAD" \
  -d "$(printf '%s' "$OWN_BODY" | jq -c --argjson v "$OWN_VERSION" '. + {summary:"A stale write", version:$v}')"
check_code 19.143 "a write at the version before that edit is refused as stale" 409 TEMPLATE_STALE

hide() { # hide CODE true|false
  http PATCH "$TEMPLATES/$1/hidden" -H 'Content-Type: application/json' -d "{\"hidden\":$2}" -H "$LEAD"
}
hide "$LIB_CODE" true
check_status 19.144 "the admin hides a library template from the firm's picker" 200
check 19.145 "…which now reads as hidden" "HIDDEN" "$(json '.origin')"
get "$TEMPLATES" -H "$LEAD"
check 19.146 "…and is still listed, marked hidden, so it can be shown again" "HIDDEN" \
  "$(json '.[] | select(.code == "'"$LIB_CODE"'") | .origin')"
check 19.147 "hiding is recorded for the firm" "1" \
  "$(sql "SELECT count(*) FROM app_lm_position_template_hidden WHERE code = '$LIB_CODE'
          AND workspace_id = (SELECT workspace_id FROM app_lm_project WHERE id = '$PROJECT')")"
hide "$LIB_CODE" false
check 19.148 "…and showing it again restores it" "LIBRARY" "$(json '.origin')"
hide "$OWN_CODE" true
check_code 19.149 "only a library template can be hidden — the firm deletes its own" 404 NOT_FOUND
if [ -n "$FALLBACK_CODE" ]; then
  hide "$FALLBACK_CODE" true
  check_code 19.150 "the generic fallback cannot be hidden" 409 TEMPLATE_FALLBACK_REQUIRED
else
  skip 19.150 "the generic fallback cannot be hidden" "the library lists no fallback template"
fi

get "$TEMPLATES/$LIB2_CODE" -H "$LEAD"
LIB2_DETAIL="$LAST_BODY"
http PUT "$TEMPLATES/$LIB2_CODE" -H 'Content-Type: application/json' -H "$LEAD" \
  -d "$(printf '%s' "$LIB2_DETAIL" | jq -c '{title, discipline, seniority, summary:"The firm reading of it", keywords, body, version}')"
check_status 19.151 "saving a library template takes the firm's own copy" 200
check 19.152 "…which reads as customised" "CUSTOMISED" "$(json '.origin')"
LIB2_COPY_VERSION=$(json '.version')
hide "$LIB2_CODE" true
check_code 19.153 "a customised template must be reset before it can be hidden" 409 CONFLICT
http DELETE "$TEMPLATES/$LIB2_CODE?version=$LIB2_COPY_VERSION" -H "$LEAD"
check_status 19.154 "resetting the firm's copy" 204
get "$TEMPLATES/$LIB2_CODE" -H "$LEAD"
check 19.155 "…brings the library's own back" "LIBRARY" "$(json '.origin')"

section "19.8  Settings → Templates — export, schema, import"

get "$TEMPLATES/export" -H "$LEAD"
check_status 19.160 "the admin exports the firm's templates" 200
check_contains 19.161 "…as a JSON attachment" "lightmove-position-templates.json" "$(header 'content-disposition')"
check 19.162 "…carrying the format marker the importer checks" "lightmove.position-templates/1" \
  "$(json '"\(.format)/\(.formatVersion)"')"
check 19.163 "…and the firm's own template" "1" "$(json '[.templates[] | select(.code == "'"$OWN_CODE"'")] | length')"
EXPORTED="$RUN_DIR/19-templates.json"
printf '%s' "$LAST_BODY" > "$EXPORTED"
get "$TEMPLATES/export" -H "$RESEARCHER"
check_code 19.164 "a MEMBER cannot export them" 403 FORBIDDEN

get "$TEMPLATES/schema" -H "$LEAD"
check_status 19.165 "the published schema downloads" 200
check 19.166 "…and is a JSON document" "object" "$(json 'type')"

import_as() { # import_as AUTH preview|commit FILE
  http POST "$TEMPLATES/import/$2" -H "$1" -F "file=@$3;type=application/json"
}

import_as "$LEAD" preview "$EXPORTED"
check_status 19.170 "the export previews back in" 200
check 19.171 "…nothing is committed by a preview" "false" "$(json '.committed')"
check 19.172 "…every template in it is readable" "0" "$(json '[.rows[] | select(.action == "INVALID")] | length')"
check 19.173 "…and the firm's own, untouched, would be left unchanged" "UNCHANGED" \
  "$(json '.rows[] | select(.code == "'"$OWN_CODE"'") | .action')"

EDITED="$RUN_DIR/19-templates-edited.json"
jq --arg own "$OWN_CODE" '.templates |= [
    (.[] | select(.code == $own) | .title = "E2E Head of Sustainability, revised"),
    (.[] | select(.code == $own) | .code = "e2e-imported-template" | .title = "E2E Imported Template")
  ]' "$EXPORTED" > "$EDITED"
import_as "$LEAD" preview "$EDITED"
check 19.174 "an edited file previews as an update and a create" "$OWN_CODE:UPDATE,e2e-imported-template:CREATE" \
  "$(json '[.rows[] | "\(.code):\(.action)"] | join(",")')"
get "$TEMPLATES/e2e-imported-template" -H "$LEAD"
check_code 19.175 "…and the preview wrote nothing" 404 NOT_FOUND

import_as "$RESEARCHER" commit "$EDITED"
check_code 19.176 "a MEMBER cannot import" 403 FORBIDDEN
import_as "$LEAD" commit "$EDITED"
check_status 19.177 "the admin commits it" 200
check 19.178 "…it says it committed" "true" "$(json '.committed')"
get "$TEMPLATES/e2e-imported-template" -H "$LEAD"
check 19.179 "the new template exists as the firm's own" "OWN/E2E Imported Template" "$(json '"\(.origin)/\(.title)"')"
get "$TEMPLATES/$OWN_CODE" -H "$LEAD"
check 19.180 "the existing one took the file's title" "E2E Head of Sustainability, revised" "$(json '.title')"

INVALID="$RUN_DIR/19-templates-invalid.json"
jq '.templates |= [.[0] | .code = "Not A Valid Code!"]' "$EDITED" > "$INVALID"
import_as "$LEAD" preview "$INVALID"
check 19.181 "a template with an unusable code previews as invalid, naming the field" "INVALID/code" \
  "$(json '.rows[0] | "\(.action)/\(.problems[0].field)"')"
import_as "$LEAD" commit "$INVALID"
check_code 19.182 "…and a file with an invalid template is refused whole" 400 TEMPLATE_IMPORT_INVALID

NOT_JSON="$RUN_DIR/19-templates-not-json.json"
printf 'this is not json' > "$NOT_JSON"
import_as "$LEAD" preview "$NOT_JSON"
check_code 19.183 "a file that is not JSON is unreadable" 400 TEMPLATE_FILE_UNREADABLE

summary
