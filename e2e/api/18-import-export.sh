#!/usr/bin/env bash
# The spreadsheet in both directions: importing a CSV into a mandate's Companies grid, and exporting a
# stage of that grid back out as a file.
#
# No AI. The e2e profile runs with the model off, and that is exactly what the import is designed to
# survive: a file whose every header is a known spelling maps with no model call at all
# (`exactHeaders`), and a file carrying one header nobody knows falls back to the synonym matcher
# (`headerMatcher`) rather than failing. Both paths are asserted — the second is the proof the AI-off
# fallback works, and its unknown header becomes a per-project custom column that the export then
# carries.
#
# Builds its own cast rather than sourcing cast.env — a lead, a researcher seated on the mandate, a
# client representative attached to it, and an admin of a second workspace — so every count below is
# this script's own and it can run at any point in the matrix. Needs no Apollo universe: an imported
# row is never resolved against the market.
#
# Not exercised: the lightmove.export.* ceilings (5,000 companies / 10,000 executives by default).
# Past them an export is refused whole rather than truncated, but reaching one means seeding five
# thousand rows, which this matrix has no business doing.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "18 — spreadsheet import and Companies export"

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

user_id_of() { sql "SELECT id FROM app_lm_user WHERE email = '$1'"; }

# The last response as plain CSV lines: the export opens with a UTF-8 BOM (for Excel) and ends every
# line with CRLF. The BOM is stripped with a literal byte string so this stays BSD-sed compatible.
csv_body()     { printf '%s\n' "$LAST_BODY" | LC_ALL=C sed $'1s/^\xef\xbb\xbf//' | tr -d '\r'; }
csv_header()   { csv_body | head -1; }
csv_rows()     { csv_body | sed 1d | grep -c . ; }
csv_line_for() { csv_body | grep -F "$1" | head -1; }
# No value this script writes carries a comma or a quote, so a plain split is exact here.
csv_field()    { printf '%s' "$1" | awk -F, -v n="$2" '{print $n}'; }

preview_as() { # preview_as AUTH FILE [CONTENT_TYPE]
  http POST "/projects/$PROJECT/import/preview" -H "$1" -F "file=@$2;type=${3:-text/csv}"
}
commit_as() { # commit_as AUTH FILE MAPPING_JSON_FILE
  http POST "/projects/$PROJECT/import/commit" -H "$1" \
    -F "file=@$2;type=text/csv" -F "mapping=<$3;type=application/json"
}
# The mapping step's confirmation, unedited: exactly what the preview proposed.
confirm_mapping() { # confirm_mapping OUT_FILE   (from the last preview response)
  json '{columns: [.columns[].mapping]}' > "$1"
}

candidates_json() { get "/projects/$PROJECT/candidates" -H "$LEAD"; printf '%s' "$LAST_BODY"; }
candidate_field() { # candidate_field CANDIDATES_JSON FULL_NAME JQ_PATH
  printf '%s' "$1" | jq -r --arg n "$2" ".candidates[] | select(.fullName == \$n) | $3"
}

export_as() { # export_as AUTH QUERY
  get "/projects/$PROJECT/export/companies?$2" -H "$1"
}

# --- the cast -------------------------------------------------------------------

LEAD_EMAIL=$(new_email implead)
RESEARCHER_EMAIL=$(new_email impresearcher)
REP_EMAIL=$(new_email imprep)
OUTSIDER_EMAIL=$(new_email impoutsider)

LEAD_TOKEN=$(signup_verified "$LEAD_EMAIL" "Ivy Importer")
make_workspace "$LEAD_TOKEN" "Import UAT $(date +%s)$RANDOM"
# A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
LEAD="$(auth_header "$(login_as "$LEAD_EMAIL")")"

post_json /invitations "$(jq -nc --arg e "$RESEARCHER_EMAIL" '[{email:$e, role:"MEMBER"}]')" -H "$LEAD" >/dev/null
accept_invite_signup "$RESEARCHER_EMAIL" "Rex Researcher"
RESEARCHER="$(auth_header "$(login_as "$RESEARCHER_EMAIL")")"

