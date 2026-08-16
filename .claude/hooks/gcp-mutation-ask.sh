#!/usr/bin/env bash
# PreToolUse hook: any GCP command that could change state must be approved by the user.
# Read-only gcloud/gsutil (list, describe, get, ls, cat, ...) falls through to normal permissions.
# Fail-closed: a gcloud/gsutil command not clearly read-only asks.

cmd=$(jq -r '.tool_input.command // empty' 2>/dev/null)

# Hard deny: dropping tables/databases/schemas or truncating, and GCP resource deletion —
# catches compound commands ("cd x && gcloud ... delete") the prefix deny rules cannot see.
destructive='(DROP[[:space:]]+(TABLE|DATABASE|SCHEMA)|TRUNCATE[[:space:]]|(instances|databases|backups|services|secrets|projects|service-accounts|buckets)[[:space:]]+delete|versions[[:space:]]+destroy)'
if echo "$cmd" | grep -Eiq "$destructive" && echo "$cmd" | grep -Eq 'gcloud|gsutil|psql|mysql'; then
  cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"Destructive database/GCP deletion — blocked by project policy"}}
JSON
  exit 0
fi

case "$cmd" in
  *gcloud*|*gsutil*) ;;
  *) exit 0 ;;
esac

readonly_pattern='(gcloud|gsutil)[^|;&]*[[:space:]](list|describe|get-iam-policy|config[[:space:]](get|list)|auth[[:space:]]list|version|info|help|ls|cat|stat|du)\b'
mutating_pattern='[[:space:]](create|delete|update|deploy|import|export|set-iam-policy|add-iam-policy-binding|remove-iam-policy-binding|patch|restart|failover|promote|restore|clone|cp|mv|rm|rsync|mb|rb|grant|revoke|enable|disable|activate|login)\b'

if echo "$cmd" | grep -Eq "$readonly_pattern" && ! echo "$cmd" | grep -Eq "$mutating_pattern"; then
  exit 0
fi

cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"ask","permissionDecisionReason":"GCP state-changing command — needs explicit approval (reads are free)"}}
JSON
