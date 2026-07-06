#!/usr/bin/env bash
# Harvest ~/.openclaw assets into gateway-export/ for bundle review.
set -euo pipefail

OPENCLAW_HOME="${OPENCLAW_HOME:-$HOME/.openclaw}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXPORT_ROOT="$REPO_ROOT/gateway-export"

SKILLS=(
  gallery-display
  file-viewer-display
  file-manager
  notepad
  weather-display
)

CANVAS_FILES=(
  map.littlehelper.html
  view_stored_img.html
  gallery.html
)

echo "Harvest from: $OPENCLAW_HOME"
echo "Export to:    $EXPORT_ROOT"
mkdir -p "$EXPORT_ROOT"

echo ""
echo "[skills]"
for name in "${SKILLS[@]}"; do
  src="$OPENCLAW_HOME/workspace/skills/$name"
  if [[ -d "$src" ]]; then
    mkdir -p "$EXPORT_ROOT/skills"
    cp -R "$src" "$EXPORT_ROOT/skills/"
    echo "  + skills/$name"
  fi
done

echo ""
echo "[scripts]"
if [[ -d "$OPENCLAW_HOME/workspace/scripts" ]]; then
  mkdir -p "$EXPORT_ROOT/scripts"
  cp "$OPENCLAW_HOME/workspace/scripts/"*.py "$EXPORT_ROOT/scripts/" 2>/dev/null || true
  ls "$EXPORT_ROOT/scripts/" 2>/dev/null | sed 's/^/  + /' || true
fi

echo ""
echo "[canvas]"
mkdir -p "$EXPORT_ROOT/canvas"
for file in "${CANVAS_FILES[@]}"; do
  src="$OPENCLAW_HOME/canvas/$file"
  if [[ -f "$src" ]]; then
    cp "$src" "$EXPORT_ROOT/canvas/"
    echo "  + $file"
  fi
done

echo ""
echo "Done. Update gateway-export/EXPORT_NOTES.md then run: python scripts/build_gateway_bundle.py"
