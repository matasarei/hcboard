#!/bin/bash
# Installs the debug build on the connected device/emulator and makes it the current keyboard.
set -e
ADB="${ADB:-adb}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
IME="net.matasar.keyboard/.ime.KeyboardService"
"$ADB" install -r "$APK"
"$ADB" shell ime enable "$IME"
"$ADB" shell ime set "$IME"
"$ADB" shell settings get secure default_input_method
