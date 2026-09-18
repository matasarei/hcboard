#!/usr/bin/env bash
set -euo pipefail

# Exports docs/assets/logo.svg to a 512x512 32-bit PNG for Google Play and Samsung Galaxy Store.
# Google Play and Galaxy Store require:
#   - 512 x 512 pixels
#   - 32-bit PNG (full-bleed square; store dynamically applies squircle corners)
#   - Max size 1024 KB

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SVG_SRC="$ROOT_DIR/docs/assets/logo.svg"
OUTPUT_PNG="$ROOT_DIR/docs/assets/icon-512.png"

if [[ ! -f "$SVG_SRC" ]]; then
    echo "Error: Source SVG not found at $SVG_SRC" >&2
    exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Exporting 512x512 store icon from $SVG_SRC..."

CHROME_BIN=""
if command -v google-chrome >/dev/null 2>&1; then
    CHROME_BIN="google-chrome"
elif command -v chromium >/dev/null 2>&1; then
    CHROME_BIN="chromium"
elif [[ -x "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]]; then
    CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
fi

if command -v rsvg-convert >/dev/null 2>&1; then
    sed 's/rx="[0-9]*"/rx="0"/' "$SVG_SRC" | rsvg-convert -w 512 -h 512 -o "$OUTPUT_PNG"
elif command -v inkscape >/dev/null 2>&1; then
    sed 's/rx="[0-9]*"/rx="0"/' "$SVG_SRC" > "$TMP_DIR/store.svg"
    inkscape -w 512 -h 512 "$TMP_DIR/store.svg" -o "$OUTPUT_PNG"
elif command -v magick >/dev/null 2>&1; then
    sed 's/rx="[0-9]*"/rx="0"/' "$SVG_SRC" > "$TMP_DIR/store.svg"
    magick -density 300 -background none -resize 512x512 "$TMP_DIR/store.svg" "$OUTPUT_PNG"
elif [[ -n "$CHROME_BIN" ]]; then
    TMP_HTML="$TMP_DIR/icon.html"
    cat << 'EOF' > "$TMP_HTML"
<!DOCTYPE html>
<html>
<head>
<style>
  html, body {
    margin: 0;
    padding: 0;
    width: 512px;
    height: 512px;
    overflow: hidden;
    background: #415F91;
  }
  svg {
    position: absolute;
    top: 0;
    left: 0;
    width: 512px;
    height: 512px;
  }
</style>
</head>
<body>
EOF
    sed -e 's/rx="[0-9]*"/rx="0"/' -e 's/<svg\([^>]*\)width="[0-9]*" height="[0-9]*"/<svg\1/' "$SVG_SRC" >> "$TMP_HTML"
    echo "</body></html>" >> "$TMP_HTML"
    "$CHROME_BIN" --headless --disable-gpu --window-size=512,512 --hide-scrollbars --screenshot="$OUTPUT_PNG" "file://$TMP_HTML" >/dev/null 2>&1
else
    echo "Error: No SVG rasterizer found (need rsvg-convert, inkscape, magick, or Chrome)." >&2
    exit 1
fi

echo "Successfully exported store icon to: $OUTPUT_PNG"
file "$OUTPUT_PNG"
