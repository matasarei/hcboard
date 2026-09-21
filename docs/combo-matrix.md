# Modifier combinations: what reaches which app

Filled in on a device, per step 5 of the plan. A key event with Ctrl meta state is what the
keyboard sends; Android leaves it to each app whether to honour key events from a soft keyboard.

| App | Field type | Ctrl+C | Ctrl+A / V / X | Ctrl+Z | Esc, Tab | Arrows, Home/End, PgUp/PgDn | Notes |
|---|---|---|---|---|---|---|---|
| Plain `EditText` (this app's Settings) | text | | | | | | Ctrl+A/C/V/X go through the editing fallback |
| Chrome (address bar, page textarea) | text / web | | | | | | |
| Termux | `TYPE_NULL` | | | | | | Ctrl+C must interrupt; fallback is off for `TYPE_NULL` |
| Code editor (e.g. Acode) | text | | | | | | |
| Any of the above, Ukrainian or Russian board | as above | | | | | | Ctrl+С must behave as Ctrl+C: the key sends its slot, and the keys read Q W E R T Y while Ctrl is armed |

Legend: ✓ works · ✗ ignored · ~ partial (say what).

## Macros

Played from the toolbar's macro sheet. Random keys are key events with Shift for capitals and
shifted symbols, so they reach an app only as far as it honours soft-keyboard key events.

| App | Field type | Password generator (random keys) | Esc / F1 key blocks | Ctrl+Shift combination | Repeat ×3 Tab | Notes |
|---|---|---|---|---|---|---|
| Plain `EditText` (this app's Settings) | text | ✓ (emulator, Ukrainian board active) | | | | |
| Chrome (address bar, page textarea) | text / web | | | | | |
| Termux | `TYPE_NULL` | | | | | Ctrl+C block must interrupt |
| Code editor (e.g. Acode) | text | | | | | |