post_json /clients '{"customName":"Import Holding","customDomain":"importholding.example"}' -H "$LEAD" >/dev/null
CLIENT_ID=$(json '.id')
post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Chief Financial Officer"}')" -H "$LEAD"
check_status 18.0 "the lead creates the mandate" 201
PROJECT=$(json '.id')

http PUT "/projects/$PROJECT/members/$(member_id_of "$RESEARCHER_EMAIL")" \
  -H 'Content-Type: application/json' -d '{"role":"RESEARCHER"}' -H "$LEAD"
check_status 18.0a "…and seats a colleague on it as a RESEARCHER" 200

post_json "/projects/$PROJECT/representatives/invitations" \
  "$(jq -nc --arg e "$REP_EMAIL" '{fullName:"Cora Client", position:"Group CFO", email:$e}')" -H "$LEAD" >/dev/null
accept_invite_signup "$REP_EMAIL" "Cora Client"
REP="$(auth_header "$(login_as "$REP_EMAIL")")"
get "/projects/$PROJECT/triage" -H "$REP"
check_status 18.0b "…and a client representative holds a read seat on it" 200

OUTSIDER_TOKEN=$(signup_verified "$OUTSIDER_EMAIL" "Otis Outsider")
make_workspace "$OUTSIDER_TOKEN" "Import Rival $(date +%s)$RANDOM"
OUTSIDER="$(auth_header "$(login_as "$OUTSIDER_EMAIL")")"

# --- the template ---------------------------------------------------------------

section "18.1  the downloadable template"

TEMPLATE_HEADER='Company,Sector,Country,City,Employees,Website,Name,Title,Level,Email,Phone,LinkedIn'

get "/projects/$PROJECT/import/template" -H "$LEAD"
check_status 18.1 "the lead downloads the import template" 200
check_contains 18.2 "…served as text/csv" "text/csv" "$(header 'content-type')"
check_contains 18.3 "…as an attachment named for the template" "lightmove-import-template.csv" \
  "$(header 'content-disposition')"
DOWNLOADED_HEADER=$(printf '%s' "$LAST_BODY" | tr -d '\r' | head -1)
check 18.4 "…whose header row is the dozen common fields, in row order" "$TEMPLATE_HEADER" "$DOWNLOADED_HEADER"
check_contains 18.5 "…with one example row beneath it" "ACWA Power" "$(printf '%s' "$LAST_BODY" | tr -d '\r' | sed -n 2p)"

get "/projects/$PROJECT/import/template" -H "$REP"
check_code 18.6 "a client seat cannot download it (WORK_EXECUTE)" 403 FORBIDDEN
get "/projects/$PROJECT/import/template" -H "$OUTSIDER"
check_code 18.7 "another tenant's admin gets not-found, never a template" 404 NOT_FOUND

# --- file A: every header a known spelling ------------------------------------------
#
# The downloaded header row verbatim, plus six more headers that are each an exact synonym in
# ImportTargetField — so HeuristicColumnMatcher is certain of every column and the model is never
# asked. Five rows: two people at one company, one at another, one at no company, and a company with
# nobody. "negotiable" is a notice period RowValues.noticePeriod cannot fold onto one of the five, and
# "90 days" is one it can.

