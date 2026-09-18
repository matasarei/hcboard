# Store Listing, Metadata & Publishing Guide

This guide contains copy-paste metadata and step-by-step submission instructions for:
1. **GitHub Releases & Obtainium** (Instant, $0, auto-updating directly from GitHub)
2. **IzzyOnDroid (F-Droid)** (Automatic inclusion in the F-Droid client ecosystem)
3. **Samsung Galaxy Store** ($0, no 20-tester rule, pre-installed on all Galaxy devices, bypasses Samsung Auto Blocker)
4. **Google Play Protect Whitelisting** (Clearing "Unsafe app / Unrecognized developer" prompts on direct APK downloads)
5. **Google Play Store** (Optional mass-market distribution)

---

## Store Listing Metadata

### App Title (max 30 chars)
```
hcboard: Keyboard & Terminal
```
*(Alternative: `hcboard`)*

### Short Description (max 80 chars)
```
Private keyboard: 60% layout, developer keys, glide typing & zero network.
```
*(75 characters)*

### Category & Tags
* **Primary Category:** Tools / Productivity
* **Tags / Keywords:** Keyboard, Input Method, IME, Developer Tools, Terminal, Termux, 60% Keyboard, Foldable, Privacy, Open Source

---

### Full Description (Play Store & Galaxy Store)

```markdown
hcboard is an on-screen keyboard for Android built for everyday typing and ready when you want to tinker — while keeping strictly to itself.

🔒 ABSOLUTE PRIVACY GUARANTEE
• Zero Network Access: hcboard does not request the INTERNET permission. The Android system physically prevents it from making network connections.
• Zero Logging: Nothing you type is recorded, stored, or transmitted.
• Local-Only Dictionaries: All language vocabularies ship directly inside the app. No data leaves your phone.
• Secure Password Handover: Password fields use Android's FLAG_SECURE against screenshots and recents previews. Credentials from your password manager are kept in memory for a single one-time injection within 30 seconds and immediately wiped.

💻 WHEN YOU WANT TO TINKER
• 60% Board on Wide Screens: Unfold your phone or pick up a tablet for a full 60% layout with Esc, Tab, Ctrl, Alt, Meta, symbols printed on keys, and F1–F12 behind Fn.
• Developer Strip on Phones: Tap </> to add Esc, Tab, Ctrl, Alt, Shift, arrows, and Fn directly above any layer.
• Real Terminal Shortcuts: Ctrl+C in Termux and SSH sessions sends real interrupt signals. In standard text fields, Ctrl+A/C/V/X provide select, copy, paste, and cut.
• Positional Cyrillic Shortcuts: Ctrl+С on Ukrainian/Russian layouts sends Ctrl+C, showing US key slots while modifiers are latched.

✨ EVERYDAY COMFORT
• Glide Typing: Slide across letters in any of ten supported languages.
• Word Suggestions & Autocorrect: Lightweight, local word candidates above the keys. Tap space to fix a typo, backspace to undo.
• 10 Languages: English, Ukrainian, Russian, Bulgarian, German, French, Spanish, Italian, Portuguese (Brazil), and Polish.
• Material 3 Design: Full dynamic colour on Android 12+, with Light, Dark, Black, and System themes.
• Customisable Ergonomics: Adjust keyboard height, key borders, haptic feedback, and space under the keys.

Fully open source under the Apache-2.0 licence.
Source code: https://github.com/matasarei/hcboard
```

---

## Distribution Channel 1: GitHub Releases + Obtainium

Obtainium acts as a personal open-source app store on Android, fetching updates directly from your GitHub Releases in the background.

