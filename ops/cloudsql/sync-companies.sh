#!/usr/bin/env bash
#
# Copies the company universe from the brightdata warehouse into LightMove.
#
#   ./ops/cloudsql/sync-companies.sh              # refresh app_lm_companies
#   ./ops/cloudsql/sync-companies.sh --keep-dump  # leave the CSV in GCS for inspection
#
# brightdata.app_companies -> lightmove.app_lm_companies. One direction, always. Re-run it whenever the
# pipeline rebuilds the warehouse: it upserts on (source, source_id), so running it twice changes nothing.
#
# Why it goes out through GCS rather than straight over a connection, which would be the obvious thing:
# brightdata.app_companies is owned by postgres and carries no grants at all. No other role can read it —
# not your IAM identity, and not lm_app, whose cloudsqlsuperuser membership does not cross table
# ownership. `gcloud sql export` sidesteps that entirely by running server-side as the instance's service
# agent, authenticated by your gcloud credentials rather than by any database password.
#
# That service agent needs to be able to write to the bucket, once:
#
#   SA=$(gcloud sql instances describe bright-gcc --format='value(serviceAccountEmailAddress)')
#   gcloud storage buckets add-iam-policy-binding gs://<bucket> \
#       --member="serviceAccount:$SA" --role=roles/storage.objectAdmin
#
# The psql half connects as lm_app and needs CREATE on the public schema for the staging table. That is
# true today; harden.sql step 4 takes CREATE away, and from then on this wants the lm_migrate role that
# harden.sql's deploy note describes.
set -euo pipefail

INSTANCE_NAME="${CLOUD_SQL_INSTANCE_NAME:-bright-gcc}"
CONNECTION="${CLOUD_SQL_CONNECTION_NAME:-hak-talent-mapping:us-central1:bright-gcc}"
SOURCE_DB="${SOURCE_DB_NAME:-brightdata}"
TARGET_DB="${DB_NAME:-lightmove}"
APP_USER="${DB_USER:-lm_app}"
BUCKET="${SYNC_BUCKET:-gs://run-sources-hak-talent-mapping-us-central1}"
PORT="${PROXY_PORT:-5434}"
URI="$BUCKET/lightmove-sync/app_companies.csv"

keep_dump=false
[[ "${1:-}" == "--keep-dump" ]] && keep_dump=true

# Homebrew keeps libpq keg-only, so psql is not on PATH by default.
export PATH="/opt/homebrew/opt/libpq/bin:$PATH"
command -v psql >/dev/null || { echo "psql not found. brew install libpq"; exit 1; }

if [[ -z "${PGPASSWORD:-}" ]]; then
    read -r -s -p "Password for $APP_USER: " PGPASSWORD; echo
    export PGPASSWORD
fi

# The 35 columns of brightdata.app_companies, minus its id — LightMove mints its own (see V3), and
# synced_at defaults. Listed explicitly rather than SELECT *: if the pipeline adds or reorders a column
# upstream, this must fail loudly, not quietly load values into the wrong fields.
COLS="source,source_id,company_id,name,slogan,linkedin_url,website,domain,logo,\
primary_industry,industry_tags,sic_codes,sic_labels,specialties,\
org_type,ownership,ipo_status,is_public,\
revenue_usd,revenue_range,revenue_source,revenue_is_floor,\
employee_count,employee_range,employee_source,\
hq_country,hq_city,markets,\
description,founded,followers,gd_rating,gd_reviews,search_text,built_at"

echo "1/4  Exporting $SOURCE_DB.app_companies to $URI…"
gcloud sql export csv "$INSTANCE_NAME" "$URI" --database="$SOURCE_DB" --quiet \
    --query="SELECT ${COLS//,/, } FROM app_companies"

# Staging table, not the real one: import csv can only COPY rows in, and a straight COPY into
# app_lm_companies would duplicate every company on the second run. The upsert below is what makes this
# script safe to re-run, and it needs the incoming rows somewhere to be compared against.
echo "2/4  Preparing staging table…"
proxy_log=$(mktemp)
cloud-sql-proxy "$CONNECTION" --port "$PORT" > "$proxy_log" 2>&1 &
proxy_pid=$!
trap 'kill "$proxy_pid" 2>/dev/null; rm -f "$proxy_log"' EXIT

