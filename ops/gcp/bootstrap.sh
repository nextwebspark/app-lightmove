#!/usr/bin/env bash
#
# Everything on GCP that has to exist before the first deploy, in one idempotent script.
#
#   ./ops/gcp/bootstrap.sh
#
# Committed and re-runnable on purpose. The alternative is a paragraph in a README describing commands
# someone once typed, which is not a record of anything — six months on, nobody can say whether the
# service account really has only the roles it should, and nobody dares find out by deleting one.
#
# It does NOT create secrets' *values*. Those are generated elsewhere and pasted in (see the end of the
# run for the exact commands): a bootstrap script that mints a signing key is a bootstrap script that
# logs one.
set -euo pipefail

PROJECT="${GCP_PROJECT:-hak-talent-mapping}"
REGION="${GCP_REGION:-us-central1}"
REPO="${GITHUB_REPO:-nextwebspark/app-lightmove}"

SERVICE="lightmove"
AR_REPO="lightmove"
RUNTIME_SA="lightmove-api"
DEPLOY_SA="lightmove-deployer"
POOL="github-pool"            # already exists, created for the other app in this project
PROVIDER="lightmove-provider" # new: the existing provider is pinned to a different repo

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
RUNTIME_SA_EMAIL="${RUNTIME_SA}@${PROJECT}.iam.gserviceaccount.com"
DEPLOY_SA_EMAIL="${DEPLOY_SA}@${PROJECT}.iam.gserviceaccount.com"

# Secrets the running service reads. Created empty here; values added by hand (see the closing notes).
RUNTIME_SECRETS=(
    lightmove-db-password
    lightmove-jwt-private-key
    lightmove-jwt-public-key
    lightmove-resend-api-key
    lightmove-google-oauth-client-secret
)
# Read only by CI, never by the service. lm_migrate can create tables; the service must not be able to.
CI_SECRETS=(lightmove-flyway-password)

say() { printf '\n\033[1m▸ %s\033[0m\n' "$1"; }

# ── APIs ──────────────────────────────────────────────────────────────────────
say "Enabling APIs"
gcloud services enable \
    run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com \
    sqladmin.googleapis.com iamcredentials.googleapis.com \
    --project="$PROJECT"

# ── Artifact Registry ─────────────────────────────────────────────────────────
say "Artifact Registry"
if gcloud artifacts repositories describe "$AR_REPO" --location="$REGION" --project="$PROJECT" &>/dev/null; then
    echo "  ✓ repository '$AR_REPO' exists"
else
    gcloud artifacts repositories create "$AR_REPO" \
        --repository-format=docker --location="$REGION" --project="$PROJECT" \
        --description="LightMove application images"
fi

# Images are tagged by git SHA and never reused, so without this the repository grows without bound and
# is billed for forever. Keep the last 10; that is more rollback range than anyone has ever needed.
cat > /tmp/lm-cleanup.json <<'JSON'
[
  {
    "name": "keep-recent",
    "action": {"type": "Keep"},
    "mostRecentVersions": {"keepCount": 10}
  },
  {
    "name": "delete-old",
    "action": {"type": "Delete"},
    "condition": {"olderThan": "30d"}
  }
]
JSON
gcloud artifacts repositories set-cleanup-policies "$AR_REPO" \
    --location="$REGION" --project="$PROJECT" --policy=/tmp/lm-cleanup.json --no-dry-run
rm -f /tmp/lm-cleanup.json

# ── Service accounts ──────────────────────────────────────────────────────────
# Two of them, and separate on purpose. The runtime identity is what an attacker who achieves RCE in the
# container inherits; it must not be able to deploy, and it must not be the project's default compute
# account, which carries Editor.
say "Service accounts"
for pair in "$RUNTIME_SA:LightMove Cloud Run runtime" "$DEPLOY_SA:LightMove GitHub Actions deployer"; do
    sa="${pair%%:*}"; desc="${pair#*:}"
    if gcloud iam service-accounts describe "${sa}@${PROJECT}.iam.gserviceaccount.com" --project="$PROJECT" &>/dev/null; then
        echo "  ✓ $sa exists"
    else
        gcloud iam service-accounts create "$sa" --display-name="$desc" --project="$PROJECT"
    fi
done

say "Runtime roles (lightmove-api)"
# cloudsql.client because the app dials Cloud SQL through the Java connector, authenticating as itself.
# The logging/monitoring roles are not optional for a custom SA: without them the service runs but is
# silent, and you debug the first incident with no logs.
for role in roles/cloudsql.client roles/logging.logWriter roles/monitoring.metricWriter; do
    gcloud projects add-iam-policy-binding "$PROJECT" \
        --member="serviceAccount:${RUNTIME_SA_EMAIL}" --role="$role" --condition=None --quiet >/dev/null
    echo "  ✓ $role"
done

say "Deploy roles (lightmove-deployer)"
for role in roles/run.admin roles/artifactregistry.writer; do
    gcloud projects add-iam-policy-binding "$PROJECT" \
        --member="serviceAccount:${DEPLOY_SA_EMAIL}" --role="$role" --condition=None --quiet >/dev/null
    echo "  ✓ $role"
done

# Scoped to the runtime SA alone, not the project. This is what lets the deployer run the service *as*
# lightmove-api — and it is the whole grant. Given project-wide, it would let CI impersonate every
# service account in the project, including ones with Editor.
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA_EMAIL" \
    --member="serviceAccount:${DEPLOY_SA_EMAIL}" --role=roles/iam.serviceAccountUser \
    --project="$PROJECT" --quiet >/dev/null
echo "  ✓ roles/iam.serviceAccountUser on ${RUNTIME_SA} only"

