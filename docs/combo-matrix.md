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
