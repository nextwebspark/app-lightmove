#!/usr/bin/env bash
#
# Deploy from a laptop. Same image, same migration step, same env, same secret mounts as
# .github/workflows/deploy.yml — deliberately, so that what you prove here is what CI will do.
#
#   ./ops/gcp/deploy.sh              # build, migrate, deploy, smoke-test
#   ./ops/gcp/deploy.sh --dry-run    # print what it would do and stop
#
# This exists for the FIRST deploy. Doing it by hand once proves the GCP wiring — service accounts,
# secrets, Cloud SQL, the image — before you trust a pipeline you have never watched run. Otherwise your
# first pipeline run is also your first deploy, and a red X could be WIF, IAM, the migration role, or the
# application, on a four-minute feedback loop.
#
# After it works: merge to main and let .github/workflows/deploy.yml own every deploy from then on.
set -euo pipefail

PROJECT="${GCP_PROJECT:-hak-talent-mapping}"
REGION="${GCP_REGION:-us-central1}"
SERVICE="${SERVICE:-lightmove}"
AR_REPO="${AR_REPO:-lightmove}"
DATABASE="${DB_NAME:-lightmove}"
CLOUD_SQL_INSTANCE="${CLOUD_SQL_INSTANCE:-hak-talent-mapping:us-central1:bright-gcc}"
RUNTIME_SA="lightmove-api@${PROJECT}.iam.gserviceaccount.com"

# lm_app today. Becomes lm_migrate once ops/cloudsql/create-migrate-role.sh has been applied.
MIGRATE_USER="${DB_MIGRATE_USER:-lm_app}"

# Defaults to resend + the verified lightmove.ai sender, so a plain run sends real mail. Requires the
# lightmove-resend-api-key secret to have a value (preflight below fails fast if it does not). Override
# EMAIL_PROVIDER=log for a dry-run environment that should not send.
EMAIL_PROVIDER="${EMAIL_PROVIDER:-resend}"
EMAIL_FROM="${EMAIL_FROM:-noreply@lightmove.ai}"
GOOGLE_OAUTH_CLIENT_ID="${GOOGLE_OAUTH_CLIENT_ID:-}"

# ⚠ Verification off. Every signup is treated as though the address had been proved.
#
# It exists because EMAIL_PROVIDER=log puts the verification link in Cloud Logging rather than an inbox,
# which makes testing a signup tedious. The cost is the entire trust model: the email domain is the only
# evidence LightMove has that someone works at a firm, so with this on, anyone can sign up as
# ceo@goldmansachs.com — never touching that mailbox — and immediately own the workspace bound to
# goldmansachs.com, with the right to invite people into it and approve others.
#
# It also means the held-wizard path never runs, because nobody is ever unverified. Staging tests a flow
# production does not have.
#
# Tolerable while there are no real users and no real data. Turn it off before there are. The service
# logs a WARN on every signup while it is on, and so does this script.
AUTO_VERIFY_EMAIL="${AUTO_VERIFY_EMAIL:-false}"

# Production limits by default. A tester creating half a dozen accounts in a row will trip the signup one
# on their fourth attempt, which is exactly what it is for and exactly what you do not want on staging.
# Note these are per Cloud Run *instance* and are wiped by a cold start — a speed bump, not a quota.
RATE_LIMIT_ENABLED="${RATE_LIMIT_ENABLED:-true}"
LOGIN_ATTEMPTS_PER_MINUTE="${LOGIN_ATTEMPTS_PER_MINUTE:-10}"
SIGNUP_ATTEMPTS_PER_HOUR="${SIGNUP_ATTEMPTS_PER_HOUR:-5}"
VERIFICATION_RESENDS_PER_HOUR="${VERIFICATION_RESENDS_PER_HOUR:-3}"
# 0 ignores X-Forwarded-For entirely and trusts the socket peer. Wrong-but-safe: every caller shares one
# rate-limit bucket. Setting it too HIGH is the dangerous direction — the header becomes attacker-supplied
# and the per-IP budget is free to bypass. Measure it (see the README) before raising it.
TRUSTED_PROXY_COUNT="${TRUSTED_PROXY_COUNT:-0}"

dry_run=false
[[ "${1:-}" == "--dry-run" ]] && dry_run=true

SHA="$(git rev-parse --short HEAD)"
# An image built from uncommitted work is not that commit, and tagging it as though it were is the exact
# lie the SHA tag exists to prevent — you would later roll "back" to a commit that never contained the
# code you are running. Say so in the tag instead.
[[ -n "$(git status --porcelain)" ]] && SHA="${SHA}-dirty"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT}/${AR_REPO}/${SERVICE}:${SHA}"

say() { printf '\n\033[1m▸ %s\033[0m\n' "$1"; }
die() { printf '\n\033[31m✗ %s\033[0m\n' "$1" >&2; exit 1; }

# ── Preflight ─────────────────────────────────────────────────────────────────
# Fail here, with a sentence, rather than four minutes in with a gcloud stack trace.
say "Preflight"

