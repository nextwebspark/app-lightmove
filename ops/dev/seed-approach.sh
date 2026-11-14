#!/usr/bin/env bash
# Recently approached, every case, for the LOCAL stack only: `npm run dev:db:seed-approach`.
#
# Needs `npm run dev` running this branch (V222 applied) on :8080 and its container. Founds a fresh workspace,
# "Approach Demo <time>", with three people who can sign in — Yara (admin), Sara and Omar (members) — three
# positions and six executives, then writes the approaches straight into the local container: emails and a reply
# (no mail is sent), logged calls and WhatsApp messages, statuses on the other positions, and a stand-in mailbox for
# Yara and Sara so the composer opens. Sending from a stand-in mailbox fails at dispatch; everything before it works.
set -euo pipefail

API=${API:-http://localhost:8080/api/v1}
PG_CONTAINER=${PG_CONTAINER:-lm-dev-pg}
PASSWORD=${SEED_PASSWORD:-Approach-Demo-2026!}
STAMP=$(date +%s)
JAR=$(mktemp)
trap 'rm -f "$JAR"' EXIT

say() { printf '\033[36m[seed-approach]\033[0m %s\n' "$*"; }
sql() { docker exec -i "$PG_CONTAINER" psql -U postgres -d lightmove -qtA -v ON_ERROR_STOP=1 "$@"; }

docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER" || { say "$PG_CONTAINER is not running — run \`npm run dev\` first"; exit 1; }
curl -s -c "$JAR" -b "$JAR" "$API/auth/csrf" >/dev/null || { say "no API on $API — run \`npm run dev\` first"; exit 1; }
[ "$(sql -c "select count(*) from flyway_schema_history where version = '222' and description = 'recently approached'")" = "1" ] \
  || { say "V222 is not applied — restart \`npm run dev\` on the feature/recently-approached branch"; exit 1; }
XSRF=$(awk '/XSRF-TOKEN/{print $7}' "$JAR")

call() {
  local method=$1 path=$2 token=$3 body=${4:-}
  local args=(-s -c "$JAR" -b "$JAR" -X "$method" "$API$path" -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}"
}
field() { python3 -I -c 'import json,sys; v=json.load(sys.stdin); [v:=v[k] for k in sys.argv[1].split(".")]; print(v)' "$1"; }

account() {
  local name=$1 email=$2
  call POST /auth/signup "" "{\"fullName\":\"$name\",\"email\":\"$email\",\"password\":\"$PASSWORD\",\"termsAccepted\":true}" >/dev/null
  sql -c "update app_lm_user set status = 'ACTIVE', email_verified_at = now() where email = '$email'"
}
login() { call POST /auth/login "" "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}" | field accessToken; }

YARA="approach-yara-$STAMP@nextwebspark.com"
SARA="approach-sara-$STAMP@nextwebspark.com"
OMAR="approach-omar-$STAMP@nextwebspark.com"

say "accounts"
account "Yara Haddad" "$YARA"
# `npm run dev:cloud` also answers on :8080, against the shared database: stop at the first account it would write there.
[ "$(sql -c "select count(*) from app_lm_user where email = '$YARA'")" = "1" ] \
  || { say "the API on $API is not using $PG_CONTAINER (is \`npm run dev:cloud\` running?) — stopped"; exit 1; }
account "Sara Al-Mansour" "$SARA"; account "Omar Khalil" "$OMAR"
WS=$(call POST /onboarding/workspace "$(login "$YARA")" "{\"mode\":\"COMPANY\",\"name\":\"Approach Demo $STAMP\"}" | field workspace.id)
sql <<SQL
WITH member AS (
  INSERT INTO app_lm_workspace_member (workspace_id, user_id, status, joined_at)
  SELECT '$WS', id, 'ACTIVE', now() FROM app_lm_user WHERE email IN ('$SARA', '$OMAR')
  RETURNING id)
INSERT INTO app_lm_workspace_member_role (member_id, role_id, role_scope)
SELECT member.id, role.id, 'WORKSPACE' FROM member, app_lm_role role WHERE role.name = 'MEMBER' AND role.scope = 'WORKSPACE';
UPDATE app_lm_user SET last_workspace_id = '$WS' WHERE email IN ('$YARA', '$SARA', '$OMAR');
SQL
YT=$(login "$YARA"); ST=$(login "$SARA"); OT=$(login "$OMAR")

say "positions"
CLIENT=$(call POST /clients "$YT" '{"customName":"Group Finance"}' | field id)
position() { call POST /projects "$1" "{\"clientId\":\"$CLIENT\",\"positionTitle\":\"$2\"}" | field id; }
CFO=$(position "$YT" "Group CFO")
CREDIT=$(position "$ST" "Head of Credit Risk")
COO=$(position "$OT" "Chief Operating Officer")

say "executives"
executive() {
  local token=$1 project=$2 name=$3 slug
  slug="$(echo "$name" | tr '[:upper:] ' '[:lower:]-')-$STAMP"
  call POST "/projects/$project/candidates" "$token" \
    "{\"fullName\":\"$name\",\"title\":\"Chief Financial Officer\",\"employerName\":\"Target Group\",\"linkedinUrl\":\"https://www.linkedin.com/in/$slug\",\"email\":\"$slug@nextwebspark.com\"}" \
    | field id
}
for person in "Layla Haddad" "Rajesh Menon" "Fatima Al Mazrouei" "Hana Aziz" "Noura Al Suwaidi" "Khalid Nasser"; do
  executive "$YT" "$CFO" "$person" >/dev/null
done
executive "$ST" "$CREDIT" "Layla Haddad" >/dev/null
executive "$ST" "$CREDIT" "Rajesh Menon" >/dev/null
executive "$ST" "$CREDIT" "Fatima Al Mazrouei" >/dev/null
executive "$OT" "$COO" "Rajesh Menon" >/dev/null
executive "$OT" "$COO" "Noura Al Suwaidi" >/dev/null

say "approaches"
sql <<SQL
\o /dev/null
CREATE TEMP TABLE who AS SELECT email, id FROM app_lm_user WHERE email IN ('$YARA', '$SARA', '$OMAR');
CREATE TEMP TABLE mapped AS
  SELECT c.id AS candidate_id, c.project_id, p.id AS person_id, p.full_name
  FROM app_lm_project_candidate c JOIN app_lm_person p ON p.id = c.person_id
  WHERE p.workspace_id = '$WS';

CREATE FUNCTION pg_temp.status(name text, project uuid, status_key text) RETURNS void AS \$\$
  UPDATE app_lm_project_candidate c SET status_id = s.id, status_changed_at = now()
  FROM app_lm_workspace_candidate_status s, mapped m
  WHERE s.workspace_id = '$WS' AND s.key = status_key AND m.candidate_id = c.id AND m.full_name = name
    AND m.project_id = project;
\$\$ LANGUAGE sql;
SELECT pg_temp.status('Layla Haddad', '$CREDIT', 'interested');
SELECT pg_temp.status('Rajesh Menon', '$CREDIT', 'engaged');
SELECT pg_temp.status('Rajesh Menon', '$COO', 'contacted');
SELECT pg_temp.status('Fatima Al Mazrouei', '$CREDIT', 'notInterested');
SELECT pg_temp.status('Noura Al Suwaidi', '$COO', 'placed');

CREATE FUNCTION pg_temp.email(name text, project uuid, sender text, direction text, kind text, status text, ago interval)
RETURNS void AS \$\$
  INSERT INTO app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, sender_user_id, direction,
      kind, status, subject, body, body_text, provider_message_id, sent_at, occurred_at)
  SELECT '$WS', project, m.person_id, m.candidate_id, w.id, direction, kind, status, 'Seeded email', 'Seeded email',
      'Seeded email', 'seed-' || gen_random_uuid(), CASE WHEN status = 'SENT' THEN now() - ago END, now() - ago
  FROM mapped m, who w WHERE m.full_name = name AND m.project_id = project AND w.email = sender;
\$\$ LANGUAGE sql;
SELECT pg_temp.email('Layla Haddad', '$CREDIT', '$SARA', 'OUTBOUND', 'MANUAL', 'SENT', interval '12 days');
SELECT pg_temp.email('Layla Haddad', '$CREDIT', '$SARA', 'INBOUND', 'REPLY', 'RECEIVED', interval '9 days');
SELECT pg_temp.email('Rajesh Menon', '$CFO', '$YARA', 'OUTBOUND', 'MANUAL', 'SENT', interval '20 days');
SELECT pg_temp.email('Fatima Al Mazrouei', '$CREDIT', '$SARA', 'OUTBOUND', 'MANUAL', 'SENT', interval '120 days');

CREATE FUNCTION pg_temp.logged(name text, project uuid, author text, kind text, ago interval) RETURNS void AS \$\$
  INSERT INTO app_lm_person_note (workspace_id, person_id, project_id, project_title, kind, body, author_user_id,
      occurred_at)
  SELECT '$WS', m.person_id, project, pr.position_title, kind, 'Seeded ' || lower(kind) || ' note', w.id, now() - ago
  FROM mapped m JOIN app_lm_project pr ON pr.id = m.project_id, who w
  WHERE m.full_name = name AND m.project_id = project AND w.email = author;
\$\$ LANGUAGE sql;
SELECT pg_temp.logged('Rajesh Menon', '$COO', '$OMAR', 'WHATSAPP', interval '3 days');
SELECT pg_temp.logged('Hana Aziz', '$CFO', '$SARA', 'CALL', interval '2 days');

INSERT INTO app_lm_mailbox_connection (workspace_id, user_id, provider, address, grant_id, status, daily_cap,
    connected_at)
SELECT '$WS', id, 'google', email, 'seed-grant-' || id, 'ACTIVE', 50, now() FROM who WHERE email IN ('$YARA', '$SARA');
SQL

cat <<DONE

Approach Demo is ready — password for all three: $PASSWORD
  Yara Haddad (admin)   $YARA    — open Group CFO
  Sara Al-Mansour       $SARA    — open Head of Credit Risk
  Omar Khalil           $OMAR

As Yara, on Group CFO (Outreach → Write email, or Add to sequence):
  Layla Haddad        amber: replied to Sara 9 days ago, Sara emailed 12 days ago, in process on Head of Credit Risk
  Rajesh Menon        amber: Omar logged WhatsApp 3 days ago (COO); in process on COO and Credit Risk. Yara's own
                      email here is left out
  Fatima Al Mazrouei  quiet: last approached 120 days ago; Not interested on Credit Risk
  Hana Aziz           amber: Sara logged a call 2 days ago on this position
  Noura Al Suwaidi    amber: Placed on Chief Operating Officer
  Khalid Nasser       nothing — the control
As Sara, on Head of Credit Risk, Rajesh Menon: "another position" and its stage only — Sara holds no seat on Group
CFO or COO. Settings → Meetings & email → Recently approached: 10 days turns Layla's email into history; 0 turns it off.
DONE
