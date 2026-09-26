#!/usr/bin/env bash
# The position brief: the New position modal's timeline, the brief a mandate is drafted with, each of
# its five steps written and read back, the role-template library, publishing, the attached position
# description, and which seat may do which of those.
#
# No AI anywhere: the document is attached and downloaded as bytes, never read. Every write here is a
# snapshot PUT of one step, deliberately lenient (autosave persists half-typed steps), so the refusals
# asserted are only the ones the server actually enforces — ranges, enum spellings, the org chart's
# shape — never a cross-field rule it does not have.
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
new_project() { # new_project BODY -> leaves the response in LAST_*
  post_json /projects "$1" -H "$LEAD_AUTH"
}

section "N16.1  the New position modal's timeline"

new_project "$(jq -nc --arg c "$CLIENT_ID" \
  '{clientId:$c, positionTitle:"Market Map Gulf CFOs", projectType:"MAPPING",
    startDate:"2027-01-01", deliveryDate:"2027-04-11"}')"
check_status N16.1.1 "a mapping mandate with a window is created" 201
check N16.1.2 "…typed MAPPING" "MAPPING" "$(json '.projectType')"
check N16.1.3 "…with the window it was given" "2027-01-01..2027-04-11" "$(json '.startDate')..$(json '.deliveryDate')"
check N16.1.4 "…and no mapping target: the map is the delivery" "null" "$(json '.mappingTargetDate')"

new_project "$(jq -nc --arg c "$CLIENT_ID" \
  '{clientId:$c, positionTitle:"Mapping With Target", projectType:"MAPPING",
    startDate:"2027-01-01", deliveryDate:"2027-04-11", mappingTargetDate:"2027-02-01"}')"
check_code N16.1.5 "a mapping mandate refuses a separate mapping target" 400 VALIDATION_FAILED
check N16.1.6 "…naming the field" "true" "$(json '.fieldErrors | has("mappingTargetDate")')"

# 100 days from 1 Jan; 60% of them is 60 days, which lands on 2 March.
new_project "$(jq -nc --arg c "$CLIENT_ID" \
  '{clientId:$c, positionTitle:"Search Defaulted Target", projectType:"SEARCH",
    startDate:"2027-01-01", deliveryDate:"2027-04-11"}')"
check_status N16.1.7 "a search with a window and no mapping target is created" 201
check N16.1.8 "…its mapping target defaulted to 60% of the window" "2027-03-02" "$(json '.mappingTargetDate')"

new_project "$(jq -nc --arg c "$CLIENT_ID" \
  '{clientId:$c, positionTitle:"Search Explicit Target", projectType:"SEARCH",
    startDate:"2027-01-01", deliveryDate:"2027-04-11", mappingTargetDate:"2027-02-15"}')"
check N16.1.9 "an explicit mapping target inside the window is kept" "2027-02-15" "$(json '.mappingTargetDate')"

new_project "$(jq -nc --arg c "$CLIENT_ID" \
  '{clientId:$c, positionTitle:"Search Target Outside", projectType:"SEARCH",
    startDate:"2027-01-01", deliveryDate:"2027-04-11", mappingTargetDate:"2027-05-01"}')"
check_code N16.1.10 "a mapping target after delivery is refused" 400 VALIDATION_FAILED

new_project "$(jq -nc --arg c "$CLIENT_ID" \
  '{clientId:$c, positionTitle:"Search Target No Window", projectType:"SEARCH", mappingTargetDate:"2027-02-15"}')"
check_code N16.1.11 "a mapping target with no window to fall inside is refused" 400 VALIDATION_FAILED

new_project "$(jq -nc --arg c "$CLIENT_ID" \
  '{clientId:$c, positionTitle:"Backwards Window", startDate:"2027-04-11", deliveryDate:"2027-01-01"}')"
check_code N16.1.12 "a delivery date before the start is refused" 400 VALIDATION_FAILED
check N16.1.13 "…on the delivery date" "true" "$(json '.fieldErrors | has("deliveryDate")')"

new_project "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Untyped Mandate"}')"
check N16.1.14 "a caller naming no type gets SEARCH" "SEARCH" "$(json '.projectType')"
check N16.1.15 "…and no window means no defaulted target" "null" "$(json '.mappingTargetDate')"