[[ "$SHA" == *-dirty ]] && echo "  ! working tree is dirty — tagging ${SHA}. Fine for a first manual deploy; commit before you rely on rollback."

for secret in lightmove-db-password lightmove-jwt-private-key lightmove-jwt-public-key lightmove-flyway-password; do
    gcloud secrets versions describe latest --secret="$secret" --project="$PROJECT" &>/dev/null \
        || die "Secret '${secret}' has no version. Run ops/gcp/bootstrap.sh, then add the values it prints."
done
echo "  ✓ all required secrets have a value"

gcloud iam service-accounts describe "$RUNTIME_SA" --project="$PROJECT" &>/dev/null \
    || die "Runtime service account is missing. Run ops/gcp/bootstrap.sh first."
echo "  ✓ runtime service account exists"

if [ "$EMAIL_PROVIDER" = "resend" ]; then
    gcloud secrets versions describe latest --secret=lightmove-resend-api-key --project="$PROJECT" &>/dev/null \
        || die "EMAIL_PROVIDER=resend but lightmove-resend-api-key has no value. The app fails fast at startup on this — better to fail here."
fi

if $dry_run; then
    echo
    echo "  image        ${IMAGE}"
    echo "  migrate as   ${MIGRATE_USER}"
    echo "  email        ${EMAIL_PROVIDER}"
    echo "  google oauth $([ -n "$GOOGLE_OAUTH_CLIENT_ID" ] && echo enabled || echo 'not configured (button stays hidden)')"
    echo
    exit 0
fi

# ── Build ─────────────────────────────────────────────────────────────────────
# Tagged with the git SHA, never `latest`: you must be able to say which commit is serving, and roll back
# to a specific one. `latest` answers neither question.
say "Build and push  ${IMAGE}"
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet >/dev/null
docker build --platform linux/amd64 -t "$IMAGE" .
docker push "$IMAGE"

# ── Migrate ───────────────────────────────────────────────────────────────────
# Before the new revision goes live, and never from inside the container (FLYWAY_ENABLED=false). A failed
# migration stops the deploy and the current revision keeps serving — which is the entire reason this is
# a step rather than something the application does to itself at boot.
say "Migrate the database"
FLYWAY_PASSWORD="$(gcloud secrets versions access latest --secret=lightmove-flyway-password --project="$PROJECT")"

cloud-sql-proxy --port 5432 "$CLOUD_SQL_INSTANCE" >/tmp/lm-proxy.log 2>&1 &
PROXY_PID=$!
trap 'kill $PROXY_PID 2>/dev/null || true' EXIT
for _ in $(seq 1 30); do nc -z 127.0.0.1 5432 2>/dev/null && break; sleep 1; done
nc -z 127.0.0.1 5432 2>/dev/null || die "cloud-sql-proxy never came up. See /tmp/lm-proxy.log"

# --platform: Redgate publish no arm64 build of the Flyway image, so on Apple Silicon it must be emulated.
#
# host.docker.internal, not 127.0.0.1: the proxy runs on the host and Flyway runs in a container, and on
# macOS `--network host` does not bridge the two.
docker run --rm --platform linux/amd64 --add-host=host.docker.internal:host-gateway \
    -v "$PWD/apps/api/src/main/resources/db/migration:/flyway/sql:ro" \
    redgate/flyway:11 \
    -url="jdbc:postgresql://host.docker.internal:5432/${DATABASE}" \
    -user="$MIGRATE_USER" \
    -password="$FLYWAY_PASSWORD" \
    -baselineOnMigrate=true \
    -placeholders.iam_user= \
    -connectRetries=5 \
    migrate

kill $PROXY_PID 2>/dev/null || true
trap - EXIT

# ── Deploy ────────────────────────────────────────────────────────────────────
say "Deploy to Cloud Run"

# WEB_BASE_URL is the service's own URL, which does not exist until the service does. First run therefore
# boots on a placeholder and is corrected below; every run after that already knows it.
KNOWN_URL="$(gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" \
    --format='value(status.url)' 2>/dev/null || true)"
BASE_URL="${KNOWN_URL:-http://localhost:8080}"

# Google sign-in is wired only when it is actually configured. Spring validates every declared
# registration at startup and REJECTS an empty client-id, so passing these through blank would mean the
# service refuses to boot rather than simply not offering the button.
OAUTH_ENV=""
OAUTH_SECRETS=""
if [ -n "$GOOGLE_OAUTH_CLIENT_ID" ]; then
    OAUTH_ENV="|SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=${GOOGLE_OAUTH_CLIENT_ID}"
    OAUTH_ENV="${OAUTH_ENV}|SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=openid,email,profile"
    OAUTH_ENV="${OAUTH_ENV}|SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=${BASE_URL}/login/oauth2/code/google"
    OAUTH_SECRETS=",SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=lightmove-google-oauth-client-secret:latest"
fi

EMAIL_SECRETS=""
[ "$EMAIL_PROVIDER" = "resend" ] && EMAIL_SECRETS=",RESEND_API_KEY=lightmove-resend-api-key:latest"

