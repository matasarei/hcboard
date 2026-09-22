# hcboard — notes for Claude

An Android keyboard (input method) written in Kotlin with Jetpack Compose: Material 3 look,
iPhone-style feel, a developer mode with latching Ctrl/Alt/Shift/Fn/Meta, a 60% board on wide
windows, and inline-autofill hosting for password managers. One Gradle module, `app`, package
`net.matasar.keyboard`. It only runs as a system input method: install it, enable it in Android
settings, select it, then type into any app. There is no standalone entry point.

## Commands

| What | Command |
|---|---|
| Install | none: Gradle resolves dependencies on first build |
| Test | `./gradlew :app:testDebugUnitTest` |
| Test (one class) | `./gradlew :app:testDebugUnitTest --tests net.matasar.keyboard.ime.DeveloperModeTest` |
| Instrumented | `./gradlew :app:connectedDebugAndroidTest` (emulator or device attached) |
| Lint | `./gradlew :app:lintDebug` (must stay at 0 errors; the build fails otherwise) |
| Build | `./gradlew :app:assembleDebug` |
| Run | `scripts/enable-ime.sh` after a build: installs the APK, enables and selects the keyboard |

Everything runs on the host. Gradle needs JDK 17: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.
The Android SDK lives in `~/Library/Android/sdk` (pointed to by the ignored `local.properties`);
platforms 36 and 37 and an `android-36 google_apis arm64` emulator image are installed. The
emulator profile is `medium_phone`; boot it headless with
`emulator -avd medium_phone -no-window -no-audio -gpu swiftshader_indirect`.

## This project specifically

- **Versions are pinned in `gradle/libs.versions.toml`:** AGP 9.4 with built-in Kotlin (no
  `org.jetbrains.kotlin.android` plugin), the Compose compiler plugin at 2.3.x which sets the
  Kotlin version, compileSdk 37, targetSdk 36, minSdk 26. The current AndroidX artifacts refuse
  compileSdk 36, so do not lower it.
- **Where things live:** `ime/` service and controller; `layout/` key data and the layers
  (`Layers.kt` phone, `SixtyPercentLayout.kt` wide, `DeveloperStrip.kt`); `input/` the editor
  port, dispatcher, latch and modifier state, key codes; `ui/` composables; `settings/` DataStore
  prefs and the settings screen (and the Custom words screen and Backup section); `backup/` the
  backup file, its codec and passphrase box; `nlp/` word lists, candidates and
  custom words; `autofill/` inline suggestions and the manager sheet; `macro/` macros (model, JSON,
  runner, store, the block editor screen).
- **`input/EditorPort.kt` is the only code that touches `InputConnection`.** Everything else goes
  through `InputDispatcher`, so unit tests use `FakeEditorPort`. Keep it that way. Edits that
  belong together (a word and its replacement, a correction and its separator) go in one
  `EditorPort.batch`, so the app sees one change.
- **Pure logic is unit-tested on the JVM** (`app/src/test`); `android.jar` stubs return defaults,
  so tests may use `KeyEvent` constants and `android.R.id` values but not framework behaviour.
- **The IME window hosts Compose** via `KeyboardService`, which owns the lifecycle, view-model
  store and saved-state registry. The owners must be set on the window's decor view as well as
  the `ComposeView`, or Compose crashes resolving its recomposer. A rebuilt input view (a fold, a
  rotation) disposes the old view's composition first. The keyboard never goes fullscreen
  (`onEvaluateFullscreenMode` is false). It is not direct-boot aware, on purpose: settings,
  custom words, macros and Keystore secrets live in credential-encrypted storage, so before the
  first unlock Android's own keyboard types. Android 16 ignores the theme's edge-to-edge opt-out
  at this target: the keys clear the bottom bar by `measureBottomBarOverlap`, not by the decor.
- **Popups draw in a 68 dp transparent overhang** above the toolbar; `onComputeInsets` hands that
  strip back to the app. Do not use Compose `Popup` windows inside the IME.
- **Clocks:** pointer and key-event timestamps are `SystemClock.uptimeMillis()`. Never compare
  them with `System.currentTimeMillis()`; that bug has been made twice.
- **Modifier semantics** (`input/ModifierState.kt`, `ime/KeyboardController.kt`): tap arms for one
  key, double tap or long press locks, holding and tapping another key chords; modifiers persist
  across layer switches and reset only on a new field. Ctrl+A/C/V/X in ordinary text fields use
  `performContextMenuAction`; in `TYPE_NULL` fields (terminals) they stay key events.
