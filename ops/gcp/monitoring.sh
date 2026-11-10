#!/usr/bin/env bash
#
# Alerting for the Cloud Run service: log-based metrics, email channels, alert policies, an uptime check.
#
#   ALERT_EMAILS="a@x.com,b@x.com" ./ops/gcp/monitoring.sh            # create or update, idempotent
#   ALERT_EMAILS="a@x.com" ./ops/gcp/monitoring.sh --dry-run          # print what it would do and stop
#
# Log metrics count app ERRORs (stdout/stderr) and 5xx (request log) apart: the request log repeats a 5xx
# the app already logged, so one counter would double every failure. A log metric sees nothing while the
# service is down, so the uptime check and its policy cover that case.
#
# Everything goes through the REST API: gcloud's monitoring policy commands sit in the alpha/beta
# components, which a fresh machine does not have.
set -euo pipefail

PROJECT="${GCP_PROJECT:-hak-talent-mapping}"
REGION="${GCP_REGION:-us-central1}"
SERVICE="${SERVICE:-lightmove}"
HOST="${HEALTH_HOST:-beta.uncava.com}"
ERROR_THRESHOLD="${ERROR_THRESHOLD:-5}"
WINDOW_SECONDS="${WINDOW_SECONDS:-600}"
ALERT_EMAILS="${ALERT_EMAILS:-nextwebspark@gmail.com,ambrish.java@gmail.com}"
API="https://monitoring.googleapis.com/v3/projects/${PROJECT}"

dry_run=false
[[ "${1:-}" == "--dry-run" ]] && dry_run=true

say() { printf '\n\033[1m▸ %s\033[0m\n' "$1"; }
die() { printf '\n\033[31m✗ %s\033[0m\n' "$1" >&2; exit 1; }

[[ -n "$ALERT_EMAILS" ]] || die "Set ALERT_EMAILS to a comma-separated list of addresses (a group address saves editing this later)."

APP_FILTER="resource.type=\"cloud_run_revision\" AND resource.labels.service_name=\"${SERVICE}\""
APP_ERRORS_FILTER="${APP_FILTER} AND severity>=ERROR AND (logName:\"run.googleapis.com%2Fstderr\" OR logName:\"run.googleapis.com%2Fstdout\")"
HTTP_5XX_FILTER="${APP_FILTER} AND logName:\"run.googleapis.com%2Frequests\" AND httpRequest.status>=500"

if $dry_run; then
    echo
    echo "  project     ${PROJECT} / ${SERVICE} (${REGION})"
    echo "  recipients  ${ALERT_EMAILS}"
    echo "  threshold   >= ${ERROR_THRESHOLD} in ${WINDOW_SECONDS}s"
    echo "  metrics     lightmove_app_errors, lightmove_http_5xx"
    echo "  uptime      https://${HOST}/actuator/health"
    echo
    exit 0
fi

TOKEN="$(gcloud auth print-access-token)"
rest() {
    local method="$1" url="$2" body="${3:-}" out status
    if [[ -n "$body" ]]; then
        out="$(curl -sS -X "$method" "$url" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$body" -w '\n%{http_code}')"
    else
        out="$(curl -sS -X "$method" "$url" -H "Authorization: Bearer ${TOKEN}" -w '\n%{http_code}')"
    fi
    status="${out##*$'\n'}"
    [[ "$status" == 2* ]] || die "${method} ${url} refused (HTTP ${status}):
${out%$'\n'*}"
    printf '%s' "${out%$'\n'*}"
}
field() { python3 -I -c "import sys,json; print(json.load(sys.stdin).get('$1',''))"; }
find_named() {
    python3 -I -c "
import sys,json
d=json.load(sys.stdin); key=sys.argv[1]; want=sys.argv[2]
for x in d.get(key,[]):
    if x.get('displayName')==want: print(x['name']); break
" "$1" "$2"
}

say "APIs"
gcloud services enable monitoring.googleapis.com logging.googleapis.com --project="$PROJECT"

say "Log-based metrics"
upsert_metric() {
    local name="$1" description="$2" filter="$3"
    if gcloud logging metrics describe "$name" --project="$PROJECT" &>/dev/null; then
        gcloud logging metrics update "$name" --description="$description" --log-filter="$filter" --project="$PROJECT" >/dev/null
        echo "  ✓ ${name}: updated"
    else
        gcloud logging metrics create "$name" --description="$description" --log-filter="$filter" --project="$PROJECT" >/dev/null
        echo "  ✓ ${name}: created"
    fi
}
upsert_metric lightmove_app_errors "Application ERROR log lines" "$APP_ERRORS_FILTER"
upsert_metric lightmove_http_5xx "Requests answered 5xx" "$HTTP_5XX_FILTER"

say "Email channels"
CHANNELS_JSON="$(rest GET "${API}/notificationChannels")"
CHANNEL_NAMES=()
IFS=',' read -ra ADDRESSES <<<"$ALERT_EMAILS"
for address in "${ADDRESSES[@]}"; do
    address="$(echo "$address" | xargs)"
    existing="$(echo "$CHANNELS_JSON" | find_named notificationChannels "Uncava alerts: ${address}")"
    if [[ -z "$existing" ]]; then
        existing="$(rest POST "${API}/notificationChannels" \
            "{\"type\":\"email\",\"displayName\":\"Uncava alerts: ${address}\",\"labels\":{\"email_address\":\"${address}\"}}" | field name)"
        echo "  ✓ ${address}: created"
    else
        echo "  ✓ ${address}: exists"
    fi
    CHANNEL_NAMES+=("\"${existing}\"")
