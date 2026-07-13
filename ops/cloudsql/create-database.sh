#!/usr/bin/env bash
#
# Creates (or recreates) the LightMove database on Cloud SQL, and makes sure the humans who need to
# read it can.
#
#   ./ops/cloudsql/create-database.sh              # create if absent
#   ./ops/cloudsql/create-database.sh --recreate   # DROP and recreate — destroys all data
#
# The schema itself is not created here. Flyway applies db/migration/*.sql on the next application
# start, which is the only thing that ever defines or changes a table.
set -euo pipefail

INSTANCE="${CLOUD_SQL_INSTANCE_NAME:-bright-gcc}"
DATABASE="${DB_NAME:-lightmove}"
APP_USER="${DB_USER:-lm_app}"

# Cloud SQL IAM principals granted read access. They authenticate with their Google identity, so no
# password is ever shared. Read-only: interactive humans look, the application writes.
IAM_USERS=("${DB_IAM_USER:-nextwebspark@gmail.com}")

recreate=false
[[ "${1:-}" == "--recreate" ]] && recreate=true

if $recreate; then
    echo "⚠️  Dropping database '$DATABASE' on '$INSTANCE'. All data will be lost."
    read -r -p "    Type the database name to confirm: " confirm
    [[ "$confirm" == "$DATABASE" ]] || { echo "Aborted."; exit 1; }
    gcloud sql databases delete "$DATABASE" --instance="$INSTANCE" --quiet
fi

if gcloud sql databases describe "$DATABASE" --instance="$INSTANCE" &>/dev/null; then
    echo "✓ Database '$DATABASE' already exists."
else
    gcloud sql databases create "$DATABASE" --instance="$INSTANCE"
    echo "✓ Created database '$DATABASE'."
fi

# The application's own role. Its password lives in application-local.yml (gitignored) or, in
# production, in Secret Manager.
if gcloud sql users list --instance="$INSTANCE" --format='value(name)' | grep -qx "$APP_USER"; then
    echo "✓ App user '$APP_USER' already exists."
else
    password=$(openssl rand -base64 24 | tr -d '/+=' | head -c 28)
    gcloud sql users create "$APP_USER" --instance="$INSTANCE" --password="$password"
    echo "✓ Created app user '$APP_USER'."
    echo
    echo "  Put this in apps/api/src/main/resources/application-local.yml — it is shown once:"
    echo "      spring.datasource.password: $password"
    echo
fi

# IAM principals: registered on the *instance* here, which creates the matching Postgres role. The
# per-database GRANTs are applied by Flyway migration V2, driven by $DB_IAM_USER.
for iam_user in "${IAM_USERS[@]}"; do
    if gcloud sql users list --instance="$INSTANCE" --format='value(name)' | grep -qx "$iam_user"; then
        echo "✓ IAM user '$iam_user' already registered on the instance."
    else
        gcloud sql users create "$iam_user" --instance="$INSTANCE" --type=cloud_iam_user
        echo "✓ Registered IAM user '$iam_user'."
    fi
done

cat <<EOF

Next: start the API. Flyway applies the schema and, because DB_IAM_USER is set, grants
${IAM_USERS[0]} read access to it.

    DB_IAM_USER=${IAM_USERS[0]} npm run dev:api

Then query it as yourself, with no password:

    ./ops/cloudsql/psql.sh

Not 'gcloud sql connect': it prompts for a password, and an IAM principal has not got one — it
authenticates with an OAuth token. psql.sh runs cloud-sql-proxy --auto-iam-authn, which mints it.
EOF
