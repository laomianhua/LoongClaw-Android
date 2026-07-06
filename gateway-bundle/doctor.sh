#!/usr/bin/env bash
set -euo pipefail
GATEWAY_HOST="${GATEWAY_HOST:-127.0.0.1}"
GATEWAY_PORT="${GATEWAY_PORT:-18789}"
PROFILE="${1:-standard}"
BUNDLE_DIR="$(cd "$(dirname "$0")" && pwd)"
PY="${PYTHON:-python3}"
exec "$PY" "$BUNDLE_DIR/scripts/doctor_bundle.py" \
  --bundle-dir "$BUNDLE_DIR" \
  --gateway-host "$GATEWAY_HOST" \
  --gateway-port "$GATEWAY_PORT" \
  --profile "$PROFILE"