FILE_A="$RUN_DIR/18-people.csv"
{
  printf '%s,Company LinkedIn,Nationality,Gender,Base salary,Currency,Notice period\n' "$DOWNLOADED_HEADER"
  printf '%s\n' \
    'Falcon Logistics,Logistics,United Arab Emirates,Dubai,1200,https://falcon.example,Aisha Rahman,Chief Financial Officer,C-Suite,aisha.rahman@falcon.example,+971 50 111 2233,https://www.linkedin.com/in/aisha-rahman-e2e,https://www.linkedin.com/company/falcon-logistics,Egyptian,Female,650000,AED,3 months' \
    'Falcon Logistics,Logistics,United Arab Emirates,Dubai,1200,https://falcon.example,Omar Siddiqui,Chief Operating Officer,C-Suite,omar.siddiqui@falcon.example,+971 50 444 5566,https://www.linkedin.com/in/omar-siddiqui-e2e,https://www.linkedin.com/company/falcon-logistics,Indian,M,600000,AED,negotiable' \
    'Dune Energy,Oil & Energy,Saudi Arabia,Riyadh,3000,https://dune.example,Layla Haddad,Group CFO,N-1,layla.haddad@dune.example,+966 55 777 8899,https://www.linkedin.com/in/layla-haddad-e2e,,British,F,900000,SAR,90 days' \
    ',,,,,,Unplaced Person,Board Advisor,,unplaced.person@elsewhere.example,,,,,,,,' \
    'Oasis Foods,Food & Beverages,Qatar,Doha,450,https://oasis.example,,,,,,,,,,,,'
} > "$FILE_A"
MAPPING_A="$RUN_DIR/18-people.mapping.json"

section "18.2  preview — a file built from the template needs no model"

preview_as "$LEAD" "$FILE_A"
check_status 18.10 "the lead previews the file" 200
check 18.11 "every header is a known spelling, so the mapping source is exactHeaders" "exactHeaders" \
  "$(json '.mappingSource')"
check 18.12 "…every data row is counted" "5" "$(json '.rowCount')"
check 18.13 "…every column is read" "18" "$(json '.columns | length')"
check 18.14 "…and every one lands on a built-in field, none on a custom column" "18" \
  "$(json '[.columns[].mapping | select(.targetField != null and .customLabel == null)] | length')"
check 18.15 "Company maps onto the company name" "companyName" \
  "$(json '.columns[] | select(.header == "Company") | .mapping.targetField')"
check 18.16 "LinkedIn is the person's, Company LinkedIn the employer's" "candidateLinkedin/companyLinkedin" \
  "$(json '[(.columns[] | select(.header == "LinkedIn") | .mapping.targetField),
             (.columns[] | select(.header == "Company LinkedIn") | .mapping.targetField)] | join("/")')"
check 18.17 "Notice period maps onto the executive's notice period" "candidateNoticePeriod" \
  "$(json '.columns[] | select(.header == "Notice period") | .mapping.targetField')"
check 18.18 "the dropdown vocabulary travels with the preview" "true" \
  "$(json '[.availableFields[].value] | index("candidateEmail") != null')"
confirm_mapping "$MAPPING_A"
check 18.19 "…and nothing was written by previewing" "0" \
  "$(sql "SELECT count(*) FROM app_lm_project_triage_company WHERE project_id = '$PROJECT'")"

preview_as "$RESEARCHER" "$FILE_A"
check_status 18.20 "a seated researcher may preview too (WORK_EXECUTE)" 200
preview_as "$REP" "$FILE_A"
check_code 18.21 "a client seat may not preview an import" 403 FORBIDDEN
preview_as "$OUTSIDER" "$FILE_A"
check_code 18.22 "another tenant's admin gets not-found" 404 NOT_FOUND
preview_as "$LEAD" "$FILE_A" "image/png"
check_code 18.23 "a content type outside the allowed list is refused" 400 UNSUPPORTED_FILE_TYPE

section "18.3  commit — written through the ordinary doors, with source CSV"

commit_as "$REP" "$FILE_A" "$MAPPING_A"
check_code 18.30 "a client seat may not commit an import" 403 FORBIDDEN
check 18.31 "…and nothing was written" "0" \
  "$(sql "SELECT count(*) FROM app_lm_project_candidate WHERE project_id = '$PROJECT'")"

