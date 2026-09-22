# TalkBack: what a screen-reader user can do with the keyboard

Each key is one accessibility node (`ui/KeyButton.kt`): a Button whose description is what the key
types now, or its name (`ui/KeySpeech.kt`), with a click that types it. Latching keys add their
state; the key grid's pane title names the page. TalkBack's own typing preference decides how a
key is typed — double-tap, or lift-to-type — and both go through that click. The keyboard has no
hover handling of its own, and must not grow one: it would type each key twice.

Filled in on a device. The first row was checked on the emulator on 2026-09-22.

| Device | TalkBack | Lift-to-type | Double-tap to type | Letters follow Shift | Named keys (Space, Backspace, ?123…) | Shift / modifier state read | Page announced on switch | Notes |
|---|---|---|---|---|---|---|---|---|
| Emulator `medium_phone`, Android 16 (API 36) | 16.0 | ✓ (touch on Q and W, lift: `Qw`) | | ✓ (connected test: Q while the automatic capital is on) | ✓ (connected test: Space, Shift) | ✓ (connected test: Off → On for the next key) | ✓ pane title present (connected test); not listened to | Speech itself was not heard: the emulator runs without audio |
| Phone, One UI / Pixel | | | | | | | | |
| 60% board (Fold inner screen or a tablet) | | | | | | | | Fn meanings (Del, Home) should be what is read |
| Developer mode strip | | | | | | | | Ctrl/Alt/Fn states |
| Any phone: keyboard shown again in the same field | | | | | | | | Is the page title ("Letters, English") spoken on every show, not only on a switch? If it is noisy, move it to a live region that speaks changes only |
| Any phone: password field | | | | | | | | Speaker: keys say Dot; with headphones: the characters |

Legend: ✓ works · ✗ does not · ~ partial (say what).

## What a long press holds

Holding a key opens the accents, the cursor trackpad or the language picker, and touch
exploration takes the finger before the keys ever see it. Each is an action on the key's own
node instead (`ui/KeyActions.kt`), which TalkBack offers under **Actions** for the focused key.
The connected tests perform them through the accessibility tree; what they *sound* like, and
whether the Actions menu is a reasonable way to reach them, needs a phone.

| Device | TalkBack | Accents on a letter | Cursor left/right on Space | By word on Space | Choose language on the globe | Notes |
|---|---|---|---|---|---|---|
| Emulator `medium_phone`, Android 16 (API 36) | actions read from the tree, no TalkBack | ✓ é typed from the e key | ✓ a, b, left, c reads `acb` | not checked | ✓ opens the sheet | Performed through `AccessibilityNodeInfo`, so this proves the actions exist and work, not how they are announced |
| Phone, One UI / Pixel | | | | | | Do the labels read well after the key's own name? Is the by-word pair honoured by the app being typed into? |

## Not covered yet

- Glide typing stays a touch gesture with no alternative: under touch exploration TalkBack takes
  the finger, and nothing else offers it.
- Whether TalkBack's own character and word navigation already moves the cursor inside a text
  field. Simulated swipes on the emulator did not move it (`mCursorSelStart` stayed put), but
  simulated gestures are not proof; a phone settles it. If it does, Space's four actions are
  redundant rather than wrong.
- The toolbar, the gear sheet and the macro and password-manager sheets had descriptions before
  this work; they were not rechecked here.
