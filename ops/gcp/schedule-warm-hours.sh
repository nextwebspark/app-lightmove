#!/usr/bin/env bash
#
# Keep one Cloud Run instance warm during Dubai working hours, and none overnight.
#
#   ./ops/gcp/schedule-warm-hours.sh              # create or update everything, idempotent
#   ./ops/gcp/schedule-warm-hours.sh --dry-run    # print what it would do and stop
#
# The service scales to zero, so the first load after ~15 minutes of idle pays a JVM + Spring Boot boot
# and the Cloud SQL connector's certificate mint before any request is answered. Testing happens in
# Dubai hours, so a warm instance is only worth paying for then: two Cloud Scheduler jobs set the
# service's minimum instances to 1 at 06:45 and back to 0 at 22:00, Asia/Dubai (UTC+4, no DST — the
# schedule cannot drift). Roughly $2-4 a month at the idle rate.
#
# Why a PATCH on the service and not `gcloud run services update --min-instances`: that flag is the
# REVISION-level minimum, immutable per revision, so every change deploys a new one — and a new revision
# is the cold start this exists to prevent. The SERVICE-level minimum (`scaling.minInstanceCount`, or
# `--min` in gcloud) changes in place with no revision. When both are set the larger wins, which is why
# neither deploy path may pass --min-instances: it would silently override the schedule upwards, and a
# deploy at 11:00 that reset it to 0 would look like the scheduler had stopped working.
#
# Its own service account, scoped to this one service. The runtime identity is deliberately not reused:
# the application must not be able to rescale the service it runs on.
set -euo pipefail

PROJECT="${GCP_PROJECT:-hak-talent-mapping}"
REGION="${GCP_REGION:-us-central1}"
SERVICE="${SERVICE:-lightmove}"

TIME_ZONE="Asia/Dubai"
# 06:45, not 07:00: the minimum has to take effect and the instance still has to boot before the first
# person opens the app.
WARM_UP_SCHEDULE="${WARM_UP_SCHEDULE:-45 6 * * *}"
WIND_DOWN_SCHEDULE="${WIND_DOWN_SCHEDULE:-0 22 * * *}"

SCHEDULER_SA="lightmove-scheduler"
SCHEDULER_SA_EMAIL="${SCHEDULER_SA}@${PROJECT}.iam.gserviceaccount.com"
RUNTIME_SA_EMAIL="lightmove-api@${PROJECT}.iam.gserviceaccount.com"
TARGET_URI="https://run.googleapis.com/v2/projects/${PROJECT}/locations/${REGION}/services/${SERVICE}?updateMask=scaling.minInstanceCount"
JOBS_API="https://cloudscheduler.googleapis.com/v1/projects/${PROJECT}/locations/${REGION}/jobs"

dry_run=false
[[ "${1:-}" == "--dry-run" ]] && dry_run=true

say() { printf '\n\033[1m▸ %s\033[0m\n' "$1"; }
die() { printf '\n\033[31m✗ %s\033[0m\n' "$1" >&2; exit 1; }

# ── Preflight ─────────────────────────────────────────────────────────────────
say "Preflight"
gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" &>/dev/null \
    || die "Cloud Run service '${SERVICE}' does not exist in ${REGION}. Deploy first."
echo "  ✓ service ${SERVICE} exists"

if $dry_run; then
    echo
    echo "  project      ${PROJECT}"
    echo "  service      ${SERVICE} (${REGION})"
    echo "  scheduler SA ${SCHEDULER_SA_EMAIL}"
    echo "  warm up      ${WARM_UP_SCHEDULE}  ${TIME_ZONE}  → minInstanceCount 1"
    echo "  wind down    ${WIND_DOWN_SCHEDULE}  ${TIME_ZONE}  → minInstanceCount 0"
    echo "  target       PATCH ${TARGET_URI}"
    echo
    exit 0
fi

# ── API ───────────────────────────────────────────────────────────────────────
# Enabling the API also creates the Scheduler service agent and grants it the role it needs to mint
# tokens for the job's service account.
say "Enabling Cloud Scheduler"
gcloud services enable cloudscheduler.googleapis.com --project="$PROJECT"

# ── Service account ───────────────────────────────────────────────────────────
say "Service account"
if gcloud iam service-accounts describe "$SCHEDULER_SA_EMAIL" --project="$PROJECT" &>/dev/null; then
    echo "  ✓ ${SCHEDULER_SA} exists"
else
    gcloud iam service-accounts create "$SCHEDULER_SA" \
        --display-name="LightMove warm-hours scheduler" --project="$PROJECT"
fi

# run.developer on the one service, not the project: it can update this service's settings and nothing
# else — no other service, no IAM, no invoking.
gcloud run services add-iam-policy-binding "$SERVICE" \
    --region="$REGION" --project="$PROJECT" \
    --member="serviceAccount:${SCHEDULER_SA_EMAIL}" --role=roles/run.developer --quiet >/dev/null