new_project "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Bad Type", projectType:"RETAINER"}')"
check_code N16.1.16 "an unknown project type is refused" 400 VALIDATION_FAILED

# --- the mandate the rest of the script works on --------------------------------

new_project "$(jq -nc --arg c "$CLIENT_ID" \
  '{clientId:$c, positionTitle:"Chief Financial Officer", projectType:"SEARCH",
    startDate:"2027-01-01", deliveryDate:"2027-04-11", targetDate:"2027-06-01"}')"
check_status N16.1.17 "the lead creates the CFO mandate" 201
P=$(json '.id')
BRIEF="/projects/$P/position"

http PUT "/projects/$P/members/$(member_id_of "$MEMBER2_EMAIL")" -H 'Content-Type: application/json' \
  -d '{"role":"RESEARCHER"}' -H "$LEAD_AUTH"
check_status N16.1.18 "and seats MEMBER2 as a researcher" 200
post_json "/projects/$P/representatives" "$(jq -nc --arg r "$REPRESENTATIVE_ID" '{representativeId:$r}')" \
  -H "$LEAD_AUTH"
check_status N16.1.19 "and gives the client representative a seat" 200

section "N16.2  the brief is drafted from the role title"

get "$BRIEF" -H "$LEAD_AUTH"
check_status N16.2.1 "the lead reads the brief" 200
DRAFT="$LAST_BODY"
check N16.2.2 "the role title is the mandate's" "Chief Financial Officer" "$(json '.details.roleTitle')"
check N16.2.3 "the CFO template drafted the department" "Finance" "$(json '.details.department')"
check N16.2.4 "…the seniority" "C_SUITE" "$(json '.details.seniority')"
check N16.2.5 "…and its four responsibilities, each marked as the template's" "4/4" \
  "$(json '.details.responsibilities | length')/$(json '[.details.responsibilities[] | select(.source == "TEMPLATE")] | length')"
check N16.2.6 "the department's provenance is the template" "TEMPLATE" "$(json '.details.fieldSources.department')"
# Read off the client row rather than hard-coded: Countries.nameOf canonicalises what fixtures typed.
check N16.2.7 "the client's home country seeds the location's country half" \
  "$(sql "SELECT hq_country FROM app_lm_client WHERE id = '$CLIENT_ID'")" "$(json '.details.locationCountry')"
check N16.2.8 "the org chart has exactly one mandate seat" "1" "$(json '[.reporting.orgChart[] | select(.mandateSeat)] | length')"
check N16.2.9 "…under the template's manager, with its four direct reports" "6" "$(json '.reporting.orgChart | length')"
check N16.2.10 "the template's notice is drafted" "3 MONTHS" "$(json '.reporting.noticeValue') $(json '.reporting.noticeUnit')"
check N16.2.11 "compensation opens in AED (V77)" "AED" "$(json '.compensation.currency')"
# Compared as numbers: jq 1.7 prints a BigDecimal literal as sent ("40.00"), 1.6 as a double ("40").
check N16.2.12 "…with the template's bonus shape and no salary band" "true PERCENT_OF_BASE null" \
  "$(json '.compensation.bonusValue == 40') $(json '.compensation.bonusBasis') $(json '.compensation.salaryMin')"
check N16.2.13 "both competency panels are drafted" "4/4" \
  "$(json '.assessment.technical | length')/$(json '.assessment.behavioural | length')"
check N16.2.14 "the technical share starts at 50" "50" "$(json '.assessment.technicalShare')"
check N16.2.15 "the reporting step shows the mandate's target date" "2027-06-01" "$(json '.reporting.targetStart')"
check N16.2.16 "nothing is published" "null" "$(json '.publication.publishedAt')"
check N16.2.17 "and no document is attached" "null" "$(json '.document')"

get "$BRIEF" -H "$LEAD_AUTH"
check N16.2.18 "reading twice drafts nothing new — the same seats come back" \
  "$(printf '%s' "$DRAFT" | jq -c '[.reporting.orgChart[].nodeId] | sort')" \
  "$(printf '%s' "$LAST_BODY" | jq -c '[.reporting.orgChart[].nodeId] | sort')"

section "N16.3  step one — details, and the two-halved location"

