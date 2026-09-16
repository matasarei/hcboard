# Password managers: what arrives in the keyboard strip

Filled in on a device, per step 9 of the plan. Set each manager as the preferred autofill
service (Settings → Passwords, passkeys & autofill), focus a login form, and note what shows.

| Manager | Inline chips in the strip | Pinned icon opens its picker | Fallback dropdown above the keyboard | Notes |
|---|---|---|---|---|
| Google Password Manager | untested: the emulator has no saved credentials | | | Emulator, API 36: `Inline Suggestions Enabled: true` in `dumpsys autofill`; no request seen for Chrome |
| Enpass | | | | Its help page does not say whether it sends inline suggestions |
| Bitwarden / 1Password (if installed) | | | | |

Chrome on Android does not use the Android autofill framework by default: it shows its own
accessory bar above the keyboard (passwords, payments, addresses) and fills from its own store.
Third-party managers there need Chrome's "Use other providers" setting.

Legend: ✓ works · ✗ not shown · ~ partial (say what).
