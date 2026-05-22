#!/usr/bin/env bash
# Sync wasmCloud runtime-operator CRDs from the published Helm chart.
#
# Usage:
#   scripts/sync-crds.sh                        # uses pinned default version
#   scripts/sync-crds.sh --version 2.3.0        # override version
#   RUNTIME_OPERATOR_VERSION=2.3.0 scripts/sync-crds.sh
#
# Published from wasmCloud/wasmCloud by .github/workflows/charts.yml to
# oci://ghcr.io/wasmcloud/charts/runtime-operator.

set -euo pipefail

DEFAULT_VERSION="2.2.1"
CHART="oci://ghcr.io/wasmcloud/charts/runtime-operator"

VERSION="${RUNTIME_OPERATOR_VERSION:-$DEFAULT_VERSION}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

command -v helm >/dev/null || { echo "helm is required" >&2; exit 1; }

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$REPO_ROOT/charts/runtime-operator/crds"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Pulling $CHART:$VERSION ..."
helm pull "$CHART" --version "$VERSION" --untar -d "$TMP" >/dev/null

SRC="$TMP/runtime-operator/crds"
if [[ ! -d "$SRC" ]] || ! compgen -G "$SRC/*.yaml" >/dev/null; then
  echo "no CRDs found in chart at $SRC" >&2
  exit 1
fi

mkdir -p "$DEST"
# Replace contents so files removed upstream go away here too.
find "$DEST" -maxdepth 1 -name '*.yaml' -delete
cp "$SRC"/*.yaml "$DEST/"
printf '%s\n' "$VERSION" > "$DEST/.version"

echo "Synced $(ls "$DEST"/*.yaml | wc -l | tr -d ' ') CRDs to $DEST (chart $VERSION)"