DETAILS=$(jq -nc '{roleTitle:"Group CFO – Energy Division", department:"Group Finance",
  locationCity:"Dubai", locationCountry:"United Arab Emirates", employmentType:"FULL_TIME_PERMANENT",
  seniority:"C_SUITE", responsibilities:[{text:"Own the group P&L"},{text:"Lead the IPO readiness programme"}],
  narrative:"Reports to the Group CEO."}')
put_json "$BRIEF/details" "$DETAILS" -H "$LEAD_AUTH"
check_status N16.3.1 "the lead writes step one" 200
get "$BRIEF" -H "$LEAD_AUTH"
check N16.3.2 "the city half round-trips" "Dubai" "$(json '.details.locationCity')"
check N16.3.3 "…and the country half, separately" "United Arab Emirates" "$(json '.details.locationCountry')"
check N16.3.4 "the typed responsibilities replaced the template's" "2 MANUAL" \
  "$(json '.details.responsibilities | length') $(json '.details.responsibilities[0].source')"
check N16.3.5 "a step saved without provenance reads as typed" "MANUAL" "$(json '.details.fieldSources.department')"
check N16.3.6 "the role title written here renames the brief" "Group CFO – Energy Division" "$(json '.details.roleTitle')"
get /projects -H "$LEAD_AUTH"
check N16.3.7 "…and the mandate itself — one title, on the project" "Group CFO – Energy Division" \
  "$(json ".[] | select(.id == \"$P\") | .positionTitle")"

put_json "$BRIEF/details" "$(printf '%s' "$DETAILS" | jq -c '.roleTitle = "  "')" -H "$LEAD_AUTH"
check_code N16.3.8 "a blank role title is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/details" "$(printf '%s' "$DETAILS" | jq -c '.fieldSources = {salaryMin:"MANUAL"}')" -H "$LEAD_AUTH"
check_code N16.3.9 "provenance for a key the step does not own is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/details" "$(printf '%s' "$DETAILS" | jq -c '.seniority = "N-1"')" -H "$LEAD_AUTH"
check_code N16.3.10 "the brief speaks the enum name, not the candidate wire token" 400 VALIDATION_FAILED
put_json "$BRIEF/details" "$(printf '%s' "$DETAILS" | jq -c '.fieldSources = {department:"DOCUMENT"}')" -H "$LEAD_AUTH"
# N16.3.5 stamped every key MANUAL, and a reading never displaces what a person typed
# (Position.mergeFieldSources): the claim is accepted, and the typed provenance stands.
check N16.3.11 "a DOCUMENT claim never displaces a MANUAL field" "200 MANUAL" \
  "$LAST_STATUS $(json '.details.fieldSources.department')"

section "N16.4  step two — the mandate context"

CONTEXT=$(jq -nc '{mandateReason:"SUCCESSION", businessDriver:"The incumbent retires in Q3.",
  strategicPriorities:[{name:"Capital discipline", selected:true},{name:"IPO readiness", selected:false}],
  confidential:true, internalContext:"Board wants a GCC national on the shortlist."}')
put_json "$BRIEF/context" "$CONTEXT" -H "$LEAD_AUTH"
check_status N16.4.1 "the lead writes step two" 200
get "$BRIEF" -H "$LEAD_AUTH"
check N16.4.2 "the reason round-trips" "SUCCESSION" "$(json '.context.mandateReason')"
check N16.4.3 "…and the confidential flag" "true" "$(json '.context.confidential')"
check N16.4.4 "…and the priorities, selection included" "Capital discipline:true,IPO readiness:false" \
  "$(json '[.context.strategicPriorities[] | "\(.name):\(.selected)"] | join(",")')"

put_json "$BRIEF/context" "$(printf '%s' "$CONTEXT" | jq -c '.strategicPriorities += [{name:"capital DISCIPLINE", selected:true}]')" \
  -H "$LEAD_AUTH"
check_code N16.4.5 "two priorities sharing a name, case aside, are refused" 400 VALIDATION_FAILED
put_json "$BRIEF/context" "$(printf '%s' "$CONTEXT" | jq -c 'del(.mandateReason)')" -H "$LEAD_AUTH"
check_code N16.4.6 "a context with no reason is refused" 400 VALIDATION_FAILED

section "N16.5  step three — reporting and the org chart"

