# hcboard

An Android keyboard for people who type code on a phone: the look of a Material 3 keyboard, the
feel of the iPhone's, and a developer mode with real Ctrl, Alt, Shift, Fn and Meta keys that latch
the way you expect.

## What it does

- **Material 3 look, dynamic colour** on Android 12 and later, light and dark.
- **iPhone-style feel:** key preview on press, a spring on every key, a light haptic tick,
  long-press accents, and a cursor trackpad when you hold the space bar.
- **Developer mode:** one tap on `</>` adds a strip with Esc, Tab, Ctrl, Alt, Shift, arrows and Fn
  above any layer. Tap a modifier to arm it for the next key, double-tap or long-press to lock it,
  or hold it and tap another key with a second finger. Modifiers survive layer switches, and the
  toolbar always says what is armed. Ctrl+C in a terminal is a real Ctrl+C; in a text field,
  Ctrl+A/C/V/X are select, copy, paste and cut.
- **A 60% board on wide screens:** foldables and tablets get every key of a standard 60% layout,
  shifted symbols printed on the keys and F1–F12 behind Fn.
- **Password managers:** the keyboard hosts Android's inline autofill suggestions from whichever
  manager you chose in system settings, and a key button opens or changes it.
- **Glide typing** on the letters layer, with an English word list bundled.
- **No network permission, ever.** Nothing you type leaves the device or is logged.

## Install

Every push to `main` produces a debug build in the
[dev pre-release](https://github.com/matasarei/hcboard/releases/tag/dev). Download
`hcboard-dev.apk`, open it on the phone, then enable and choose **Keyboard** under
Settings › System › Keyboard › On-screen keyboard. With a phone attached:

```
adb install -r hcboard-dev.apk
```

## Build

Requires JDK 17 and the Android SDK (platform 37).

```
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
scripts/enable-ime.sh            # installs the debug APK and makes it the current keyboard
```

The instrumented smoke test needs an emulator or device: `./gradlew :app:connectedDebugAndroidTest`.

## Third-party code and data

hcboard is licensed under the [Apache License 2.0](LICENSE). It includes work from other projects,
all under Apache-2.0; the exact attributions are in [NOTICE](NOTICE).

| What | From | Licence |
|---|---|---|
| Glide-typing classifier (`input/glide/GlideClassifier.kt`) | [FlorisBoard](https://github.com/florisboard/florisboard), `StatisticalGlideTypingClassifier.kt` by The FlorisBoard Contributors, after Étienne Desticourt's analysis for AnySoftKeyboard | Apache-2.0 |
| English word list (`assets/dictionaries/en_US.txt`) | [Android Open Source Project](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/), `en_US_wordlist.combined`, filtered by `scripts/build-wordlist.py` | Apache-2.0 |
| UI toolkit and libraries | [AndroidX](https://developer.android.com/jetpack/androidx) and Jetpack Compose, Material 3 | Apache-2.0 |
| Language | [Kotlin](https://kotlinlang.org/) | Apache-2.0 |

Design references, not code: the layout follows Gboard's proportions and the feel follows the
iPhone keyboard; nothing from either was copied.

## Contributing

`CLAUDE.md` holds the build commands and the conventions that matter in this codebase. Work lands
through pull requests to `main`; every pull request runs the unit tests and lint.