- **Macros** (`macro/`): a stack of blocks (text, key or combination by name, random keys, repeat,
  wait, paste, copy field) kept as JSON (kotlinx.serialization) in their own DataStore, `macros`, apart from
  the settings. `MacroRunner` plays them through `InputDispatcher` only: repeats are unrolled and
  counted first (over 2 000 steps plays nothing), Repeat is capped at 100 and Wait at 10 s where
  the block is read, and a run stops when the field finishes. Key blocks follow the typed-combo rule
  (Ctrl+A/C/V/X are editor actions outside terminals). Random keys go out as key events from a
  fresh `SecureRandom` per run and are never stored or logged; after them the strip reads nothing
  back, as after a filled password. A text block may be `secret`: plain in memory, masked in every
  summary and in the editor, and sealed by `KeystoreSecretBox` (AES-GCM, Android Keystore) in
  `MacroStore`'s one `encode`, so it is never plain on disk or in a backup; one that does not open
  loads empty with `keptSealed` and is written back unchanged. After a secret, too, nothing is read
  back. Copy field reads the whole field through `EditorPort.fieldText`, never in a password field,
  and the service marks the clip `EXTRA_IS_SENSITIVE` when the macro has a secret or random keys.
  The editor is `MacrosActivity`; the keyboard only plays, from `ui/MacroSheet.kt`.
- **Glide typing:** `input/glide/GlideClassifier.kt` is an approved Apache-2.0 copy of FlorisBoard's
  classifier with its header kept; do not "clean it up". Word lists live in `assets/dictionaries/`;
  how they are built, overlaid and rebuilt (regenerate, never hand-edit) is in `docs/wordlists.md`.
  The same lists feed `nlp/Candidates.kt` (prefix completions and one-edit corrections for the word
  before the cursor, read through `InputDispatcher.wordBeforeCursor`): there is no composing region
  on purpose, words are replaced with delete-and-commit in one batch, and nothing is read in a
  field where `suggestionsAllowed` says no. The grid's letter-bounds registry is rebuilt per layout
  and the glide listener is keyed on it, or a language switch classifies against the old alphabet.
  The trail is drawn from the root's draw pass: a sized canvas grows the IME window mid-gesture and
  shifts every later pointer position.
- **Custom words** (`nlp/CustomWords.kt`, `CustomWordStore`, DataStore `words`): language tag →
  word → frequency, 230 when added, 0 when blocked; letters only, at most 48, case kept. They are
  applied when a list loads (`WordList.withOverrides`, 0 removes the word), keyed by the language
  tag so `ru` words reach `ru_bg` too, never written into the assets. The service clears its
  engine cache and reloads the current language when they change; a version counter drops a
  list that was still loading with the old words.
- **Backup** (`backup/`, UI in `settings/BackupSection.kt`): one JSON file (`"format":
  "hcboard-backup"`, version 1) with `Settings` (serializable; `Settings.sanitized()` clamps a
  restored one), custom words and macros, through the system file picker only. Secret texts are
  sealed with `PassphraseSecretBox` (PBKDF2-SHA256 210 000 rounds, AES-GCM, JDK classes so the JVM
  tests run it) behind a sealed check value, so a wrong passphrase refuses the import before
  anything is written; `keptSealed` never crosses between the Keystore and the passphrase (such a
  secret travels empty). Import is capped at 5 MB and at 10 000 000 KDF rounds, replaces all three
  after a confirmation, and writes macros first (the only write that can refuse), then words, then
  settings. The passphrase and opened secrets live in `remember`, never `rememberSaveable`, and the
  passphrase is a `CharArray` wiped after use.