commit_as "$LEAD" "$FILE_A" "$MAPPING_A"
check_status 18.32 "the lead commits the confirmed mapping" 200
check 18.33 "all five rows were read" "5" "$(json '.rowsRead')"
# Falcon Logistics is on two rows: the first creates it, the second updates it in place.
check 18.34 "three companies created, the repeated one updated rather than duplicated" "3/1/0" \
  "$(json '"\(.companiesCreated)/\(.companiesUpdated)/\(.companiesSkipped)"')"
check 18.35 "four people created, none updated" "4/0" "$(json '"\(.candidatesCreated)/\(.candidatesUpdated)"')"
check 18.36 "no custom column was needed" "0" "$(json '.customColumnsCreated | length')"
check 18.37 "no row failed" "0" "$(json '.rowErrors | length')"

check 18.38 "every company landed In universe with source CSV" "3" \
  "$(sql "SELECT count(*) FROM app_lm_project_triage_company
          WHERE project_id = '$PROJECT' AND source = 'CSV' AND status = 'IN_UNIVERSE'")"
check 18.39 "…with no universe id, because an import is never resolved against the market" "0" \
  "$(sql "SELECT count(*) FROM app_lm_project_triage_company
          WHERE project_id = '$PROJECT' AND apollo_account_id IS NOT NULL")"
check 18.40 "every person landed with source CSV" "4" \
  "$(sql "SELECT count(*) FROM app_lm_project_candidate WHERE project_id = '$PROJECT' AND source = 'CSV'")"
check 18.41 "each address is a contact row that came through the CSV door" "4" \
  "$(sql "SELECT count(*) FROM app_lm_candidate_contact cc
            JOIN app_lm_project_candidate c ON c.id = cc.candidate_id
          WHERE c.project_id = '$PROJECT' AND cc.channel = 'EMAIL' AND cc.source = 'CSV'")"
check 18.42 "…and so is each phone" "3" \
  "$(sql "SELECT count(*) FROM app_lm_candidate_contact cc
            JOIN app_lm_project_candidate c ON c.id = cc.candidate_id
          WHERE c.project_id = '$PROJECT' AND cc.channel = 'PHONE' AND cc.source = 'CSV'")"
check 18.43 "the two Falcon people are mapped at the one Falcon row" "2" \
  "$(sql "SELECT count(*) FROM app_lm_project_candidate c
            JOIN app_lm_project_triage_company t ON t.id = c.triage_company_id
          WHERE c.project_id = '$PROJECT' AND t.company_name = 'Falcon Logistics'")"
check 18.44 "the person with no company is filed unmapped" "1" \
  "$(sql "SELECT count(*) FROM app_lm_project_candidate
          WHERE project_id = '$PROJECT' AND full_name = 'Unplaced Person' AND triage_company_id IS NULL")"
check 18.45 "the import is on the audit trail with its tally" "5/3/4" \
  "$(await_sql "SELECT metadata->>'rowsRead' || '/' || (metadata->>'companiesCreated') || '/' || (metadata->>'candidatesCreated')
                FROM app_lm_audit_event WHERE event_type = 'SPREADSHEET_IMPORTED' AND target_id = '$PROJECT'
                ORDER BY id DESC LIMIT 1" "5/3/4")"

PEOPLE=$(candidates_json)
check 18.46 "the API reads the imported person back as source csv" "csv" \
  "$(candidate_field "$PEOPLE" "Aisha Rahman" '.source')"
check 18.47 "gender spellings fold: Female, M, F" "female/male/female" \
  "$(printf '%s/%s/%s' "$(candidate_field "$PEOPLE" "Aisha Rahman" '.gender')" \
      "$(candidate_field "$PEOPLE" "Omar Siddiqui" '.gender')" "$(candidate_field "$PEOPLE" "Layla Haddad" '.gender')")"
check 18.48 "a nationality is stored as the file spelled it — folding into a group is the report's" "Egyptian" \
  "$(candidate_field "$PEOPLE" "Aisha Rahman" '.nationality')"
check 18.49 "seniority spellings fold onto the ladder" "C-Suite/N-1" \
  "$(candidate_field "$PEOPLE" "Aisha Rahman" '.seniority')/$(candidate_field "$PEOPLE" "Layla Haddad" '.seniority')"