if [ "$AUTO_VERIFY_EMAIL" = "true" ]; then
    printf '\n\033[31m  ⚠  AUTO_VERIFY_EMAIL=true — email verification is OFF on this deployment.\033[0m\n'
    printf '\033[31m     Anyone may claim any company domain. Do not leave this on with real users.\033[0m\n\n'
fi

# ^|^ makes | the separator instead of a comma, because WEB_CORS_ORIGINS is a comma-separated list and
# the OAuth scope is another. It must be a character that appears in NO value — and @ (the obvious pick)
# is wrong: EMAIL_FROM is an email address, so gcloud splits it mid-way and rejects `lightmove.app` as a
# malformed entry. A pipe cannot occur in a URL, an address, or a file path.
#
# max-instances 2, NOT the default of 100: 2 × DB_POOL_MAX=5 = 10 connections, under the db-f1-micro's
# ~25 — which the brightdata ETL and gth-api also draw on. Raise it and you take down the neighbours.
gcloud run deploy "$SERVICE" \
    --project "$PROJECT" \
    --image "$IMAGE" \
    --region "$REGION" \
    --service-account "$RUNTIME_SA" \
    --allow-unauthenticated \
    --min-instances 0 \
    --max-instances 2 \
    --cpu 1 --memory 1Gi --cpu-boost \
    --concurrency 80 \
    --timeout 60s \
    --set-env-vars "^|^FLYWAY_ENABLED=false|MANAGEMENT_PORT=8080|DB_POOL_MAX=5|EMAIL_PROVIDER=${EMAIL_PROVIDER}|EMAIL_FROM=${EMAIL_FROM}|WEB_BASE_URL=${BASE_URL}|WEB_CORS_ORIGINS=${BASE_URL}|LIGHTMOVE_AUTH_AUTO_VERIFY_EMAIL=${AUTO_VERIFY_EMAIL}|AUTH_RATE_LIMIT_ENABLED=${RATE_LIMIT_ENABLED}|AUTH_LOGIN_ATTEMPTS_PER_MINUTE=${LOGIN_ATTEMPTS_PER_MINUTE}|AUTH_SIGNUP_ATTEMPTS_PER_HOUR=${SIGNUP_ATTEMPTS_PER_HOUR}|AUTH_VERIFICATION_RESENDS_PER_HOUR=${VERIFICATION_RESENDS_PER_HOUR}|LIGHTMOVE_WEB_TRUSTED_PROXY_COUNT=${TRUSTED_PROXY_COUNT}|JWT_PRIVATE_KEY_LOCATION=file:/secrets/jwt-private/private.pem|JWT_PUBLIC_KEY_LOCATION=file:/secrets/jwt-public/public.pem${OAUTH_ENV}" \
    --set-secrets "DB_PASSWORD=lightmove-db-password:latest,/secrets/jwt-private/private.pem=lightmove-jwt-private-key:latest,/secrets/jwt-public/public.pem=lightmove-jwt-public-key:latest${EMAIL_SECRETS}${OAUTH_SECRETS}"

URL="$(gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" --format='value(status.url)')"

# First deploy only. Verification emails and the OAuth redirect are built from WEB_BASE_URL, so a
# placeholder cannot be left in place — every link in every signup email would point at localhost.
if [ -z "$KNOWN_URL" ]; then
    say "First deploy — correcting WEB_BASE_URL to ${URL}"
    gcloud run services update "$SERVICE" --region "$REGION" --project "$PROJECT" \
        --update-env-vars "^|^WEB_BASE_URL=${URL}|WEB_CORS_ORIGINS=${URL}"
fi

# ── Prove it serves ───────────────────────────────────────────────────────────
# `gcloud run deploy` returning 0 means the revision started, not that it works. These assert the
# properties this whole arrangement exists to preserve.
say "Smoke test"
curl -fsS --retry 5 --retry-delay 3 "$URL/actuator/health" >/dev/null && echo "  ✓ healthy"
curl -fsS "$URL/" | grep -q '<div id="root">' && echo "  ✓ the SPA is in the image and served at /"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$URL/auth/verify?token=x")" = "200" ] && echo "  ✓ history fallback (the URL in every verification email)"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$URL/actuator/prometheus")" = "401" ] && echo "  ✓ metrics are not public"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$URL/api/v1/onboarding/workspaces")" = "401" ] && echo "  ✓ the API is still shut"

cat <<EOF

────────────────────────────────────────────────────────────────────────────────
  ${URL}

  Email is '${EMAIL_PROVIDER}'. $([ "$EMAIL_PROVIDER" = "log" ] && echo "Verification links go to Cloud Logging, not a mailbox:" || echo "Sending for real.")
$([ "$EMAIL_PROVIDER" = "log" ] && echo "
    gcloud logging read 'resource.labels.service_name=${SERVICE} AND textPayload:auth/verify' \\
        --project=${PROJECT} --limit=1 --format='value(textPayload)'
")
  Deployed ${SHA}. Roll back with:

    gcloud run services update-traffic ${SERVICE} --region ${REGION} --to-revisions=PREVIOUS=100
────────────────────────────────────────────────────────────────────────────────
EOF