for _ in {1..20}; do
    grep -q "ready for new connections" "$proxy_log" && break
    sleep 0.5
done
grep -q "ready for new connections" "$proxy_log" || { echo "Proxy failed to start:"; cat "$proxy_log"; exit 1; }

CONN="host=127.0.0.1 port=$PORT dbname=$TARGET_DB user=$APP_USER"

psql "$CONN" -v ON_ERROR_STOP=1 -q <<'SQL'
DROP TABLE IF EXISTS app_lm_companies_load;
CREATE TABLE app_lm_companies_load (LIKE app_lm_companies EXCLUDING ALL);
ALTER TABLE app_lm_companies_load DROP COLUMN id, DROP COLUMN synced_at;
SQL

echo "3/4  Importing into staging…"
gcloud sql import csv "$INSTANCE_NAME" "$URI" --database="$TARGET_DB" \
    --table=app_lm_companies_load --columns="$COLS" --quiet

# One transaction: until it commits, readers keep seeing the previous universe.
#
# The xmax trick distinguishes an insert from an update — on a row ON CONFLICT has just inserted, xmax is
# 0; on one it updated, xmax carries the id of the updating transaction.
echo "4/4  Upserting into app_lm_companies…"
psql "$CONN" -v ON_ERROR_STOP=1 <<SQL
BEGIN;

WITH upserted AS (
    INSERT INTO app_lm_companies (${COLS//,/, }, synced_at)
    SELECT ${COLS//,/, }, now() FROM app_lm_companies_load
    ON CONFLICT (source, source_id) DO UPDATE SET
        company_id       = EXCLUDED.company_id,
        name             = EXCLUDED.name,
        slogan           = EXCLUDED.slogan,
        linkedin_url     = EXCLUDED.linkedin_url,
        website          = EXCLUDED.website,
        domain           = EXCLUDED.domain,
        logo             = EXCLUDED.logo,
        primary_industry = EXCLUDED.primary_industry,
        industry_tags    = EXCLUDED.industry_tags,
        sic_codes        = EXCLUDED.sic_codes,
        sic_labels       = EXCLUDED.sic_labels,
        specialties      = EXCLUDED.specialties,
        org_type         = EXCLUDED.org_type,
        ownership        = EXCLUDED.ownership,
        ipo_status       = EXCLUDED.ipo_status,
        is_public        = EXCLUDED.is_public,
        revenue_usd      = EXCLUDED.revenue_usd,
        revenue_range    = EXCLUDED.revenue_range,
        revenue_source   = EXCLUDED.revenue_source,
        revenue_is_floor = EXCLUDED.revenue_is_floor,
        employee_count   = EXCLUDED.employee_count,
        employee_range   = EXCLUDED.employee_range,
        employee_source  = EXCLUDED.employee_source,
        hq_country       = EXCLUDED.hq_country,
        hq_city          = EXCLUDED.hq_city,
        markets          = EXCLUDED.markets,
        description      = EXCLUDED.description,
        founded          = EXCLUDED.founded,
        followers        = EXCLUDED.followers,
        gd_rating        = EXCLUDED.gd_rating,
        gd_reviews       = EXCLUDED.gd_reviews,
        search_text      = EXCLUDED.search_text,
        built_at         = EXCLUDED.built_at
    RETURNING (xmax = 0) AS was_insert
)
SELECT count(*) FILTER (WHERE was_insert)     AS inserted,
       count(*) FILTER (WHERE NOT was_insert) AS updated
FROM upserted;

-- Companies we hold that the warehouse no longer publishes. Reported, never deleted: a project may
-- already reference one, and what should happen to it is a product decision nobody has made yet.
SELECT count(*) AS gone_upstream_kept
FROM app_lm_companies c
WHERE NOT EXISTS (
    SELECT 1 FROM app_lm_companies_load l
     WHERE l.source = c.source AND l.source_id = c.source_id
);

DROP TABLE app_lm_companies_load;
COMMIT;
SQL

$keep_dump || gcloud storage rm "$URI" --quiet
echo "Done."