check 18.50 "the package keeps its own currency" "AED 650000 / SAR 900000" \
  "$(candidate_field "$PEOPLE" "Aisha Rahman" '"\(.compensation.currency) \(.compensation.baseSalary)"') / $(candidate_field "$PEOPLE" "Layla Haddad" '"\(.compensation.currency) \(.compensation.baseSalary)"')"
check 18.51 "a notice period stated as one of the five is kept" "3 months" \
  "$(candidate_field "$PEOPLE" "Aisha Rahman" '.compensation.noticePeriod')"
check 18.52 "…and 90 days folds exactly onto 3 months" "3 months" \
  "$(candidate_field "$PEOPLE" "Layla Haddad" '.compensation.noticePeriod')"
check 18.53 "…but \"negotiable\" is not rounded to anything: nothing is written" "null" \
  "$(candidate_field "$PEOPLE" "Omar Siddiqui" '.compensation.noticePeriod')"

section "18.4  re-importing the same file matches people on their email"

# Give Omar a notice period by hand first, so the second import proves an unfoldable cell keeps what
# the row already holds rather than clearing it.
OMAR_ID=$(candidate_field "$PEOPLE" "Omar Siddiqui" '.id')
http PUT "/projects/$PROJECT/candidates/$OMAR_ID" -H 'Content-Type: application/json' -H "$LEAD" \
  -d "$(candidate_field "$PEOPLE" "Omar Siddiqui" '{triageCompanyId, fullName, title, seniority, status,
        employerName: .companyName, linkedinUrl, locationCountry, locationCity, nationality, gender,
        yearsExperience, summary, note, compensation: (.compensation + {noticePeriod: "1 month"}),
        career, languages, source, sourceUrl, customFields}')"
check_status 18.60 "a researcher records Omar's notice period by hand" 200

commit_as "$LEAD" "$FILE_A" "$MAPPING_A"
check_status 18.61 "the same file is committed a second time" 200
check 18.62 "…every person is matched and updated, none created" "0/4" \
  "$(json '"\(.candidatesCreated)/\(.candidatesUpdated)"')"
check 18.63 "…every company row updates the one already held" "0/4" \
  "$(json '"\(.companiesCreated)/\(.companiesUpdated)"')"
check 18.64 "the mandate still holds four people" "4" \
  "$(sql "SELECT count(*) FROM app_lm_project_candidate WHERE project_id = '$PROJECT'")"
check 18.65 "…and three companies" "3" \
  "$(sql "SELECT count(*) FROM app_lm_project_triage_company WHERE project_id = '$PROJECT'")"
check 18.66 "…and still one contact row per address" "4" \
  "$(sql "SELECT count(*) FROM app_lm_candidate_contact cc
            JOIN app_lm_project_candidate c ON c.id = cc.candidate_id
          WHERE c.project_id = '$PROJECT' AND cc.channel = 'EMAIL'")"
PEOPLE=$(candidates_json)
check 18.67 "\"negotiable\" leaves the hand-recorded notice period standing" "1 month" \
  "$(candidate_field "$PEOPLE" "Omar Siddiqui" '.compensation.noticePeriod')"

section "18.5  an unknown header — the model is off, so the header matcher answers"

# "Mandate Priority" shares no token with any synonym, so the matcher is not certain of it and the
# proposer has to ask the model. With the model off in e2e that call fails and the heuristic's own
# proposal stands — a new candidate-side text column — labelled headerMatcher rather than claiming a
# model mapped it.
FILE_B="$RUN_DIR/18-priority.csv"
{
  printf '%s,Mandate Priority\n' "$DOWNLOADED_HEADER"
  printf '%s\n' 'Mirage Hospitality,Hospitality,United Arab Emirates,Abu Dhabi,800,https://mirage.example,Nadia Karim,Chief Commercial Officer,C-Suite,nadia.karim@mirage.example,,https://www.linkedin.com/in/nadia-karim-e2e,High'
} > "$FILE_B"
MAPPING_B="$RUN_DIR/18-priority.mapping.json"

