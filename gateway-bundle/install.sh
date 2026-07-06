#!/usr/bin/env bash
set -euo pipefail
PROFILE="${1:-standard}"
WITH_PC_PATCH=""
if [[ "${2:-}" == "--with-pc-patch" ]]; then
  WITH_PC_PATCH="--with-pc-patch"
fi
BUNDLE_DIR="$(cd "$(dirname "$0")" && pwd)"
PY="${PYTHON:-python3}"
exec "$PY" "$BUNDLE_DIR/scripts/install_bundle.py" --bundle-dir "$BUNDLE_DIR" --profile "$PROFILE" $WITH_PC_PATCH
