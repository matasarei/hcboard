# Glide typing: what the phone recognises

Filled in on a device, per step 6 of the glide plan. Glide each word once at a normal pace on the
letters layer and note the committed word, the alternatives offered, and how long the word took
to appear after lifting the finger (a stopwatch is fine; under a quarter of a second reads as
instant).

| Word | Committed | Alternatives | Delay | Notes |
|---|---|---|---|---|
| hello | | | | double l: a small loop or a pause on l |
| world | | | | |
| keyboard | | | | |
| thanks | | | | |
| morning | | | | |
| please | | | | |
| tomorrow | | | | double r |
| because | | | | |
| people | | | | |
| android | | | | |

Emulator, API 36 (Pixel-class profile), before this sheet was filled: the smoke test's glide
through h, e, l (loop), o commits "hello"; a straight h-e-l-o line ranks "help" (frequency 149)
just above "hello" (120), which is the classifier working as designed. The slowest of five
classifications over the 43,920-word list took 16 ms on the JVM.

If a common word never appears, raise its frequency floor in `scripts/build-wordlist.py` or
check that the AOSP list has it; if recognition is slow, lower the floor to shrink the list.