MANAGER=$(new_uuid); SEAT=$(new_uuid); REPORT=$(new_uuid); EMPTY_LEAF=$(new_uuid)
chart() { # chart EXTRA_JQ -> a reporting body with the four seats, then EXTRA_JQ applied
  jq -nc --arg m "$MANAGER" --arg s "$SEAT" --arg r "$REPORT" --arg e "$EMPTY_LEAF" "
    {orgChart:[
       {nodeId:\$m, parentNodeId:null, title:\"Group CEO\", name:\"Khalid Al Mansoori\", mandateSeat:false},
       {nodeId:\$s, parentNodeId:\$m, title:null, name:null, mandateSeat:true, canvasX:120.5, canvasY:80},
       {nodeId:\$r, parentNodeId:\$s, title:\"Financial Controller\", name:null, mandateSeat:false},
       {nodeId:\$e, parentNodeId:\$s, title:null, name:null, mandateSeat:false}],
     teamSize:\"4 direct, 120 in function\", noticeValue:90, noticeUnit:\"DAYS\"} | ${1:-.}"
}

put_json "$BRIEF/reporting" "$(chart)" -H "$LEAD_AUTH"
check_status N16.5.1 "the lead writes the chart" 200
get "$BRIEF" -H "$LEAD_AUTH"
check N16.5.2 "an unnamed leaf clears itself; the named seats stay" "3" "$(json '.reporting.orgChart | length')"
check N16.5.3 "the chart keeps the client's own node ids" "true" \
  "$(json "[.reporting.orgChart[].nodeId] | (index(\"$MANAGER\") != null and index(\"$REPORT\") != null)")"
check N16.5.4 "the role reports to the seat above it" "$MANAGER" \
  "$(json '.reporting.orgChart[] | select(.mandateSeat) | .parentNodeId')"
check N16.5.5 "the added direct report hangs under the mandate seat" "Financial Controller" \
  "$(json ".reporting.orgChart[] | select(.parentNodeId == \"$SEAT\") | .title")"
check N16.5.6 "a dragged box keeps its position" "120.5" "$(json '.reporting.orgChart[] | select(.mandateSeat) | .canvasX')"
check N16.5.7 "the team size round-trips" "4 direct, 120 in function" "$(json '.reporting.teamSize')"
# The five-option notice picker is the screen's; the brief keeps the pair as recorded and the server
# narrows neither column, so a brief stating ninety days reads back as ninety days.
check N16.5.8 "a notice outside the screen's five options is kept as recorded" "90 DAYS" \
  "$(json '.reporting.noticeValue') $(json '.reporting.noticeUnit')"

put_json "$BRIEF/reporting" "$(chart '.noticeValue = -1')" -H "$LEAD_AUTH"
check_code N16.5.9 "a negative notice is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/reporting" "$(chart '.noticeUnit = "YEARS"')" -H "$LEAD_AUTH"
check_code N16.5.10 "an unknown notice unit is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/reporting" "$(chart '.orgChart |= map(.mandateSeat = false)')" -H "$LEAD_AUTH"
check_code N16.5.11 "a chart with no mandate seat is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/reporting" "$(chart '.orgChart[2].mandateSeat = true')" -H "$LEAD_AUTH"
check_code N16.5.12 "a chart with two mandate seats is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/reporting" "$(chart ".orgChart[2].parentNodeId = \"$(new_uuid)\"")" -H "$LEAD_AUTH"
check_code N16.5.13 "a seat reporting to one outside the chart is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/reporting" "$(chart ".orgChart[0].parentNodeId = \"$REPORT\"")" -H "$LEAD_AUTH"
check_code N16.5.14 "a chart that loops back on itself is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/reporting" "$(chart ".orgChart[2].nodeId = \"$MANAGER\"")" -H "$LEAD_AUTH"
check_code N16.5.15 "two seats sharing an id are refused" 400 VALIDATION_FAILED
put_json "$BRIEF/reporting" "$(chart '.orgChart = []')" -H "$LEAD_AUTH"
check_code N16.5.16 "an empty chart is refused" 400 VALIDATION_FAILED
get "$BRIEF" -H "$LEAD_AUTH"
check N16.5.17 "none of the refusals touched the stored chart" "3" "$(json '.reporting.orgChart | length')"

# The target date is the project's: the reporting step shows it and the project is where it moves.
http PATCH "/projects/$P" -H 'Content-Type: application/json' -d '{"targetDate":"2027-07-15"}' -H "$LEAD_AUTH"
check_status N16.5.18 "the lead moves the mandate's target date" 200
get "$BRIEF" -H "$LEAD_AUTH"
check N16.5.19 "…and the brief's reporting step reads the new one" "2027-07-15" "$(json '.reporting.targetStart')"

section "N16.6  step four — compensation, with a fixed-amount bonus"

COMP=$(jq -nc '{currency:"AED", salaryMin:900000, salaryMax:1200000, baseSalaryMode:"ANNUAL",
  bonusValue:250000.50, bonusBasis:"FIXED_AMOUNT", incentiveType:"RSU", incentiveAmount:400000,
  incentiveVesting:"Four years, one-year cliff",
  benefits:[{name:"Housing allowance", amount:25000, frequency:"MONTHLY"},
            {name:"Annual flights", frequency:"YEARLY"}]}')
put_json "$BRIEF/compensation" "$COMP" -H "$LEAD_AUTH"
check_status N16.6.1 "the lead writes step four" 200
get "$BRIEF" -H "$LEAD_AUTH"
check N16.6.2 "the band round-trips" "900000-1200000 ANNUAL" \
  "$(json '.compensation.salaryMin')-$(json '.compensation.salaryMax') $(json '.compensation.baseSalaryMode')"
check N16.6.3 "a bonus stated as a fixed amount keeps its basis" "FIXED_AMOUNT" "$(json '.compensation.bonusBasis')"
check N16.6.4 "…and its money, cents included (V66 widened the column)" "true" "$(json '.compensation.bonusValue == 250000.5')"
check N16.6.5 "the benefits round-trip, an unquantified one without an amount" "25000|null" \
  "$(json '[.compensation.benefits[].amount] | map(tostring) | join("|")')"

get "$BRIEF/compensation" -H "$LEAD_AUTH"
check_status N16.6.6 "the compensation-only read answers" 200
check N16.6.7 "…with the brief's figures" "AED FIXED_AMOUNT 1200000" \
  "$(json '.currency') $(json '.bonusBasis') $(json '.salaryMax')"

put_json "$BRIEF/compensation" "$(printf '%s' "$COMP" | jq -c '.salaryMin = 2000000')" -H "$LEAD_AUTH"
check_status N16.6.8 "a minimum above the maximum is saved — autosave persists a half-typed band" 200
put_json "$BRIEF/compensation" "$(printf '%s' "$COMP" | jq -c '.currency = "aed"')" -H "$LEAD_AUTH"
check_code N16.6.9 "a lower-case currency is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/compensation" "$(printf '%s' "$COMP" | jq -c '.bonusValue = -1')" -H "$LEAD_AUTH"
check_code N16.6.10 "a negative bonus is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/compensation" "$(printf '%s' "$COMP" | jq -c '.bonusBasis = "SHARES"')" -H "$LEAD_AUTH"
check_code N16.6.11 "an unknown bonus basis is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/compensation" "$COMP" -H "$LEAD_AUTH" >/dev/null

section "N16.7  step five — criteria and competencies"

CRITERIA=$(jq -nc '{criteria:[{text:"Big-4 audit background", mode:"REQUIRED"},
                              {text:"Arabic speaker", mode:"PREFERRED", source:"MANUAL"}]}')