- **Languages** are data in `layout/Languages.kt`: rows, accents, native name. A layer sizes itself to
  its widest row. The globe key exists only with two or more languages enabled; the persisted
  current language is authoritative and the service follows changes to it. The enabled set is the
  keyboard's own, and Android mirrors it: `method.xml` declares a subtype per language (native-name
  label, fixed `subtypeId` = `Language.subtypeId` = its hash code; `MethodXmlTest` pins both, run it
  with `--rerun` after editing only `res/`, which is not a unit-test input), because Android's
  keyboard list shows the enabled subtypes' names under the keyboard's name. `ime/SystemSubtypes.kt`
  pushes the enabled set with `setExplicitlyEnabledInputMethodSubtypes` (API 34+; the service and
  the settings screen both push) and never reads Android's set back; the globe reports the current
  subtype, and a pick in Android's switcher is followed only for an enabled language. Only English
  overrides the implicitly enabled subtypes, so below API 34 the list reads "English" and never
  the phone's languages. The first-run default is English only; additional languages are added by
  the user in settings (`Languages.defaultEnabled`).
  English is always on and not toggleable in settings. The 60% board is
  built from the same data (`layout/SixtyPercentLayout.kt`): letters fill the ANSI slots of
  `AnsiSlots.rows` left to right, a letter on a punctuation slot carries that punctuation on Fn,
  and a nine-letter Shift row shrinks both Shifts to keep `.` and `/` (eight letters in Bulgarian leave standard shifts and punctuation). Ukrainian and Russian fill
  all twelve top-row slots (ї, ъ), while Bulgarian fills eleven (ч on `[`), so `[` and `]` are Fn or punctuation keys. Every
  letter key carries `Key.slot`, the US character of its slot: modifier combinations send the slot
  (Ctrl+С is Ctrl+C) and `displayLabel` shows it while Ctrl, Alt or Meta is active.
  The wide board splits (`layout/SplitLayout.kt`) by cutting the balanced board after its sixth
  key per row, every key at its full-board width, the rows padded on the inner side;
  `ui/SplitGeometry.kt` decides when (Off/Auto/Always; Auto = a separating vertical hinge from
  androidx.window, or a landscape window under 480 dp high) and sizes the unit so no key lands on
  a hinge.
- **TalkBack** (`ui/KeySpeech.kt`, `docs/talkback-matrix.md`): every key is one accessibility node,
  named for what it types now (or Space, Shift, the arrows…), with a latching key's state and a
  click that types it. TalkBack's lift-to-type and double-tap both use that click; never add a
  hover handler of our own, or a key types twice.
- **A key is a legend line and a glyph** (`ui/KeyButton.kt`), never a stack of paddings: the
  shifted symbol sits top-left, the Fn meaning top-right only when it is a symbol, both of the pair
  (`[{`, `` `~ ``; `printedFnLegend`) — named meanings (F1, arrows, Home, Del) show as the glyph
  while Fn is active — and the glyph fills what is left.
  Nothing is dropped on a short key — both zones are sized from the key's own height by the
  functions in `ui/Dimens.kt`, because the height setting starts at 80% (a 36.8 dp key) and a
  landscape window squeezes it further. The glyph is always **what the key would type now**:
  `KeyboardController.displayLabel` follows Shift, Fn and Fn+Shift, the live legend tints
  `armedRing`, and an icon steps aside for an Fn meaning that has a name (backspace reads `Del`).
- **Privacy rules:** no `INTERNET` permission, ever; never log typed text or anything from a
  password field; never read or cache the content of inline autofill suggestions, only host the
  views the manager draws. The one exception is the fill screen (`autofill/FillActivity.kt`): a
  password the user picked there goes through `PendingFill` to the package it was requested for,
  once and within 30 s, and is wiped after; it is never shown by the keyboard (not even in the
  strip), logged, stored, or put in an `Intent` or `Bundle`.
- **Emulator quirks:** it reports a hardware keyboard, so set
  `adb shell settings put secure show_ime_with_hard_keyboard 1` or the keyboard never shows;
  `am force-stop` on the package while it is the current IME makes Android fall back to Gboard;
  `connectedDebugAndroidTest` uninstalls the app afterwards, so run `scripts/enable-ime.sh`
  again; `uiautomator dump` does not include the IME window, so the connected tests tap a key at
  its accessibility node's bounds (`UiAutomation` windows), never at a position worked out from
  the layout: the bars the keyboard pads for differ by device.
- **Generated, never hand-edited:** `app/build/`, `.gradle/`. `design/*.dc.html` are mock
  artboards generated by a script kept outside the repository; edit the mocks by regenerating.
- **Device checks still open:** `docs/combo-matrix.md` (Termux, Chrome, a code editor),
  `docs/autofill-matrix.md` (Enpass, Google Password Manager) and `docs/talkback-matrix.md` are
  templates to fill on a phone.
- **Licence:** Apache-2.0 (`LICENSE`). Third-party Apache code and data are listed in `NOTICE`; a
  copied file keeps its original header and says what changed. No GPL sources, ever. The app
  carries `LICENSE`, `NOTICE` and `licences/BSD-3-Clause.txt` as `res/raw` (the build copies
  them) and shows them under Settings → About → Open-source licences, with the libraries it is
  built on: `res/raw/dependencies.txt` is generated from the dependencies' POMs by
  `./gradlew :app:generateDependencyLicences` and committed, the way the word lists are.
  `checkDependencyLicences` (in CI) fails when it drifts, and the task itself fails on a licence
  the screen has no text for — add the text under `licences/` and to `KNOWN_LICENCES` first.
- **Branching:** `main` is the base; work lands through pull requests from feature branches.