done
CHANNELS="$(IFS=,; echo "${CHANNEL_NAMES[*]}")"

say "Uptime check"
UPTIME_NAME="Uncava health"
UPTIME_BODY="{\"displayName\":\"${UPTIME_NAME}\",\"period\":\"300s\",\"timeout\":\"10s\",
\"monitoredResource\":{\"type\":\"uptime_url\",\"labels\":{\"project_id\":\"${PROJECT}\",\"host\":\"${HOST}\"}},
\"httpCheck\":{\"path\":\"/actuator/health\",\"port\":443,\"useSsl\":true,\"validateSsl\":true,\"requestMethod\":\"GET\"}}"
uptime_existing="$(rest GET "${API}/uptimeCheckConfigs" | find_named uptimeCheckConfigs "$UPTIME_NAME")"
if [[ -z "$uptime_existing" ]]; then
    uptime_existing="$(rest POST "${API}/uptimeCheckConfigs" "$UPTIME_BODY" | field name)"
    echo "  ✓ created"
else
    rest PATCH "${API%/projects/*}/${uptime_existing}" "$UPTIME_BODY" >/dev/null
    echo "  ✓ updated"
fi
UPTIME_ID="${uptime_existing##*/}"

say "Alert policies"
LOGS_LINK="https://console.cloud.google.com/logs/query;query=resource.type%3D%22cloud_run_revision%22%20resource.labels.service_name%3D%22${SERVICE}%22%20severity%3E%3DERROR?project=${PROJECT}"
POLICIES_JSON="$(rest GET "${API}/alertPolicies")"

count_policy() {
    local display="$1" metric="$2" doc="$3"
    cat <<JSON
{"displayName":"${display}","combiner":"OR","enabled":true,
 "conditions":[{"displayName":"${display}","conditionThreshold":{
   "filter":"metric.type=\"logging.googleapis.com/user/${metric}\" AND resource.type=\"cloud_run_revision\"",
   "comparison":"COMPARISON_GT","thresholdValue":$((ERROR_THRESHOLD - 1)),"duration":"0s",
   "aggregations":[{"alignmentPeriod":"${WINDOW_SECONDS}s","perSeriesAligner":"ALIGN_SUM","crossSeriesReducer":"REDUCE_SUM"}],
   "trigger":{"count":1}}}],
 "alertStrategy":{"autoClose":"1800s"},
 "notificationChannels":[${CHANNELS}],
 "documentation":{"mimeType":"text/markdown","content":"${doc}\n\nLogs: ${LOGS_LINK}"}}
JSON
}

uptime_policy() {
    cat <<JSON
{"displayName":"Uncava health check failing","combiner":"OR","enabled":true,
 "conditions":[{"displayName":"Health endpoint unreachable","conditionThreshold":{
   "filter":"metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\" AND metric.label.check_id=\"${UPTIME_ID}\" AND resource.type=\"uptime_url\"",
   "comparison":"COMPARISON_GT","thresholdValue":1,"duration":"300s",
   "aggregations":[{"alignmentPeriod":"300s","perSeriesAligner":"ALIGN_NEXT_OLDER","crossSeriesReducer":"REDUCE_COUNT_FALSE","groupByFields":["resource.label.host"]}],
   "trigger":{"count":1}}}],
 "alertStrategy":{"autoClose":"1800s"},
 "notificationChannels":[${CHANNELS}],
 "documentation":{"mimeType":"text/markdown","content":"https://${HOST}/actuator/health failed from at least two probe regions for 5 minutes. Check the Cloud Run revision and Cloud SQL.\n\nLogs: ${LOGS_LINK}"}}
JSON
}

upsert_policy() {
    local display="$1" body="$2" existing
    existing="$(echo "$POLICIES_JSON" | find_named alertPolicies "$display")"
    if [[ -z "$existing" ]]; then
        rest POST "${API}/alertPolicies" "$body" >/dev/null
        echo "  ✓ ${display}: created"
    else
        rest PATCH "${API%/projects/*}/${existing}" "$body" >/dev/null
        echo "  ✓ ${display}: updated"
    fi
}
upsert_policy "Uncava application errors" "$(count_policy "Uncava application errors" lightmove_app_errors \
    "At least ${ERROR_THRESHOLD} ERROR log lines in $((WINDOW_SECONDS / 60)) minutes.")"
upsert_policy "Uncava 5xx responses" "$(count_policy "Uncava 5xx responses" lightmove_http_5xx \
    "At least ${ERROR_THRESHOLD} requests answered 5xx in $((WINDOW_SECONDS / 60)) minutes.")"
upsert_policy "Uncava health check failing" "$(uptime_policy)"

cat <<EOF

────────────────────────────────────────────────────────────────────────────────
  Prove it once by hand: write ${ERROR_THRESHOLD} test ERROR lines to the app log and wait for the email.

    for i in \$(seq ${ERROR_THRESHOLD}); do
      gcloud logging write run.googleapis.com/stderr "alert test \$i" --severity=ERROR --project ${PROJECT} \\
        --payload-type=text --monitored-resource-type=cloud_run_revision \\
        --monitored-resource-labels=service_name=${SERVICE},location=${REGION},revision_name=test,configuration_name=${SERVICE}
    done
────────────────────────────────────────────────────────────────────────────────
EOF
