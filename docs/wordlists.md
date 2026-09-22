# Word lists: where they come from and how to rebuild them

The dictionaries the glide engine and the suggestion strip read (`app/src/main/assets/dictionaries/`).
Moved here from `CLAUDE.md`, which points to this file; the rules are unchanged.

## Sources

- Built by `scripts/build-wordlist.py` from AOSP's LatinIME word lists (Apache-2.0).
- **Ukrainian:** Helium314's CC BY 4.0 list, plus the curated overlay
  `scripts/wordlists/uk-everyday.tsv` (the corpus is news text and under-rates chat words).
- **English:** AOSP plus `scripts/wordlists/en-modern.tsv` (essential tech and modern chat words).
- **Every other list** has its own chat overlay, `scripts/wordlists/<language>-everyday.tsv`, with
  tiers matched to its corpus's scale: ru 175/155/135, bg 255/230/210, the rest 200/180/165.
  German nouns keep their capital.

## Rebuilding

- **Regenerate, never hand-edit.** The builder reads a shipped asset as its source, so
  `scripts/build-wordlist.py <asset> <asset> --boost <the tsv>` rebuilds it. `bg.txt` needs
  `--floor 0`: its frequencies go down to 2.
- Each `*OverlayTest` (on `DictionaryOverlayTest`) fails when an asset drifts below its overlay. The
  test task does not track `scripts/wordlists/`, so run it with `--rerun` after editing only an
  overlay.
- Before raising a short word, check it does not outrank a more common one-edit neighbour.
- An е spelling of a ё word (идет) is corrected to the ё word by `Candidates`, so overlays leave е
  spellings out.

## Russian + Bulgarian

The combined dictionary `ru_bg.txt` is built by `scripts/build-ru-bg-wordlist.py` with collision
protection (1-edit Bulgarian words capped at 75 < 80) and loaded when `ruBulgarianVocabulary` is
active. Rebuild it after changing `ru.txt` or `bg.txt`.
