# Privacy Policy for hcboard

**Effective Date:** September 19, 2026  
**Repository:** [github.com/matasarei/hcboard](https://github.com/matasarei/hcboard)  
**Licence:** Apache-2.0  

hcboard is an open-source on-screen keyboard (Input Method Editor) for Android designed with a strict, verifiable commitment to user privacy: **it does not collect, store, transmit, or monetize any user data.**

---

## 1. No Network Access (The Technical Guarantee)

hcboard does **not** request the `android.permission.INTERNET` permission in its Android Manifest. 

Because the permission is absent, the Android operating system sandbox strictly prevents the application from establishing network connections, sending HTTP requests, or transmitting data over Wi-Fi or cellular networks. Even if code attempted to send data, Android would block it at the system kernel level.

There are no remote servers, no analytics, no crash reporting SDKs, no ad frameworks, and no tracking libraries of any kind.

---

## 2. Keystrokes & Input Data

* **Zero Keystroke Logging:** Nothing you type is ever recorded, logged, or saved to storage.
* **No Cloud Processing:** All keyboard operations (typing, modifier chording, glide typing, suggestion generation) occur 100% locally on your device.
* **Transient Memory:** Text input is processed in real-time and handed directly to the active application via Android's standard `InputConnection` interface.

---

## 3. Word Suggestions & Dictionaries

* **Local Bundled Dictionaries:** All vocabulary and language word lists ship directly inside the app package (`assets/dictionaries/`). No dictionaries or language models are ever downloaded from the internet.
* **Candidate Calculation:** Autocorrect and completion suggestions inspect only the word immediately preceding the text cursor.
* **Sensitive Field Opt-Out:** In compliance with Android IME guidelines, suggestions and autocorrect are automatically and completely disabled in password fields, PIN/number fields, e-mail fields, and terminal emulators.

---

## 4. Passwords & Autofill Security

* **Inline Suggestions:** Autofill suggestion chips shown in the toolbar are rendered directly by your device's chosen Password Manager via AndroidX Autofill. hcboard merely hosts the container view and cannot read or inspect the credentials inside those views.
* **"Fill a Login" Screen (`FillActivity`):** When filling credentials into terminal emulators or apps that do not support inline suggestions:
  * The screen is locked with `WindowManager.LayoutParams.FLAG_SECURE` to prevent screenshots, screen recording, and exposure in recent-app previews.
  * The username and password provided by your password manager are stored only in transient memory for a maximum of 30 seconds for a single, one-time injection into the requesting application. Started from a username field, the username is typed there and the password into the next password field of the same application, within 10 seconds; started from a password field or a terminal, only the password is typed. Switching to another application discards them.
  * Once committed, the memory reference is immediately wiped. Credentials are never written to disk, never logged, and never included in Android Intents or Bundles.

---

## 5. Local Settings & Diagnostics

* **Local Preferences:** User preferences (such as keyboard height, selected theme, haptic feedback, and enabled languages) are stored purely on your device using Android Jetpack DataStore.
* **Diagnostics:** The diagnostics section in Settings displays only window insets, input types, and target package names held in transient memory to help diagnose layout issues. It never displays or records typed content.

---

## 6. Children's Privacy

hcboard does not collect, store, or transmit personal information from anyone, including children under the age of 13.

---

## 7. Open Source Verification

Because privacy claims should be independently verifiable, hcboard is fully open source under the Apache License 2.0. The complete source code, build scripts, and resource definitions are publicly available at:

[https://github.com/matasarei/hcboard](https://github.com/matasarei/hcboard)

---

## 8. Contact

If you have any questions or feedback regarding this Privacy Policy or hcboard's security architecture, you may open an issue on GitHub:

* **GitHub Issues:** [https://github.com/matasarei/hcboard/issues](https://github.com/matasarei/hcboard/issues)
