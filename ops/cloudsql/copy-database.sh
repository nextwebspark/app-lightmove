#!/usr/bin/env bash
#
# Copies one LightMove database to another Cloud SQL instance, owned by the target's migrate role.
#
#   SRC_PORT=5433 SRC_USER=lm_app SRC_PGPASSWORD=… SRC_DB=lightmove \
#   DST_PORT=5434 DST_USER=uncava_migrate DST_PGPASSWORD=… DST_DB=uncava \
#     ./ops/cloudsql/copy-database.sh schema-pre | data | sequences | schema-post | verify
#
# Run the steps in that order against an empty target that already has the `vector` extension.
#
# Why not one pg_dump | pg_restore: a dump reads every row through the forced row-level-security policies, and
# pg_dump empties search_path, so the policy function app_lm_tenant_visible cannot find app_lm_rls_maintenance()
# ("No function matches") and the dump dies on the first tenant table. The data therefore moves table by table
# with COPY on an ordinary session that raises app.rls_maintenance, between a pre-data and a post-data restore:
# the post-data section is what turns row-level security on, so it must come after the rows are in.
#
# EXCLUDE_TABLES names tables the source role cannot read (objects an operator created by hand are owned by that
# person, not by the application) and that nothing in this repository touches.
set -euo pipefail

: "${SRC_PORT:?}" "${SRC_USER:?}" "${SRC_PGPASSWORD:?}" "${SRC_DB:?}"
: "${DST_PORT:?}" "${DST_USER:?}" "${DST_PGPASSWORD:?}" "${DST_DB:?}"
EXCLUDE_TABLES="${EXCLUDE_TABLES:-}"
WORK="${WORK_DIR:-$(mktemp -d)}"

src() { PGPASSWORD="$SRC_PGPASSWORD" PGOPTIONS='-c app.rls_maintenance=on -c TimeZone=UTC' \
        psql "host=127.0.0.1 port=$SRC_PORT dbname=$SRC_DB user=$SRC_USER sslmode=disable" -v ON_ERROR_STOP=1 "$@"; }
dst() { PGPASSWORD="$DST_PGPASSWORD" PGOPTIONS='-c app.rls_maintenance=on -c TimeZone=UTC' \
        psql "host=127.0.0.1 port=$DST_PORT dbname=$DST_DB user=$DST_USER sslmode=require" -v ON_ERROR_STOP=1 "$@"; }

exclude_args=()
excluded_sql="''"
for t in $EXCLUDE_TABLES; do
    exclude_args+=(-T "$t")
    excluded_sql+=",'$t'"
done

tables() {
    src -Atc "SELECT relname FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relkind IN ('r','p')
              AND relname NOT IN ($excluded_sql) ORDER BY relname"
}

case "${1:-}" in
schema-pre|schema-post)
    section="${1#schema-}-data"
    dump="$WORK/schema.dump"
    [[ -f "$dump" ]] || PGPASSWORD="$SRC_PGPASSWORD" pg_dump \
        "host=127.0.0.1 port=$SRC_PORT dbname=$SRC_DB user=$SRC_USER sslmode=disable" \
        --schema-only -Fc --no-owner --no-acl ${exclude_args[@]+"${exclude_args[@]}"} -f "$dump"
    # Through psql, not pg_restore -d: a newer pg_dump writes SET transaction_timeout, which a Postgres 16 target
    # rejects, and COMMENT ON EXTENSION, which only the extension's owner (postgres, who created it) may run.
    pg_restore --no-owner --no-acl --section="$section" -f - "$dump" | grep -vE '^(SET transaction_timeout|COMMENT ON EXTENSION)' \
        | PGPASSWORD="$DST_PGPASSWORD" psql "host=127.0.0.1 port=$DST_PORT dbname=$DST_DB user=$DST_USER sslmode=require" \
            -v ON_ERROR_STOP=1 -q > /dev/null
    echo "✓ $section restored into $DST_DB"
    ;;
data)
    for t in $(tables); do
        cols="$(src -Atc "SELECT string_agg(quote_ident(attname), ',' ORDER BY attnum) FROM pg_attribute
                          WHERE attrelid = 'public.\"$t\"'::regclass AND attnum > 0 AND NOT attisdropped AND attgenerated = ''")"
        src -c "\\copy (SELECT $cols FROM public.\"$t\") TO STDOUT (FORMAT binary)" \
            | dst -c "\\copy public.\"$t\" ($cols) FROM STDIN (FORMAT binary)" > /dev/null
        echo "  copied $t"
    done
    echo "✓ data copied"
    ;;
sequences)
    src -Atc "SELECT format('SELECT setval(%L, %s, %s);', quote_ident(n.nspname) || '.' || quote_ident(s.relname),
                            coalesce(q.last_value, 1), (q.last_value IS NOT NULL)::text)
              FROM pg_class s
              JOIN pg_namespace n ON n.oid = s.relnamespace
              JOIN pg_sequences q ON q.schemaname = n.nspname AND q.sequencename = s.relname
              LEFT JOIN pg_depend d ON d.objid = s.oid AND d.deptype IN ('a','i') AND d.classid = 'pg_class'::regclass
              LEFT JOIN pg_class t ON t.oid = d.refobjid
              WHERE s.relkind = 'S' AND n.nspname = 'public' AND coalesce(t.relname, '') NOT IN ($excluded_sql)" \
        | dst -At > /dev/null
    echo "✓ sequences positioned"
    ;;
verify)
    # Run before any migration newer than the source's head: the checksum covers the source's columns only.
    count_sql() { echo "SELECT relname || ' ' || (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', relname), false, true, '')))[1]::text
                        FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relkind IN ('r','p')
                          AND relname NOT IN ($excluded_sql) ORDER BY relname"; }
    src -Atc "$(count_sql)" > "$WORK/src.counts"
    dst -Atc "$(count_sql)" > "$WORK/dst.counts"
    if ! diff -u "$WORK/src.counts" "$WORK/dst.counts"; then
        echo "✗ row counts differ (above)"; exit 1
    fi
    echo "✓ $(wc -l < "$WORK/src.counts" | tr -d ' ') tables, every row count identical"
    : > "$WORK/src.sums"; : > "$WORK/dst.sums"
    for t in $(tables); do
        cols="$(src -Atc "SELECT string_agg(quote_ident(attname), ',' ORDER BY attnum) FROM pg_attribute
                          WHERE attrelid = 'public.\"$t\"'::regclass AND attnum > 0 AND NOT attisdropped AND attgenerated = ''")"
        sum_sql="SELECT '$t ' || coalesce(md5(string_agg(r, '' ORDER BY r)), 'empty')
                 FROM (SELECT md5(ROW($cols)::text) AS r FROM public.\"$t\") s"
        src -Atc "$sum_sql" >> "$WORK/src.sums"
        dst -Atc "$sum_sql" >> "$WORK/dst.sums"
    done
    if diff -u "$WORK/src.sums" "$WORK/dst.sums"; then
        echo "✓ $(wc -l < "$WORK/src.sums" | tr -d ' ') tables, every row checksum identical"
    else
        echo "✗ checksums differ (above)"; exit 1
    fi
    ;;
*)
    echo "usage: $0 schema-pre | data | sequences | schema-post | verify" >&2
    exit 2
    ;;
esac