put_json "$BRIEF/criteria" "$CRITERIA" -H "$LEAD_AUTH"
check_status N16.7.1 "the lead writes the criteria" 200
get "$BRIEF" -H "$LEAD_AUTH"
check N16.7.2 "the criteria list is replaced whole" "Big-4 audit background:REQUIRED,Arabic speaker:PREFERRED" \
  "$(json '[.assessment.criteria[] | "\(.text):\(.mode)"] | join(",")')"
check N16.7.3 "a criterion sent without provenance reads as typed" "MANUAL" "$(json '.assessment.criteria[0].source')"
put_json "$BRIEF/criteria" '{"criteria":[{"text":"Anything","mode":"NICE_TO_HAVE"}]}' -H "$LEAD_AUTH"
check_code N16.7.4 "an unknown criterion mode is refused" 400 VALIDATION_FAILED

COMPETENCIES=$(jq -nc '{technical:[{name:"IFRS reporting", weight:60},{name:"Treasury", weight:30}],
  behavioural:[{name:"Strategic leadership", description:"Sets direction", weight:100}], technicalShare:70}')
put_json "$BRIEF/competencies" "$COMPETENCIES" -H "$LEAD_AUTH"
check_status N16.7.5 "the lead writes both panels and the technical share" 200
get "$BRIEF" -H "$LEAD_AUTH"
check N16.7.6 "the panels round-trip" "IFRS reporting,Treasury|Strategic leadership" \
  "$(json '[.assessment.technical[].name] | join(",")')|$(json '[.assessment.behavioural[].name] | join(",")')"