# ── Secrets ───────────────────────────────────────────────────────────────────
say "Secrets"
for secret in "${RUNTIME_SECRETS[@]}" "${CI_SECRETS[@]}"; do
    if gcloud secrets describe "$secret" --project="$PROJECT" &>/dev/null; then
        echo "  ✓ $secret exists"
    else
        gcloud secrets create "$secret" --replication-policy=automatic --project="$PROJECT"
    fi
done

# secretAccessor per secret, never project-wide. Project-wide would hand the running container every
# secret in the project — including the other application's database password, which it has no business
# being able to read.
for secret in "${RUNTIME_SECRETS[@]}"; do
    gcloud secrets add-iam-policy-binding "$secret" \
        --member="serviceAccount:${RUNTIME_SA_EMAIL}" --role=roles/secretmanager.secretAccessor \
        --project="$PROJECT" --quiet >/dev/null
done
echo "  ✓ runtime SA can read its 5 secrets — and no others"

# The Flyway password is CI's, not the service's. Granting it to the runtime SA would put a role that can
# ALTER TABLE inside the container, which is exactly what splitting the roles was for.
for secret in "${CI_SECRETS[@]}"; do
    gcloud secrets add-iam-policy-binding "$secret" \
        --member="serviceAccount:${DEPLOY_SA_EMAIL}" --role=roles/secretmanager.secretAccessor \
        --project="$PROJECT" --quiet >/dev/null
done
echo "  ✓ deployer can read lightmove-flyway-password — the runtime SA cannot"

# CI needs the app's DB password too, to smoke-test? No — it does not. It never touches it.
# CI needs cloudsql.client to run the proxy for migrations.
gcloud projects add-iam-policy-binding "$PROJECT" \
    --member="serviceAccount:${DEPLOY_SA_EMAIL}" --role=roles/cloudsql.client --condition=None --quiet >/dev/null
echo "  ✓ deployer has cloudsql.client (for the migration step)"

# ── Workload Identity Federation ──────────────────────────────────────────────
# Keyless GitHub → GCP. No JSON service-account key is ever created, so there is none to leak, rotate,
# or find in a GitHub secret three years from now.
say "Workload Identity Federation"
if ! gcloud iam workload-identity-pools describe "$POOL" --location=global --project="$PROJECT" &>/dev/null; then
    gcloud iam workload-identity-pools create "$POOL" --location=global \
        --display-name="GitHub Actions" --project="$PROJECT"
fi

# A NEW provider rather than relaxing the existing one. The pool's current provider is pinned to
# another repository's deploys; widening its condition to fit this repo would put that service one typo
# away from breaking. Providers are free.
if gcloud iam workload-identity-pools providers describe "$PROVIDER" \
        --workload-identity-pool="$POOL" --location=global --project="$PROJECT" &>/dev/null; then
    echo "  ✓ provider '$PROVIDER' exists"
else
    gcloud iam workload-identity-pools providers create-oidc "$PROVIDER" \
        --workload-identity-pool="$POOL" --location=global --project="$PROJECT" \
        --display-name="LightMove (app-lightmove)" \
        --issuer-uri="https://token.actions.githubusercontent.com" \
        --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
        --attribute-condition="assertion.repository == '${REPO}'"
fi

# Only this repository may impersonate the deployer. Without the condition above AND this binding, any
# GitHub repository in the world could mint a token for this pool.
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA_EMAIL" \
    --role=roles/iam.workloadIdentityUser --project="$PROJECT" --quiet \
    --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}/attribute.repository/${REPO}" >/dev/null
echo "  ✓ only ${REPO} may impersonate ${DEPLOY_SA}"

# ── What is left for a human ──────────────────────────────────────────────────
cat <<EOF

────────────────────────────────────────────────────────────────────────────────
Done. Three things remain, and all three are deliberately manual.

1. GitHub repository variables (Settings → Secrets and variables → Actions → Variables):

     GCP_PROJECT               ${PROJECT}
     GCP_WIF_PROVIDER          projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}/providers/${PROVIDER}
     GCP_DEPLOY_SA             ${DEPLOY_SA_EMAIL}
     GCP_RUNTIME_SA            ${RUNTIME_SA_EMAIL}

   Optional, and each one unlocks a feature rather than being required to deploy:

     EMAIL_PROVIDER            'resend' once a sending domain is verified (default: 'log')
     EMAIL_FROM                noreply@<your-domain>
     GOOGLE_OAUTH_CLIENT_ID    enables the "Continue with Google" button
     TRUSTED_PROXY_COUNT       see the README — measure it, do not guess it (default: 0)

   Variables, not secrets — none of these is one. A WIF provider path is not a credential; treating
   public identifiers as secrets only teaches people that the secrets list is full of things that
   do not matter.

2. Secret values. The JWT keypair is generated ONCE, on your machine, and never in CI:

     openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
     openssl rsa -pubout -in private.pem -out public.pem

     gcloud secrets versions add lightmove-jwt-private-key --data-file=private.pem
     gcloud secrets versions add lightmove-jwt-public-key  --data-file=public.pem
     shred -u private.pem public.pem     # they are in Secret Manager now; a second copy is a second leak

     gcloud secrets versions add lightmove-db-password     --data-file=-   # lm_app
     gcloud secrets versions add lightmove-flyway-password --data-file=-   # lm_migrate (create-migrate-role.sh)

   Losing the private key signs everyone out — access tokens live 15 minutes and refresh tokens are in
   the database, so nothing is lost but sessions. Leaking it lets anyone mint tokens for any user.

3. The database roles, if you have not already:

     ./ops/cloudsql/create-migrate-role.sh
     gcloud sql connect bright-gcc --user=postgres --database=lightmove < ops/cloudsql/harden.sql

Then push to main. .github/workflows/deploy.yml does the rest.
────────────────────────────────────────────────────────────────────────────────
EOF