echo "  ✓ roles/run.developer on ${SERVICE} only"

# Cloud Run validates the whole Service on any update, the runtime service account it names included, so
# even a scaling-only PATCH is refused with a bare 403 without actAs on that account. Scoped to
# lightmove-api alone: project-wide it would let this job run anything as any account in the project.
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA_EMAIL" \
    --member="serviceAccount:${SCHEDULER_SA_EMAIL}" --role=roles/iam.serviceAccountUser \
    --project="$PROJECT" --quiet >/dev/null
echo "  ✓ roles/iam.serviceAccountUser on lightmove-api only"

# ── Jobs ──────────────────────────────────────────────────────────────────────
# Through the REST API rather than `gcloud scheduler jobs create http`, because gcloud's --http-method
# accepts only delete/get/head/post/put in every release track, and PATCH is the only verb the Cloud Run
# v2 API takes for a partial update. The API itself has always allowed it.
#
# OAuth rather than OIDC because the target is a Google API. Retries because a PATCH that lands while a
# release is mid-deploy is refused with a 409, and the next attempt goes through.
upsert_job() {
    local name="$1" schedule="$2" min="$3" description="$4"
    local body payload method url verb response status

    body="$(printf '{"scaling":{"minInstanceCount":%d}}' "$min" | base64 | tr -d '\n')"
    payload=$(cat <<JSON
{
  "name": "projects/${PROJECT}/locations/${REGION}/jobs/${name}",
  "description": "${description}",
  "schedule": "${schedule}",
  "timeZone": "${TIME_ZONE}",
  "attemptDeadline": "60s",
  "retryConfig": {
    "retryCount": 5,
    "minBackoffDuration": "60s",
    "maxBackoffDuration": "600s"
  },
  "httpTarget": {
    "uri": "${TARGET_URI}",
    "httpMethod": "PATCH",
    "headers": { "Content-Type": "application/json" },
    "body": "${body}",
    "oauthToken": {
      "serviceAccountEmail": "${SCHEDULER_SA_EMAIL}",
      "scope": "https://www.googleapis.com/auth/cloud-platform"
    }
  }
}
JSON
)

    if gcloud scheduler jobs describe "$name" --location="$REGION" --project="$PROJECT" &>/dev/null; then
        method=PATCH
        url="${JOBS_API}/${name}?updateMask=description,schedule,timeZone,attemptDeadline,retryConfig,httpTarget"
        verb=updated
    else
        method=POST
        url="$JOBS_API"
        verb=created
    fi

    response="$(curl -sS -X "$method" "$url" \
        -H "Authorization: Bearer ${ACCESS_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "$payload" -w '\n%{http_code}')"
    status="${response##*$'\n'}"
    [[ "$status" == 2* ]] || die "Cloud Scheduler refused '${name}' (HTTP ${status}):
${response%$'\n'*}"

    echo "  ✓ ${name}: ${verb} — '${schedule}' ${TIME_ZONE} → minInstanceCount ${min}"
}

say "Scheduler jobs"
ACCESS_TOKEN="$(gcloud auth print-access-token)"
upsert_job lightmove-warm-up   "$WARM_UP_SCHEDULE"   1 "Uncava: keep one instance warm from Dubai morning"
upsert_job lightmove-wind-down "$WIND_DOWN_SCHEDULE" 0 "Uncava: scale to zero after Dubai evening"

# ── What is left for a human ──────────────────────────────────────────────────
CURRENT_MIN="$(gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" \
    --format='value(metadata.annotations."run.googleapis.com/minScale")')"

cat <<EOF

────────────────────────────────────────────────────────────────────────────────
  Service-level minimum instances now: ${CURRENT_MIN:-0}

  Prove the PATCH path once by hand rather than finding out at 06:45. Each run must change the
  minimum and leave the revision name unchanged:

    gcloud scheduler jobs run lightmove-warm-up --location ${REGION} --project ${PROJECT}
    gcloud run services describe ${SERVICE} --region ${REGION} --project ${PROJECT} \\
        --format='value(metadata.annotations."run.googleapis.com/minScale",status.latestReadyRevisionName)'
    gcloud scheduler jobs run lightmove-wind-down --location ${REGION} --project ${PROJECT}

  Finish on whichever job matches the time of day in Dubai. To stop paying for the warm instance:

    gcloud scheduler jobs pause lightmove-warm-up --location ${REGION} --project ${PROJECT}

  Neither deploy path passes --min-instances, and neither may: that is the revision-level minimum,
  and it would override this schedule upwards on the next release.
────────────────────────────────────────────────────────────────────────────────
EOF
