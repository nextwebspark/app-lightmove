#!/usr/bin/env bash
# Exports chosen executives as golden-set rows (JSONL) for the nationality eval — NationalityEval,
# apps/api/src/test/java/app/lightmove/api/eval. These are real people's profiles: the file must stay
# out of the repository, so it is written outside it or under the gitignored eval-data/.
#
#   ops/eval/export-golden.sh <output.jsonl> <candidate-id> [<candidate-id> ...]
#
# Reads the local dev database by default (the lm-dev-pg container `npm run dev` starts). To read
# another one, set PSQL to a full psql command, e.g. PSQL="psql postgresql://user@host/lightmove".
#
# Only the columns CandidateDossier allows leave the database: no contact, compensation, note or
# custom field. Every row comes out as a model_draft with no nationality label: a person fills
# `expected` in and sets labelSource to human_confirmed, or to known where the nationality is actually
# known. A seniority a researcher recorded (not one the AI proposed) is carried as the draft label.
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <output.jsonl> <candidate-id> [<candidate-id> ...]" >&2
  exit 2
fi

output="$1"
shift

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
output_dir="$(cd "$(dirname "$output")" 2>/dev/null && pwd || true)"
if [ -z "$output_dir" ]; then
  echo "The directory of $output does not exist." >&2
  exit 2
fi
case "$output_dir/" in
  "$repo_root/eval-data/"*) ;;
  "$repo_root/"*)
    echo "Refusing to write real profiles inside the repository — use a path outside it or under eval-data/." >&2
    exit 2
    ;;
esac

for id in "$@"; do
  if ! [[ "$id" =~ ^[0-9a-fA-F-]{36}$ ]]; then
    echo "Not a candidate id: $id" >&2
    exit 2
  fi
done
ids="$(printf "'%s'," "$@")"
ids="${ids%,}"

read -r -a psql_command <<< "${PSQL:-docker exec -i ${PG_CONTAINER:-lm-dev-pg} psql -U lm_app -d lightmove}"

"${psql_command[@]}" -v ON_ERROR_STOP=1 -tA <<SQL > "$output"
SELECT json_build_object(
         'id', m.id,
         'labelSource', 'model_draft',
         'note', '',
         'profile', json_build_object(
             'fullName', c.full_name,
             'title', c.title,
             'companyName', m.company_name,
             'locationCity', c.location_city,
             'locationCountry', c.location_country,
             'summary', c.summary,
             'career', coalesce(c.profile -> 'career', '[]'::jsonb),
             'education', coalesce(c.profile -> 'education', '[]'::jsonb),
             'skills', coalesce(c.profile -> 'skills', '[]'::jsonb),
             'languages', coalesce(c.profile -> 'languages', '[]'::jsonb)),
         'expected', json_build_object(
             'nationality', NULL,
             'seniority', CASE
                 WHEN c.ai_inferred_fields ? 'seniority' THEN NULL
                 ELSE CASE c.seniority_level
                     WHEN 'BOARD' THEN 'Board'
                     WHEN 'C_SUITE' THEN 'C-Suite'
                     WHEN 'N_MINUS_1' THEN 'N-1'
                     WHEN 'N_MINUS_2' THEN 'N-2'
                     WHEN 'N_MINUS_3' THEN 'N-3'
                 END
             END))
FROM app_lm_project_candidate m
JOIN app_lm_person c ON c.id = m.person_id
WHERE m.id IN ($ids)
ORDER BY c.full_name;
SQL

echo "Wrote $(wc -l < "$output" | tr -d ' ') rows to $output. Label every row before running the eval." >&2
