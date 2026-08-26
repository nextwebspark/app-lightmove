#!/usr/bin/env bash
# Rebuilds app_lm_apollo_keywords from the universe. Run it after every pipeline load: the view does
# not follow app_lm_apollo_companies, so until this runs the Company Keywords box offers the
# vocabulary of the previous load.
#
# CONCURRENTLY so the box keeps answering while it rebuilds — it takes about a second on 71,822 rows.
# Must run as the view's owner, which is the role that migrated it (lm_migrate in the pipeline).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$HERE/psql.sh" -c "REFRESH MATERIALIZED VIEW CONCURRENTLY app_lm_apollo_keywords;"
