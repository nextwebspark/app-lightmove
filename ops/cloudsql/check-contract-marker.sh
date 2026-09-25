#!/usr/bin/env bash
# Fails when a migration breaks the previous release's code without saying so.
#
#   ops/cloudsql/check-contract-marker.sh <migration.sql>...
#
# Dropping, renaming, retyping or tightening to NOT NULL something the last release still reads is the
# contract half of an expand/contract change. It must carry `-- lightmove:contract`, which is what the
# Rollback workflow reads to refuse a rollback across it and what CI reads to skip the N-1 check. A hit
# that is genuinely harmless (a table created and dropped in the same file) says
# `-- lightmove:additive` instead. db-ops skill, "Rolling back".
set -euo pipefail

# Run against the statements with comments stripped and whitespace collapsed, so a statement split over
# lines still matches and a comment mentioning DROP COLUMN does not.
DESTRUCTIVE='(?i)\bdrop\s+(table|view|materialized\s+view|type|function|sequence|column)\b'
DESTRUCTIVE+='|\b(alter\s+table\s+(if\s+exists\s+)?(only\s+)?[\w."]+|,)\s*drop\s+(?!(constraint|default|not|identity|expression|column|index)\b)[\w"]+'
DESTRUCTIVE+='|\balter\s+table\s+(if\s+exists\s+)?(only\s+)?[\w."]+\s+rename\b(?!\s+constraint\b)'
DESTRUCTIVE+='|\bset\s+not\s+null\b'
DESTRUCTIVE+='|\balter\s+column\s+[\w"]+\s+(set\s+data\s+)?type\b'

failed=0
for f in "$@"; do
    if grep -qiE '^--[[:space:]]*lightmove:(contract|additive)([[:space:]]|$)' "$f"; then
        continue
    fi
    hits=$(sed -E 's/--.*$//' "$f" | tr -s '[:space:]' ' ' | grep -oP "$DESTRUCTIVE" || true)
    if [ -n "$hits" ]; then
        while IFS= read -r hit; do
            echo "::error file=${f}::'${hit}' breaks the code of the release before it, so rolling back past this migration would fail. Split it: stop using the column in one release and drop it in a later one, or mark this file '-- lightmove:contract' (db-ops skill, \"Rolling back\")."
        done <<< "$hits"
        failed=1
    fi
done
exit $failed
