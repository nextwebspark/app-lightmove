#!/usr/bin/env bash
# Moves the code's own names from lightmove to uncava. Idempotent: a branch cut before the rename re-runs
# it after merging main, and a checkout re-runs it once to move its own application-local.yml.
# What is stored, signed or published keeps lightmove (CLAUDE.md, "The product is Uncava").
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

SELF="ops/dev/rename-uncava.sh"

for root in apps/api/src/main/java apps/api/src/test/java; do
    if [ -d "$root/app/lightmove" ] && [ ! -e "$root/app/uncava" ]; then
        git mv "$root/app/lightmove" "$root/app/uncava"
    fi
done

renamed_path() {
    printf '%s' "$1" | sed -E \
        -e 's#/app/lightmove/#/app/uncava/#' \
        -e 's#LightMove#Uncava#g' \
        -e 's#lightMove#uncava#g' \
        -e 's#lightmove-(domain|llm)#uncava-\1#g'
}

move_all() {
    while IFS= read -r -d '' path; do
        target=$(renamed_path "$path")
        [ "$target" = "$path" ] && continue
        mkdir -p "$(dirname "$target")"
        "$@" "$path" "$target"
    done
}

git ls-files -z | move_all git mv --
git ls-files -z -o --exclude-standard | move_all mv --
find apps/api/src -depth -type d -path '*/app/lightmove*' -empty -delete
find .claude/skills -depth -type d -name lightmove-domain -empty -delete

files=()
while IFS= read -r -d '' file; do
    files+=("$file")
done < <(git grep -I -l -z --untracked -E 'lightmove|LightMove|LIGHTMOVE|lightMove' -- . \
    ':(exclude)apps/api/src/main/resources/db/migration' \
    ':(exclude)e2e/results' \
    ":(exclude)$SELF" || true)
for local_yml in apps/api/src/main/resources/application-local.yml; do
    [ -f "$local_yml" ] && files+=("$local_yml")
done
[ "${#files[@]}" -eq 0 ] && { echo "Nothing left to rename."; exit 0; }

# Each lookahead name is stored or published: a browser or extension storage key, a channel, a template
# file's format marker, an error or schema URI, or an attribute key of a persisted OAuth client.
KEEP='position-templates|client\.|loaded-hashes|mcp\.(?:caller|origin)|promptId|app\b|returnTo|oauth\b|mailbox\b|position\.receipts|positionsOrigin|sourcing\.dismissed|settings\b|session\b|lastProjectId|lastRead|connectReturnTab'

KEEP="$KEEP" perl -pi -e '
    BEGIN { $keep = $ENV{KEEP} }
    my $f = $ARGV;
    s/\bapp\.lightmove\b/app.uncava/g;
    s{\bapp/lightmove/}{app/uncava/}g;
    s/LightMove(Application|Properties|ApiClient)/Uncava$1/g;
    s/lightMove(ApiClient|PageReader|Properties)/uncava$1/g;
    s/LIGHTMOVE_/UNCAVA_/g;
    s/\b([Aa]) LightMove\b/$1n LightMove/g;
    s/\bLightMove\b/Uncava/g unless $f eq "docs/glossary.md";
    s/\bapp-lightmove\b/app-uncava/g;
    s/\blightmove-(domain|template-library\.json|position-templates\.json|import-template\.csv|capture-|emit-manifest|public-base-url|llm\.postman)/uncava-$1/g;
    s/\blightmove:scan\b/uncava:scan/g;
    s/prefix = "lightmove"/prefix = "uncava"/g;
    if ($f =~ m{^apps/api/} || $f eq "docs/production-observability.md") {
        s/\blightmove-api\b(?!@)/uncava-api/g;
    }
    if ($f =~ m{^apps/api/.*\.ya?ml(\.example)?$}) {
        s/^lightmove:/uncava:/;
    }
    if ($f =~ m{^apps/(web|extension)/}) {
        s/\blightmove\.(web\.cors-allowed-origins|position\.document\.max-file-size-bytes)/uncava.$1/g;
    } elsif ($f !~ m{^(ops/cloudsql|claude-design)/}) {
        s/(?<![\w.-])lightmove\.(?!$keep)/uncava./g;
    }
' "${files[@]}"

echo "Renamed. What still says lightmove is meant to:"
git grep -n -I --untracked -E 'lightmove|LightMove|LIGHTMOVE|lightMove' -- . \
    ':(exclude)apps/api/src/main/resources/db/migration' ':(exclude)e2e/results' ":(exclude)$SELF" \
    | wc -l