preview_as "$LEAD" "$FILE_B"
check_status 18.70 "a file with one unrecognised header previews" 200
check 18.71 "…the mapping says the header matcher produced it, not the model" "headerMatcher" \
  "$(json '.mappingSource')"
check 18.72 "…and proposes a new text column on the person for the unknown header" \
  "Mandate Priority/candidate/text/null" \
  "$(json '.columns[] | select(.header == "Mandate Priority") | .mapping
            | "\(.customLabel)/\(.customTarget)/\(.customType)/\(.customFieldKey)"')"
check 18.73 "…while every known header still maps as before" "companyName" \
  "$(json '.columns[0].mapping.targetField')"
confirm_mapping "$MAPPING_B"

commit_as "$LEAD" "$FILE_B" "$MAPPING_B"
check_status 18.74 "committing it" 200
check 18.75 "…defines the custom column" "Mandate Priority" "$(json '.customColumnsCreated | join(",")')"
check 18.76 "…as a row of the project's own columns" "1" \
  "$(sql "SELECT count(*) FROM app_lm_project_custom_column WHERE project_id = '$PROJECT' AND label = 'Mandate Priority'")"
PEOPLE=$(candidates_json)
check 18.77 "…and the person carries the value in it" "High" \
  "$(candidate_field "$PEOPLE" "Nadia Karim" '.customFields | to_entries | map(.value) | join(",")')"

preview_as "$LEAD" "$FILE_B"
check 18.78 "a second import of that shape needs no model: the column now exists" "exactHeaders" \
  "$(json '.mappingSource')"
check 18.79 "…and the header fills the existing column by its key" "true" \
  "$(json '.columns[] | select(.header == "Mandate Priority") | .mapping.customFieldKey != null')"

get "/projects/$PROJECT/import/template" -H "$LEAD"
check 18.80 "the template now carries the mandate's custom column" "$TEMPLATE_HEADER,Mandate Priority" \
  "$(printf '%s' "$LAST_BODY" | tr -d '\r' | head -1)"

# --- export -------------------------------------------------------------------------

section "18.6  export — the whole stage, as the grid draws it"

# One shortlisted company with one person, typed by hand, so the stage switch has something to find.
post_json "/projects/$PROJECT/triage/capture" \
  '{"companyName":"Sahara Ventures","status":"shortlisted","companyCountry":"Oman","companyCity":"Muscat"}' -H "$LEAD"
SAHARA_ID=$(json '.id')
post_json "/projects/$PROJECT/candidates" "$(jq -nc --arg c "$SAHARA_ID" \
  '{triageCompanyId:$c, fullName:"Sami Shortlisted", title:"Chief Executive Officer", source:"manual"}')" -H "$LEAD"
check_status 18.89 "a shortlisted company with one executive is typed in by hand" 201

EXPORT_HEADER='Company,Website,Company LinkedIn,Country,Executive,Title,Email,Phone,Status,Sector,City,Revenue,Employees,Note,Founded,Added,Description,Source,Mandate Priority'

export_as "$LEAD" "status=inUniverse"
check_status 18.90 "the lead exports the In universe stage" 200
check_contains 18.91 "…as text/csv" "text/csv" "$(header 'content-type')"
check_contains 18.92 "…downloaded as an attachment" "uncava-companies.csv" "$(header 'content-disposition')"
check 18.93 "the header is every grid column, Links spelled out, then the custom column" \
  "$EXPORT_HEADER" "$(csv_header)"
# Falcon x2 people, Dune x1, Oasis with nobody (its own line), Mirage x1, and the unmapped person
# appended because this is the universe stage with no company search.
check 18.94 "one line per person at a company, a line for a company with nobody, and the unmapped person" \
  "6" "$(csv_rows)"