check N16.7.7 "the technical share round-trips" "70" "$(json '.assessment.technicalShare')"
check N16.7.8 "a panel not totalling 100 is saved — nothing checks the sum" "90" \
  "$(json '[.assessment.technical[].weight] | add')"
put_json "$BRIEF/competencies" "$(printf '%s' "$COMPETENCIES" | jq -c 'del(.technicalShare)')" -H "$LEAD_AUTH"
check N16.7.9 "a write that omits the share leaves the stored one standing" "70" "$(json '.assessment.technicalShare')"
put_json "$BRIEF/competencies" "$(printf '%s' "$COMPETENCIES" | jq -c '.technicalShare = 101')" -H "$LEAD_AUTH"
check_code N16.7.10 "a share over 100 is refused" 400 VALIDATION_FAILED
put_json "$BRIEF/competencies" "$(printf '%s' "$COMPETENCIES" | jq -c '.technical[0].weight = 101')" -H "$LEAD_AUTH"
check_code N16.7.11 "a weight over 100 is refused" 400 VALIDATION_FAILED

section "N16.8  the role-template library"

get /position-templates -H "$LEAD_AUTH"
check_status N16.8.1 "a staff member lists the templates" 200
check N16.8.2 "the seventeen library templates are offered" "true" "$(json 'length >= 17')"
check N16.8.3 "…the CFO among them, as the shared library's" "true" \
  "$(json '[.[] | select(.code == "chief-financial-officer")][0].shared')"
CTO_TEMPLATE=$(json '.[] | select(.code == "chief-technology-officer") | .id')

post_json "$BRIEF/template" "$(jq -nc --arg t "$CTO_TEMPLATE" '{templateId:$t}')" -H "$LEAD_AUTH"
check_status N16.8.4 "the lead redrafts the brief from the CTO template" 200
check N16.8.5 "the drafted half is the new template's" "Technology" "$(json '.details.department')"
check N16.8.6 "…the role title stays the mandate's" "Group CFO – Energy Division" "$(json '.details.roleTitle')"
check N16.8.7 "…the location is left as it was" "Dubai / United Arab Emirates" \
  "$(json '.details.locationCity') / $(json '.details.locationCountry')"
check N16.8.8 "…the criteria somebody typed survive the redraft" "2" \
  "$(json '[.assessment.criteria[] | select(.text == "Big-4 audit background" or .text == "Arabic speaker")] | length')"
check N16.8.9 "…and the chart is the template's again, one mandate seat" "1" \
  "$(json '[.reporting.orgChart[] | select(.mandateSeat)] | length')"

post_json "$BRIEF/template" "$(jq -nc --arg t "$(new_uuid)" '{templateId:$t}')" -H "$LEAD_AUTH"
check_code N16.8.10 "a template id nobody holds" 404 NOT_FOUND
post_json "$BRIEF/template" '{}' -H "$LEAD_AUTH"
check_code N16.8.11 "a redraft naming no template" 400 VALIDATION_FAILED

section "N16.9  publishing is a stamp, not a lock"

http POST "$BRIEF/publish" -H "$LEAD_AUTH"
check_status N16.9.1 "the lead publishes" 200
PUBLISHED_AT=$(json '.publication.publishedAt')
check N16.9.2 "the stamp says when" "true" "$([ -n "$PUBLISHED_AT" ] && [ "$PUBLISHED_AT" != "null" ] && echo true || echo false)"
check N16.9.3 "…and who, by name" "Mel Member" "$(json '.publication.publishedBy')"

put_json "$BRIEF/context" "$(printf '%s' "$CONTEXT" | jq -c '.businessDriver = "Edited after publishing."')" -H "$LEAD_AUTH"
check_status N16.9.4 "a published brief still takes a write" 200
check N16.9.5 "…which lands" "Edited after publishing." "$(json '.context.businessDriver')"
check N16.9.6 "…and leaves it published" "$PUBLISHED_AT" "$(json '.publication.publishedAt')"

