#!/usr/bin/env bash
#
# Copies the candidate-documents bucket between projects and proves the copy byte for byte.
#
#   SRC_CONFIG=default SRC_BUCKET=hak-talent-mapping-lightmove-documents \
#   DST_CONFIG=uncava  DST_BUCKET=uncava-prod-documents \
#     ./ops/gcp/copy-documents-bucket.sh copy | verify
#
# Hashes come from `gcloud storage hash`, which reads CMEK objects (their listing carries no checksum).
# Each side is a separate gcloud configuration (different account), so the objects pass through a local temp
# directory that holds personal data: it is removed on exit. Run `copy` again at cutover for the delta.
set -euo pipefail

: "${SRC_CONFIG:?}" "${SRC_BUCKET:?}" "${DST_CONFIG:?}" "${DST_BUCKET:?}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

on() { local config="$1"; shift; CLOUDSDK_ACTIVE_CONFIG_NAME="$config" gcloud "$@"; }

inventory() {
    local listing
    if ! listing="$(on "$1" storage hash --hex --format=json "gs://$2/**" 2>&1)"; then
        [[ "$listing" == *"matched no objects"* ]] || { echo "$listing" >&2; return 1; }
        return 0
    fi
    python3 -c 'import json,sys
for o in json.load(sys.stdin):
    print(o["url"].split("/", 3)[3], o["crc32c_hash"], o["md5_hash"])' <<< "$listing" | sort
}

case "${1:-}" in
copy)
    on "$SRC_CONFIG" storage rsync -r "gs://$SRC_BUCKET" "$WORK/objects"
    on "$DST_CONFIG" storage rsync -r "$WORK/objects" "gs://$DST_BUCKET"
    echo "✓ copied"
    ;;
verify)
    inventory "$SRC_CONFIG" "$SRC_BUCKET" > "$WORK/src.inv"
    inventory "$DST_CONFIG" "$DST_BUCKET" > "$WORK/dst.inv"
    if diff -u "$WORK/src.inv" "$WORK/dst.inv"; then
        echo "✓ $(wc -l < "$WORK/src.inv" | tr -d ' ') objects, names, crc32c and md5 identical"
    else
        echo "✗ buckets differ (above)"; exit 1
    fi
    ;;
*)
    echo "usage: $0 copy | verify" >&2
    exit 2
    ;;
esac