AISHA_LINE=$(csv_line_for "Aisha Rahman")
check 18.95 "Website and Company LinkedIn carry the company's two links" \
  "https://falcon.example|https://www.linkedin.com/company/falcon-logistics" \
  "$(csv_field "$AISHA_LINE" 2)|$(csv_field "$AISHA_LINE" 3)"
check 18.96 "a phone opening with + is defused so a spreadsheet cannot read it as a formula" \
  "'+971 50 111 2233" "$(csv_field "$AISHA_LINE" 8)"
check 18.97 "the import's rows are labelled by their door" "Import" "$(csv_field "$AISHA_LINE" 18)"
check 18.98 "the custom column's value is on its person's line" "High" \
  "$(csv_field "$(csv_line_for "Nadia Karim")" 19)"
check 18.99 "the company with nobody keeps its own line" ",,," \
  "$(csv_line_for "Oasis Foods" | awk -F, '{print "," $5 "," $6 ","}')"
await_sql "SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'COMPANIES_EXPORTED' AND target_id = '$PROJECT'" "1" >/dev/null
check 18.100 "unlike any other read, the export is on the audit trail" "inUniverse/6/true/1" \
  "$(sql "SELECT (metadata->>'stage') || '/' || (metadata->>'rows') || '/' || (metadata->>'wholeStage') || '/' || (metadata->>'customColumns')
          FROM app_lm_audit_event WHERE event_type = 'COMPANIES_EXPORTED' AND target_id = '$PROJECT'
          ORDER BY id DESC LIMIT 1")"

export_as "$LEAD" "status=shortlisted"
check 18.101 "the Shortlisted stage is its own file, with no unmapped people appended" "1" "$(csv_rows)"
check_contains 18.102 "…holding the shortlisted company" "Sahara Ventures" "$(csv_body | sed -n 2p)"

export_as "$LEAD" "status=inUniverse&q=Falcon"
check 18.103 "a company-name filter narrows the file to that company's lines, unmapped people dropped" "2" \
  "$(csv_rows)"
check 18.104 "…and the audit event says it was not the whole stage" "2/false" \
  "$(await_sql "SELECT (metadata->>'rows') || '/' || (metadata->>'wholeStage') FROM app_lm_audit_event
                WHERE event_type = 'COMPANIES_EXPORTED' AND target_id = '$PROJECT' ORDER BY id DESC LIMIT 1" "2/false")"

export_as "$LEAD" "status=inUniverse&executiveQuery=Aisha"
check 18.105 "an executive-name filter keeps only the matching person, not their colleagues" "1" "$(csv_rows)"
check_contains 18.106 "…and it is her" "Aisha Rahman" "$(csv_body | sed -n 2p)"

export_as "$LEAD" "status=nowhere"
check_code 18.107 "an unknown stage is refused" 400 VALIDATION_FAILED

section "18.7  who may take the file"

export_as "$REP" "status=inUniverse"
check_status 18.110 "a client seat may export the mandate it can already read (WORK_VIEW)" 200
check 18.111 "…the same six lines" "6" "$(csv_rows)"
check 18.112 "…and it is recorded against the representative" "1" \
  "$(await_sql "SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'COMPANIES_EXPORTED'
                AND target_id = '$PROJECT' AND actor_user_id = '$(user_id_of "$REP_EMAIL")'" "1")"
export_as "$RESEARCHER" "status=inUniverse"
check_status 18.113 "a seated researcher may export" 200
export_as "$OUTSIDER" "status=inUniverse"
check_code 18.114 "another tenant's admin gets not-found, never the file" 404 NOT_FOUND
check 18.115 "…and no export was recorded for them" "0" \
  "$(sql "SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'COMPANIES_EXPORTED'
          AND target_id = '$PROJECT' AND actor_user_id = '$(user_id_of "$OUTSIDER_EMAIL")'")"
note 18.116 "not exercised: lightmove.export.max-companies/max-candidates — refused whole past the cap, never truncated"

summary
