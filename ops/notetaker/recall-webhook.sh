#!/usr/bin/env bash
# Drives the Notetaker locally, against `npm run dev`'s API and database.
#
#   ops/notetaker/recall-webhook.sh bots
#       The local recordings, newest first: id, status, start, join_at, Recall's bot id.
#
#   ops/notetaker/recall-webhook.sh send <event> <recording-id|bot-id> [sub_code | transcript_id]
#       Posts one Recall bot event, Svix-signed with application-local.yml's bot-webhook-secret, to the local
#       webhook, exactly as Recall would. Events: joining_call, in_waiting_room, in_call_recording, call_ended,
#       done, fatal, transcript.done, transcript.failed. A transcript event needs a transcript id Recall holds,
#       so the note is filed only for a bot that really recorded; the states walk for any bot.
#
#   ops/notetaker/recall-webhook.sh tunnel
#       A public https URL onto the local API, for a real bot's events and its live copilot stream. Point the
#       "Uncava local dev" Recall webhook endpoint (EU) at <url>/api/v1/notetaker/webhooks/recall and switch it on
#       (the workspace's one verification secret already signs it), then start `RECALL_PUBLIC_BASE_URL=<url> npm run
#       dev`: without it a bot sent with Live copilot is refused, its stream pointing at localhost.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
API="${API:-http://localhost:8080}"
PG_CONTAINER="${PG_CONTAINER:-lm-dev-pg}"
LOCAL_YML="$REPO_DIR/apps/api/src/main/resources/application-local.yml"

q() { docker exec "$PG_CONTAINER" psql -U lm_app -d lightmove -v ON_ERROR_STOP=1 -tAc "$1"; }

secret() {
  local value="${RECALL_BOT_WEBHOOK_SECRET:-}"
  if [ -z "$value" ]; then
    value="$(awk '/^ *recall:/ {in_recall=1; next} in_recall && /bot-webhook-secret:/ {print $2; exit}' "$LOCAL_YML")"
  fi
  [ -n "$value" ] || { echo "No bot-webhook-secret in $LOCAL_YML or RECALL_BOT_WEBHOOK_SECRET." >&2; exit 1; }
  printf '%s' "$value"
}

bot_of() {
  if [[ "$1" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
    local bot
    bot="$(q "SELECT recall_bot_id FROM app_lm_meeting_recording WHERE id = '$1'" || true)"
    [ -n "$bot" ] && { printf '%s' "$bot"; return; }
  fi
  printf '%s' "$1"
}

body_of() {
  local event="$1" bot="$2" extra="${3:-}"
  case "$event" in
    transcript.done|transcript.failed)
      [ -n "$extra" ] || { echo "$event needs a transcript id." >&2; exit 1; }
      printf '{"event":"%s","data":{"bot":{"id":"%s"},"transcript":{"id":"%s"}}}' "$event" "$bot" "$extra" ;;
    joining_call|in_waiting_room|in_call_recording|call_ended|done|fatal)
      local sub="null"
      [ -n "$extra" ] && sub="\"$extra\""
      printf '{"event":"bot.%s","data":{"bot":{"id":"%s"},"data":{"code":"%s","sub_code":%s,"updated_at":"%s"}}}' \
        "$event" "$bot" "$event" "$sub" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" ;;
    *) echo "Unknown event: $event" >&2; exit 1 ;;
  esac
}

send() {
  local event="$1" bot body id ts signature
  bot="$(bot_of "$2")"
  body="$(body_of "$event" "$bot" "${3:-}")"
  ts="$(date +%s)"
  id="msg_local_${ts}_$RANDOM"
  signature="$(SECRET="$(secret)" ID="$id" TS="$ts" BODY="$body" python3 -I -c '
import base64, hashlib, hmac, os
key = os.environ["SECRET"].removeprefix("whsec_")
signed = ".".join([os.environ["ID"], os.environ["TS"], os.environ["BODY"]])
mac = hmac.new(base64.b64decode(key), signed.encode(), hashlib.sha256)
print("v1," + base64.b64encode(mac.digest()).decode())')"
  echo "→ $event for bot $bot"
  curl -sS -o /dev/null -w "  %{http_code}\n" -X POST "$API/api/v1/notetaker/webhooks/recall" \
    -H "Content-Type: application/json" -H "webhook-id: $id" -H "webhook-timestamp: $ts" \
    -H "webhook-signature: $signature" --data "$body"
}

case "${1:-}" in
  bots)
    q "SELECT id, status, to_char(starts_at, 'DD Mon HH24:MI'), coalesce(to_char(join_at, 'DD Mon HH24:MI'), '-'),
              coalesce(recall_bot_id, '-'), coalesce(failure_reason, '')
       FROM app_lm_meeting_recording ORDER BY created_at DESC LIMIT 15" | column -t -s '|' ;;
  send)
    [ $# -ge 3 ] || { sed -n '2,20p' "$0"; exit 1; }
    send "$2" "$3" "${4:-}" ;;
  tunnel)
    exec cloudflared tunnel --url "$API" ;;
  *)
    sed -n '2,20p' "$0"; exit 1 ;;
esac