### How Users Install via Obtainium
1. Install [Obtainium](https://github.com/ImranR98/Obtainium) from GitHub or F-Droid.
2. Open Obtainium, tap **Add App**, and paste:
   ```
   https://github.com/matasarei/hcboard
   ```
3. Tap **Add**. Obtainium will download `hcboard-vX.Y.Z.apk` and notify the user whenever you push a new release tag.

### Publishing a New Release
1. Update `versionCode` and `versionName` in `app/build.gradle.kts` (e.g. `versionCode = 2`, `versionName = "0.2.0"`).
2. Commit and push:
   ```bash
   git tag v0.2.0
   git push origin v0.2.0
   ```
3. GitHub Actions (`.github/workflows/release.yml`) builds, signs, and attaches `hcboard-v0.2.0.apk` and `hcboard-v0.2.0.aab` automatically.

---

## Distribution Channel 2: IzzyOnDroid (F-Droid Repository)

[IzzyOnDroid](https://apt.izzysoft.de/fdroid/) is the premier companion repository for F-Droid, allowing F-Droid users to find and update hcboard automatically.

### Submission Steps:
1. Ensure at least one tagged release exists on GitHub with an attached release APK.
2. Open an inclusion request issue at:
   [https://gitlab.com/IzzyOnDroid/repo/-/issues/new](https://gitlab.com/IzzyOnDroid/repo/-/issues/new)
3. Fill out the short template:
   - **App Name:** hcboard
   - **Repository:** `https://github.com/matasarei/hcboard`
   - **Licence:** Apache-2.0
   - **Release Tag format:** `v*`
4. Once merged (typically 24–48 hours), IzzyOnDroid tracks your releases automatically.

---

## Distribution Channel 3: Samsung Galaxy Store ($0 Official Channel)

The Samsung Galaxy Store is pre-installed on all Samsung phones, foldables, and tablets. **Publishing here completely bypasses Samsung Auto Blocker** and gives users a seamless 1-tap install with $0 account fees and no 20-tester requirement.

### Submission Steps:
1. **Register Developer Account:** Go to [Samsung Galaxy Store Developer Portal](https://developer.samsung.com/galaxy-store) and sign in with a Samsung account ($0 fee).
2. **Add New Application:**
   - App Type: Android
   - Default Language: English
   - Title: `hcboard: Keyboard & Terminal`
3. **App Information:**
   - Paste Short and Full Descriptions from above.
   - Category: `Utility` or `Productivity`.
   - Age Rating: All / 4+.
   - Privacy Policy URL: `https://github.com/matasarei/hcboard/blob/main/docs/PRIVACY.md`.
4. **Graphic Assets:**
   - App Icon: Upload `docs/assets/icon-512.png` (512x512 PNG).
   - Screenshots: Upload 4+ screenshots (phone + foldable/tablet).
5. **Binary Upload:**
   - Upload `app-release.aab` or `hcboard-vX.Y.Z.apk`.
6. **Submit for Review:** Samsung reviews the app within 1–3 business days. Once approved, it is live in the Galaxy Store worldwide.

---

## Distribution Channel 4: Clearing Google Play Protect Sideload Warnings

When users download a newly signed APK directly outside of app stores, Google Play Protect may show a warning because the signing certificate is new. You can register your certificate with Google for free to remove this warning:

### How to Submit for Whitelisting:
1. Get the SHA-256 fingerprint of your release keystore:
   ```bash
   keytool -list -v -keystore "$HCBOARD_RELEASE_KEYSTORE" -alias "$HCBOARD_RELEASE_KEY_ALIAS"
   ```
   Look for the `SHA256:` fingerprint line (e.g., `AB:CD:12:34:...`).
2. Open the **Google Play Protect Developer Appeal Form**:
   [https://support.google.com/googleplay/android-developer/contact/protectappeals](https://support.google.com/googleplay/android-developer/contact/protectappeals)
3. Fill in the fields:
   - **Package Name:** `net.matasar.keyboard`
   - **SHA-256 Fingerprint:** Paste the fingerprint from step 1.
   - **Download Link / Website:** `https://github.com/matasarei/hcboard/releases`
   - **Description:** *"Open-source keyboard application with zero network permissions. Clean utility app seeking certificate whitelisting for direct user downloads."*
4. Google typically updates their Play Protect definitions within 2–3 business days, after which direct APK installs show no security warnings.

---

## Distribution Channel 5: Google Play Store (Optional)

If you decide to register a Google Play Developer account ($25 fee):

1. **Data Safety Questionnaire:**
   - Does your app collect or share user data? **No**
   - Data collection declaration: All toggles set to **No** (due to zero network permission).
2. **Content Rating (IARC):**
   - Utility app $\rightarrow$ Rating: PEGI 3 / Everyone.
3. **Closed Testing Track (20 Testers):**
   - Create a Google Group: `hcboard-testers@googlegroups.com`.
   - Add the Google Group under Closed Testing $\rightarrow$ Testers.
   - Recruit testers from `r/AndroidClosedTesting` and `r/fossdroid`.
   - Keep 20 testers opted in for 14 continuous days before requesting production access.
