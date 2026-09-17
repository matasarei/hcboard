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
  shifted symbols printed on the keys and F1–F12 behind Fn. The board follows the current
  language: its letters sit on the ANSI slots, punctuation a long alphabet displaces stays
  reachable through Fn, and a globe key appears when more than one language is enabled.
- **Positional shortcuts:** Ctrl+С on a Cyrillic board is Ctrl+C, as on a PC, and while a
  combination modifier is armed the keys show the Latin letters they will send.
- **Password managers:** the keyboard hosts Android's inline autofill suggestions from whichever
  manager you chose in system settings, and a key button opens or changes it.
- **Nine languages:** English, Ukrainian, Russian, French, Spanish, German, Italian, Portuguese
  and Polish, each with its own layout and long-press accents. Until you change the set in
  Settings it follows your phone's languages, plus English; a globe key cycles them (hold it to pick).
  Android's keyboard picker lists hcboard once; languages are the keyboard's own business.
- **Glide typing** on the letters layer of every language, with a word list bundled for each.
- **Word suggestions** from the same lists while you type: the word as typed, its correction (applied by space, one backspace takes it back) and completions, in the toolbar behind a chevron. Both parts are settings; off in password, terminal, number, address and e-mail fields.
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

hcboard is licensed under the [Apache License 2.0](LICENSE). It includes code and data from other
projects, under Apache-2.0 and, for the Ukrainian word list, CC BY 4.0; the exact attributions are
in [NOTICE](NOTICE).

| What | From | Licence |
|---|---|---|
| Glide-typing classifier (`input/glide/GlideClassifier.kt`) | [FlorisBoard](https://github.com/florisboard/florisboard), `StatisticalGlideTypingClassifier.kt` by The FlorisBoard Contributors, after Étienne Desticourt's analysis for AnySoftKeyboard | Apache-2.0 |
| Word lists for English, Russian, French, Spanish, German, Italian, Portuguese (Brazil) and Polish (`assets/dictionaries/`) | [Android Open Source Project](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/), the `*_wordlist.combined` files, filtered by `scripts/build-wordlist.py` | Apache-2.0 |
| Ukrainian word list (`assets/dictionaries/uk.txt`) | [Helium314/aosp-dictionaries](https://codeberg.org/Helium314/aosp-dictionaries), `wordlists_experimental/main_uk.combined`, built from the [Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/en/download/) word lists, merged with this project's everyday-word overlay (`scripts/wordlists/uk-everyday.tsv`) | CC BY 4.0 (overlay Apache-2.0) |
| UI toolkit and libraries | [AndroidX](https://developer.android.com/jetpack/androidx) and Jetpack Compose, Material 3 | Apache-2.0 |
| Language | [Kotlin](https://kotlinlang.org/) | Apache-2.0 |

Design references, not code: the layout follows Gboard's proportions and the feel follows the
iPhone keyboard; nothing from either was copied.

## Contributing

`CLAUDE.md` holds the build commands and the conventions that matter in this codebase. Work lands
through pull requests to `main`; every pull request runs the unit tests and lint.
