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

Legend: ✓ works · ✗ does not · ~ partial (say what).

## Not covered yet

- Glide typing, long-press accents and the space-bar trackpad are touch gestures; under touch
  exploration TalkBack takes the finger, so none of them is expected to be reachable. Not checked,
  and no alternative is offered yet.
- The toolbar, the gear sheet and the macro and password-manager sheets had descriptions before
  this work; they were not rechecked here.