http POST "$BRIEF/publish" -H "$LEAD_AUTH"
check N16.9.7 "publishing again keeps the original stamp" "$PUBLISHED_AT" "$(json '.publication.publishedAt')"
# Audit rows land asynchronously: wait for the first, then give a wrongly-recorded second time to land.
PUBLISHED_EVENTS="SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'POSITION_PUBLISHED' AND target_id = '$P'"
await_sql "$PUBLISHED_EVENTS" 1 >/dev/null
sleep 0.3
check N16.9.8 "one POSITION_PUBLISHED event for the two presses" "1" "$(sql "$PUBLISHED_EVENTS")"

http DELETE "$BRIEF/publish" -H "$LEAD_AUTH"
check_status N16.9.9 "the lead withdraws the publication" 200
check N16.9.10 "…and the stamp is gone" "null|null" "$(json '.publication.publishedAt')|$(json '.publication.publishedBy')"
http DELETE "$BRIEF/publish" -H "$LEAD_AUTH"
check_status N16.9.11 "withdrawing an unpublished brief is a no-op, not an error" 200

section "N16.10  the position description"

DOC_DIR="$RUN_DIR/16-docs"
mkdir -p "$DOC_DIR"
printf 'Group CFO - Energy Division\nReports to the Group CEO.\n' > "$DOC_DIR/cfo-brief.txt"
printf '%%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%%%EOF\n' > "$DOC_DIR/cfo-brief.pdf"
printf '\x89PNG\r\n\x1a\n' > "$DOC_DIR/logo.png"
: > "$DOC_DIR/empty.txt"
TXT_SIZE=$(wc -c < "$DOC_DIR/cfo-brief.txt" | tr -d ' ')

http POST "$BRIEF/document" -F "file=@$DOC_DIR/cfo-brief.txt;type=text/plain" -H "$LEAD_AUTH"
check_status N16.10.1 "the lead attaches a plain-text description" 200
check N16.10.2 "the brief now names it" "cfo-brief.txt text/plain $TXT_SIZE" \
  "$(json '.document.fileName') $(json '.document.contentType') $(json '.document.fileSize')"

get "$BRIEF/document" -H "$LEAD_AUTH"
check_status N16.10.3 "it downloads" 200
check N16.10.4 "…byte for byte" "$(cat "$DOC_DIR/cfo-brief.txt")" "$LAST_BODY"
check_contains N16.10.5 "…always as an opaque download, never its declared type" "application/octet-stream" "$(header 'Content-Type')"
check_contains N16.10.6 "…with nosniff" "nosniff" "$(header 'X-Content-Type-Options')"
check_contains N16.10.7 "…as an attachment under its own name" "attachment" "$(header 'Content-Disposition')"

http POST "$BRIEF/document" -F "file=@$DOC_DIR/cfo-brief.pdf;type=application/pdf" -H "$LEAD_AUTH"
check_status N16.10.8 "attaching a PDF replaces it" 200
check N16.10.9 "…under the new name" "cfo-brief.pdf" "$(json '.document.fileName')"
check N16.10.10 "one document per brief, never a second row" "1" \
  "$(sql "SELECT count(*) FROM app_lm_position_document d JOIN app_lm_position p ON p.id = d.position_id
          WHERE p.project_id = '$P'")"

http POST "$BRIEF/document" -F "file=@$DOC_DIR/logo.png;type=image/png" -H "$LEAD_AUTH"
check_code N16.10.11 "a type outside the allowlist is refused" 400 UNSUPPORTED_FILE_TYPE
http POST "$BRIEF/document" -F "file=@$DOC_DIR/empty.txt;type=text/plain" -H "$LEAD_AUTH"
check_code N16.10.12 "an empty file is refused" 400 VALIDATION_FAILED
get "$BRIEF" -H "$LEAD_AUTH"
check N16.10.13 "neither refusal disturbed the stored one" "cfo-brief.pdf" "$(json '.document.fileName')"

