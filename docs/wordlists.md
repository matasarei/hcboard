# Word lists: where they come from and how to rebuild them

The dictionaries the glide engine and the suggestion strip read (`app/src/main/assets/dictionaries/`).
Moved here from `CLAUDE.md`, which points to this file; the rules are unchanged.

## Sources

- Built by `scripts/build-wordlist.py` from AOSP's LatinIME word lists (Apache-2.0).
- **Ukrainian:** Helium314's CC BY 4.0 list, plus the curated overlay
  `scripts/wordlists/uk-everyday.tsv` (the corpus is news text and under-rates chat words).
- **English:** AOSP plus `scripts/wordlists/en-modern.tsv` (essential tech and modern chat words).
- **Dutch, Swedish, Danish, Finnish, Czech, Romanian, Greek, Croatian, Slovenian, Lithuanian,
  Latvian and European Portuguese** (`pt_PT`) are the AOSP lists alone, built with the defaults
  (`--floor 60 --max 80000`); their chat overlays are still to come.
- **Every other list** has its own chat overlay, `scripts/wordlists/<language>-everyday.tsv`, with
  tiers matched to its corpus's scale: ru 175/155/135, bg 255/230/210, the rest 200/180/165.
  German nouns keep their capital.
- **Russian and Ukrainian IT and chat words** (коммит, задеплоить, фича, крінж) come in every form,
  because a list holding one form of a word corrects the others back to it (фичу to фича). They
  are written as lemmas with a paradigm class in `scripts/wordlists/<ru|uk>-tech.lemmas.tsv`, and
  `scripts/expand-paradigms.py` writes their forms into `<ru|uk>-tech.tsv` (generated, never
  edited; its classes, `!form` exclusions and `--self-test` are in its docstring). A lemma keeps
  its tier and its other forms rank `--form-step` below it (ru 20, uk 45): being known is what
  stops a correction, and a form ranked higher wins the correction of a common word's typo (мему
  for тему). With `--asset` it refuses an е spelling of a ё word and prints the short forms that
  outrank a common one-edit neighbour.
- `app/src/test/resources/chat-probe/` holds the words each of ru, uk and en must know and never
  correct, and the typos of common words that must still correct (`ChatVocabularyTest`). A word
  found missing goes into the probe first, then into the overlay.

## Words with apostrophes

- A word is letters with apostrophes between them (don't, розв'язок, c'est, dell'anno); `’` and
  `ʼ` are stored as `'`. The builder keeps them, and `--max` caps only the words without an
  apostrophe, so adding them never pushes out a word a list already had.
- **English, French and Italian** take theirs from the AOSP sources with the list (elided forms
  such as l'eau and c'est are whole words there, so elision needs no rules of its own).
- **Ukrainian:** Helium314's frequencies for apostrophe words are flat (almost all 10) or shared
  by a family of forms, so `scripts/uk-apostrophe-words.py <main_uk.combined>` writes
  `scripts/wordlists/uk-apostrophe.tsv`, every one at 70 (just above the list's floor), and
  `uk-everyday.tsv` gives the everyday ones their tiers. Rebuild with every boost:
  `scripts/build-wordlist.py <uk.txt> <uk.txt> --floor 0 --max 90000 --boost scripts/wordlists/uk-everyday.tsv --boost scripts/wordlists/uk-apostrophe.tsv --boost scripts/wordlists/uk-tech.tsv`.
  A boost only raises: to lower a word, rebuild from the list as it was before.
- After a rebuild, `grep -v "'"` of the new list must equal the old list.

## Rebuilding

- **Regenerate, never hand-edit.** The builder reads a shipped asset as its source, so
  `scripts/build-wordlist.py <asset> <asset> --boost <the tsv>` rebuilds it. `bg.txt` needs
  `--floor 0`: its frequencies go down to 2.
- `ru.txt` and `uk.txt` hold more than the default `--max 80000` words once an overlay adds to
  them, so rebuild them with `--max 90000`, or the words at the floor (квота, розумне) fall out
  and are then corrected away. Russian:
  `scripts/build-wordlist.py <ru.txt> <ru.txt> --max 90000 --boost scripts/wordlists/ru-everyday.tsv --boost scripts/wordlists/ru-tech.tsv`,
  then `ru_bg.txt` (below). After editing a lemma file, run `expand-paradigms.py` first.
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
