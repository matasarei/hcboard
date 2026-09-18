#!/usr/bin/env bash
set -euo pipefail

# Generates a production release keystore for signing hcboard APKs and AABs.
#
# Usage:
#   scripts/generate-release-keystore.sh [keystore_path] [alias]
#
# Default keystore path: $HOME/.android/hcboard-release.keystore
# Default alias: release

KEYSTORE_PATH="${1:-$HOME/.android/hcboard-release.keystore}"
ALIAS="${2:-release}"

if [[ -f "$KEYSTORE_PATH" ]]; then
    echo "Keystore already exists at: $KEYSTORE_PATH"
    echo "Refusing to overwrite an existing release key. Backup this key safely!"
    exit 1
fi

mkdir -p "$(dirname "$KEYSTORE_PATH")"

echo "================================================================="
echo "Generating production release keystore for hcboard"
echo "Target: $KEYSTORE_PATH"
echo "Alias:  $ALIAS"
echo "================================================================="
echo "Note: You will be asked for a keystore password and organization info."
echo "CRITICAL: Back up this keystore and its password! If lost, you will"
echo "never be able to update your app on user devices or app stores."
echo "================================================================="

KEYTOOL_BIN="keytool"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then
    KEYTOOL_BIN="$JAVA_HOME/bin/keytool"
fi

"$KEYTOOL_BIN" -genkeypair \
    -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -sigalg SHA256withRSA \
    -validity 10000 \
    -dname "CN=hcboard, OU=Keyboard, O=hcboard, C=US"

echo ""
echo "Keystore successfully generated at: $KEYSTORE_PATH"
echo ""
echo "To build signed releases locally, add to your shell profile (~/.zshrc or ~/.bashrc):"
echo "-----------------------------------------------------------------"
echo "export HCBOARD_RELEASE_KEYSTORE=\"$KEYSTORE_PATH\""
echo "export HCBOARD_RELEASE_KEYSTORE_PASSWORD=\"<your-keystore-password>\""
echo "export HCBOARD_RELEASE_KEY_ALIAS=\"$ALIAS\""
echo "export HCBOARD_RELEASE_KEY_PASSWORD=\"<your-keystore-password>\""
echo "-----------------------------------------------------------------"
echo ""
echo "To configure GitHub Actions release builds, add these Repository Secrets:"
echo "-----------------------------------------------------------------"
echo "RELEASE_KEYSTORE_BASE64: (Output of: base64 < \"$KEYSTORE_PATH\" | tr -d '\\n')"
echo "RELEASE_KEYSTORE_PASSWORD: <your-keystore-password>"
echo "RELEASE_KEY_ALIAS: $ALIAS"
echo "RELEASE_KEY_PASSWORD: <your-keystore-password>"
echo "-----------------------------------------------------------------"