get "$BRIEF/document" -H "$CLIENT_AUTH"
check_status N16.10.14 "the client seat may open the description its mandate was briefed from" 200
http POST "$BRIEF/document" -F "file=@$DOC_DIR/cfo-brief.txt;type=text/plain" -H "$CLIENT_AUTH"
check_code N16.10.15 "…but not attach one" 403 FORBIDDEN
http DELETE "$BRIEF/document" -H "$CLIENT_AUTH"
check_code N16.10.16 "…nor remove it" 403 FORBIDDEN
http POST "$BRIEF/document" -F "file=@$DOC_DIR/cfo-brief.txt;type=text/plain" -H "$RESEARCHER_AUTH"
check_code N16.10.17 "a researcher cannot attach one either — it is PROJECT_EDIT" 403 FORBIDDEN

http DELETE "$BRIEF/document" -H "$LEAD_AUTH"
check_status N16.10.18 "the lead removes it" 200
check N16.10.19 "…and the brief no longer names one" "null" "$(json '.document')"
get "$BRIEF/document" -H "$LEAD_AUTH"
check_code N16.10.20 "downloading a removed document" 404 NOT_FOUND
http DELETE "$BRIEF/document" -H "$LEAD_AUTH"
check_status N16.10.21 "removing when nothing is attached is a no-op" 200

section "N16.11  who may read and who may write"

# Every body below is well-formed on purpose: Bean Validation runs before method security, so a
# malformed payload would answer 400 and never reach the gate the case is about.
get "$BRIEF" -H "$CLIENT_AUTH"
check_status N16.11.1 "the client seat reads the brief" 200
get "$BRIEF/compensation" -H "$CLIENT_AUTH"
check_status N16.11.2 "…and its compensation" 200
get "$BRIEF" -H "$RESEARCHER_AUTH"
check_status N16.11.3 "a researcher reads the brief" 200

refused_writes() { # refused_writes CASE_PREFIX AUTH WHO EXPECTED_STATUS EXPECTED_CODE
  local id="$1" auth="$2" who="$3" status="$4" code="$5"
  put_json "$BRIEF/details" "$DETAILS" -H "$auth"
  check_code "$id.1" "$who: details" "$status" "$code"
  put_json "$BRIEF/context" "$CONTEXT" -H "$auth"
  check_code "$id.2" "$who: context" "$status" "$code"
  put_json "$BRIEF/reporting" "$(chart)" -H "$auth"
  check_code "$id.3" "$who: reporting" "$status" "$code"
  put_json "$BRIEF/compensation" "$COMP" -H "$auth"
  check_code "$id.4" "$who: compensation" "$status" "$code"
  put_json "$BRIEF/criteria" "$CRITERIA" -H "$auth"
  check_code "$id.5" "$who: criteria" "$status" "$code"
  put_json "$BRIEF/competencies" "$COMPETENCIES" -H "$auth"
  check_code "$id.6" "$who: competencies" "$status" "$code"
  post_json "$BRIEF/template" "$(jq -nc --arg t "$CTO_TEMPLATE" '{templateId:$t}')" -H "$auth"
  check_code "$id.7" "$who: template redraft" "$status" "$code"
  http POST "$BRIEF/publish" -H "$auth"
  check_code "$id.8" "$who: publish" "$status" "$code"
  http DELETE "$BRIEF/publish" -H "$auth"
  check_code "$id.9" "$who: withdraw publication" "$status" "$code"
}

refused_writes N16.11.4 "$CLIENT_AUTH" "a client seat cannot write" 403 FORBIDDEN
refused_writes N16.11.5 "$RESEARCHER_AUTH" "a researcher cannot write the brief" 403 FORBIDDEN

get /position-templates -H "$CLIENT_AUTH"
check_code N16.11.6 "a pure client cannot browse the template library" 403 FORBIDDEN

get "$BRIEF" -H "$OUTSIDER_AUTH"
check_code N16.11.7 "another workspace's admin cannot read it — the id does not exist for them" 404 NOT_FOUND
get "$BRIEF/compensation" -H "$OUTSIDER_AUTH"
check_code N16.11.8 "…nor its compensation" 404 NOT_FOUND
get "$BRIEF/document" -H "$OUTSIDER_AUTH"
check_code N16.11.9 "…nor its document" 404 NOT_FOUND
put_json "$BRIEF/details" "$DETAILS" -H "$OUTSIDER_AUTH"
check_code N16.11.10 "…nor write it" 404 NOT_FOUND

get "$BRIEF" -H "$LEAD_AUTH"
check N16.11.11 "no refused write landed" "Technology|Group CFO – Energy Division" \
  "$(json '.details.department')|$(json '.details.roleTitle')"

summary
