#!/usr/bin/env bash
#
# Daily log digest: a Cloud Run Job that reads the service's last 24h of logs and emails a summary,
# started each morning by Cloud Scheduler.
#
#   DIGEST_TO="a@x.com,b@x.com" ./ops/gcp/log-digest.sh            # build, create or update, idempotent
#   DIGEST_TO="a@x.com" ./ops/gcp/log-digest.sh --dry-run          # print what it would do and stop
#
# Its own service account, read-only on logs and on the one secret it needs. A job outside the service
# still reports when the service itself is down.
set -euo pipefail

PROJECT="${GCP_PROJECT:-hak-talent-mapping}"
REGION="${GCP_REGION:-us-central1}"
AR_REPO="${AR_REPO:-lightmove}"
JOB="lightmove-log-digest"
SCHEDULE="${DIGEST_SCHEDULE:-0 8 * * *}"
TIME_ZONE="Asia/Dubai"
DIGEST_TO="${DIGEST_TO:-nextwebspark@gmail.com,ambrish.java@gmail.com}"
EMAIL_FROM="${EMAIL_FROM:-Uncava <noreply@uncava.com>}"
DIGEST_SA="lightmove-digest"
DIGEST_SA_EMAIL="${DIGEST_SA}@${PROJECT}.iam.gserviceaccount.com"
SCHEDULER_SA_EMAIL="lightmove-scheduler@${PROJECT}.iam.gserviceaccount.com"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT}/${AR_REPO}/${JOB}:latest"
HERE="$(cd "$(dirname "$0")" && pwd)"

say() { printf '\n\033[1m▸ %s\033[0m\n' "$1"; }
die() { printf '\n\033[31m✗ %s\033[0m\n' "$1" >&2; exit 1; }

[[ -n "$DIGEST_TO" ]] || die "Set DIGEST_TO to a comma-separated list of addresses."

if [[ "${1:-}" == "--dry-run" ]]; then
    echo
    echo "  job        ${JOB} (${REGION}) as ${DIGEST_SA_EMAIL}"
    echo "  image      ${IMAGE}"
    echo "  schedule   ${SCHEDULE} ${TIME_ZONE}"
    echo "  to         ${DIGEST_TO}"
    echo
    exit 0
fi

say "Service account"
gcloud services enable run.googleapis.com cloudbuild.googleapis.com cloudscheduler.googleapis.com --project="$PROJECT"
if ! gcloud iam service-accounts describe "$DIGEST_SA_EMAIL" --project="$PROJECT" &>/dev/null; then
    gcloud iam service-accounts create "$DIGEST_SA" --display-name="LightMove log digest" --project="$PROJECT"
fi
gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:${DIGEST_SA_EMAIL}" \
    --role=roles/logging.viewer --condition=None --quiet >/dev/null
gcloud secrets add-iam-policy-binding lightmove-resend-api-key --project="$PROJECT" \
    --member="serviceAccount:${DIGEST_SA_EMAIL}" --role=roles/secretmanager.secretAccessor --quiet >/dev/null
echo "  ✓ roles/logging.viewer, and the Resend secret only"

say "Image"
gcloud builds submit "$HERE/log-digest" --tag "$IMAGE" --project="$PROJECT"

say "Job"
JOB_ARGS=(--image="$IMAGE" --region="$REGION" --project="$PROJECT" --service-account="$DIGEST_SA_EMAIL"
    --max-retries=1 --task-timeout=300s
    --set-env-vars="^#^GCP_PROJECT=${PROJECT}#EMAIL_FROM=${EMAIL_FROM}#DIGEST_TO=${DIGEST_TO}"
    --set-secrets="RESEND_API_KEY=lightmove-resend-api-key:latest")
if gcloud run jobs describe "$JOB" --region="$REGION" --project="$PROJECT" &>/dev/null; then
    gcloud run jobs update "$JOB" "${JOB_ARGS[@]}"
else
    gcloud run jobs create "$JOB" "${JOB_ARGS[@]}"
fi

say "Scheduler"
gcloud run jobs add-iam-policy-binding "$JOB" --region="$REGION" --project="$PROJECT" \
    --member="serviceAccount:${SCHEDULER_SA_EMAIL}" --role=roles/run.invoker --quiet >/dev/null
RUN_URI="https://run.googleapis.com/v2/projects/${PROJECT}/locations/${REGION}/jobs/${JOB}:run"
SCHEDULER_ARGS=(--location="$REGION" --project="$PROJECT" --schedule="$SCHEDULE" --time-zone="$TIME_ZONE"
    --uri="$RUN_URI" --http-method=POST --oauth-service-account-email="$SCHEDULER_SA_EMAIL")
if gcloud scheduler jobs describe "$JOB" --location="$REGION" --project="$PROJECT" &>/dev/null; then
    gcloud scheduler jobs update http "$JOB" "${SCHEDULER_ARGS[@]}"
else
    gcloud scheduler jobs create http "$JOB" "${SCHEDULER_ARGS[@]}"
fi

cat <<EOF

────────────────────────────────────────────────────────────────────────────────
  Send one now:  gcloud run jobs execute ${JOB} --region ${REGION} --project ${PROJECT} --wait
  Preview only:  cd ops/gcp/log-digest && python3 main.py --dry-run   (needs gcloud ADC and google-cloud-logging)
────────────────────────────────────────────────────────────────────────────────
EOF
