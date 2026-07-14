#!/usr/bin/env bash
#
# Splits the database in two: a role that can change the schema, and a role that can only use it.
#
#   ./ops/cloudsql/create-migrate-role.sh            # show what it would do
#   ./ops/cloudsql/create-migrate-role.sh --apply    # do it
#
# NOT YET RUN against bright-gcc, and NOT YET CORRECT — see the warning below. Until it is, migrations
# run as lm_app (see ops/gcp/deploy.sh and the DB_MIGRATE_USER variable in .github/workflows/deploy.yml),
# which owns everything and works today.
#
# ⚠ THE REASSIGN BELOW WILL FAIL AS WRITTEN.
#
# It connects as `postgres` on the assumption that postgres is a superuser. On Cloud SQL it is not
# (`rolsuper = f`), and it is not a member of lm_app either — the two are siblings under
# cloudsqlsuperuser, which confers nothing between them. REASSIGN OWNED requires membership in *both*
# the old and the new role, so postgres can reassign neither. grant-db-user.sql already found this and
# says so; this script has not been reconciled with it.
#
# The fix is to run as a role that is a member of both. lm_app is a member of cloudsqlsuperuser (until
# harden.sql strips it), so the likely shape is: GRANT lm_migrate TO lm_app, then REASSIGN as lm_app.
# That has not been proved, so do not run this against a live database until it has been rehearsed
# against a throwaway Postgres.
#
# ── Why ───────────────────────────────────────────────────────────────────────
#
# The application used to migrate itself at boot. That forced lm_app — the *runtime* role, the one on the
# other end of any SQL injection we ever ship — to hold CREATE ON SCHEMA public for as long as the service
# ran. harden.sql revokes exactly that in step 4 and says in its own comments that it cannot be applied
# while the application migrates itself. The two were mutually exclusive, and hardening lost.
#
# ── Why this is more than one GRANT ───────────────────────────────────────────
#
# lm_app currently *owns* all nine tables and flyway_schema_history. Ownership, not privilege, is what
# governs DDL in Postgres, so simply granting lm_migrate CREATE would leave it unable to do the two things
# it exists to do:
#
#   - write flyway_schema_history, which lm_app owns — Flyway fails on its very first run; and
#   - ALTER any existing table, because only an owner may ALTER.
#
# And the mirror image: a table CREATEd by lm_migrate is owned by lm_migrate, and lm_app would have no
# rights on it at all. The next migration would add a table the application cannot read.
#
# So ownership moves to lm_migrate, and lm_app is granted DML — on what exists now, and, via ALTER DEFAULT
# PRIVILEGES, on everything lm_migrate creates from here on. That last line is the one people forget, and
# they find out when a migration ships a table nobody can query.
set -euo pipefail

INSTANCE="${CLOUD_SQL_INSTANCE_NAME:-bright-gcc}"
DATABASE="${DB_NAME:-lightmove}"
APP_USER="${DB_USER:-lm_app}"
MIGRATE_USER="${DB_MIGRATE_USER:-lm_migrate}"

apply=false
[[ "${1:-}" == "--apply" ]] && apply=true

# Written to a file rather than captured in $( ... ). macOS still ships bash 3.2, whose parser mishandles
# a heredoc inside a command substitution when the body contains an apostrophe — and this one does.
SQL_FILE="$(mktemp -t lm-migrate-role)"
trap 'rm -f "$SQL_FILE"' EXIT

cat > "$SQL_FILE" <<SQL
BEGIN;

-- Ownership of the schema's objects moves wholesale. REASSIGN OWNED is the only statement that catches
-- the tables, their sequences, their indexes and flyway_schema_history in one pass — enumerating them by
-- hand is how one gets missed and a migration fails six weeks later on the one table nobody listed.
REASSIGN OWNED BY ${APP_USER} TO ${MIGRATE_USER};

-- lm_app can now read and write rows, and nothing else. No CREATE, no ALTER, no DROP.
GRANT USAGE ON SCHEMA public TO ${APP_USER};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${APP_USER};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${APP_USER};

-- The line that is always forgotten. Without it the grants above cover only today's tables: the next
-- migration creates one owned by lm_migrate, lm_app holds nothing on it, and the application 500s on a
-- table that exists and looks fine.
ALTER DEFAULT PRIVILEGES FOR ROLE ${MIGRATE_USER} IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${APP_USER};
ALTER DEFAULT PRIVILEGES FOR ROLE ${MIGRATE_USER} IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ${APP_USER};

-- The point of all of it. lm_app can no longer change the shape of the database — which is what lets
-- harden.sql's step 4 finally be applied.
REVOKE CREATE ON SCHEMA public FROM ${APP_USER};

COMMIT;
SQL

if ! $apply; then
    echo "Would create role '${MIGRATE_USER}' on ${INSTANCE}/${DATABASE} and run:"
    echo
    cat "$SQL_FILE"
    echo
    echo "Re-run with --apply to do it. Then: harden.sql, and set DB_MIGRATE_USER=${MIGRATE_USER}."
    exit 0
fi

if gcloud sql users list --instance="$INSTANCE" --format='value(name)' | grep -qx "$MIGRATE_USER"; then
    echo "✓ Role '$MIGRATE_USER' already exists."
else
    PASSWORD="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
    gcloud sql users create "$MIGRATE_USER" --instance="$INSTANCE" --password="$PASSWORD"
    echo "✓ Created role '$MIGRATE_USER'."
    echo
    echo "  Password (shown once — store it now, and nowhere else):"
    echo "    $PASSWORD"
    echo
    echo "    gcloud secrets versions add lightmove-flyway-password --data-file=- <<< '$PASSWORD'"
    echo
fi

# As postgres: only a superuser can REASSIGN OWNED between two roles it is not.
gcloud sql connect "$INSTANCE" --user=postgres --database="$DATABASE" --quiet < "$SQL_FILE"

echo
echo "✓ ${MIGRATE_USER} owns the schema. ${APP_USER} has DML and no DDL."
echo
echo "  Next:"
echo "    gcloud sql connect $INSTANCE --user=postgres --database=$DATABASE < ops/cloudsql/harden.sql"
echo "    …and set the GitHub variable DB_MIGRATE_USER=${MIGRATE_USER}"
