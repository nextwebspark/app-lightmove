#!/usr/bin/env bash
# Asserts a deployed revision serves, and keeps the properties this deployment exists to preserve.
#
#   ops/gcp/smoke.sh public    <public-url> <service-url>   # what users reach, after traffic moves
#   ops/gcp/smoke.sh candidate <tagged-url> <public-url>    # a revision holding no traffic yet
#
# `gcloud run deploy` succeeding means the revision started, not that it serves. Called by deploy.yml
# (both modes) and rollback.yml (public).
set -euo pipefail

MODE="${1:?mode: public or candidate}"
URL="${2%/}"
OTHER="${3%/}"

echo "health"
curl -fsS --retry 5 --retry-delay 3 "$URL/actuator/health" > /dev/null

echo "metrics are NOT public (Actuator shares the app port here)"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$URL/actuator/prometheus")" = "401" ]

echo "the API is still shut to anonymous callers"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$URL/api/v1/onboarding/workspaces")" = "401" ]

case "$MODE" in
    public)
        echo "the SPA is in the image and served at the root"
        curl -fsS "$URL/" | grep -q '<div id="root">'

        # With a custom domain the service's own URL redirects every page to it, so a page check against
        # it reads an empty 302 and fails a deploy that is serving fine (v0.3.0).
        if [ "$URL" != "$OTHER" ]; then
            echo "the service's own URL sends pages to the public origin"
            [ "$(curl -s -o /dev/null -w '%{redirect_url}' "$OTHER/")" = "$URL/" ]
        fi
        ;;
    candidate)
        # A tagged URL is never the canonical host, so CanonicalOriginRedirectFilter answers every page
        # with a redirect and the SPA itself can only be checked once the revision is public.
        echo "pages are sent to the public origin"
        [ "$(curl -s -o /dev/null -w '%{redirect_url}' "$URL/")" = "$OTHER/" ]
        ;;
    *)
        echo "unknown mode '$MODE'" >&2
        exit 2
        ;;
esac
