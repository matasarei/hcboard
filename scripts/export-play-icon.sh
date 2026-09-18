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

# Create a square (full-bleed) SVG variant without rounded corners for store masking
TMP_SVG="$TMP_DIR/store-logo.svg"
sed 's/rx="[0-9]*"/rx="0"/' "$SVG_SRC" > "$TMP_SVG"

echo "Exporting 512x512 store icon from $SVG_SRC..."

if command -v rsvg-convert >/dev/null 2>&1; then
    rsvg-convert -w 512 -h 512 "$TMP_SVG" -o "$OUTPUT_PNG"
elif command -v inkscape >/dev/null 2>&1; then
    inkscape -w 512 -h 512 "$TMP_SVG" -o "$OUTPUT_PNG"
elif command -v magick >/dev/null 2>&1; then
    magick -density 300 -background none -resize 512x512 "$TMP_SVG" "$OUTPUT_PNG"
elif command -v qlmanage >/dev/null 2>&1; then
    # macOS native Quick Look rasterizer
    qlmanage -t -s 512 -o "$TMP_DIR" "$TMP_SVG" >/dev/null 2>&1
    cp "$TMP_DIR/store-logo.svg.png" "$OUTPUT_PNG"
else
    echo "Error: No SVG rasterizer found (need rsvg-convert, inkscape, magick, or macOS qlmanage)." >&2
    exit 1
fi

echo "Successfully exported store icon to: $OUTPUT_PNG"
file "$OUTPUT_PNG"
