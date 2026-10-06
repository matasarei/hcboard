#!/usr/bin/env python3
"""Expands a lemma overlay into every form of each word, for Russian and Ukrainian.

The corpora under-rate the words people type in chats and at work (коммит, фича, задеплоить), and
an overlay that lists one form of a word does more harm than good in these languages: every other
form (фичу, коммиты) is then one edit from a known word and is corrected back to it. So the
overlay lists lemmas with a paradigm class, and this script writes all their forms:

    # lemma<TAB>tier<TAB>class (no class, or "-": the word only)
    коммит	155	noun-m
    фича	155	noun-f
    закоммитить	155	verb-i

    scripts/expand-paradigms.py --lang ru --form-step 20 --asset app/src/main/assets/dictionaries/ru.txt \
        scripts/wordlists/ru-tech.lemmas.tsv scripts/wordlists/ru-tech.tsv

The lemma keeps its tier; every other form is --form-step below it. Being known is what keeps a
form from being corrected; its rank only orders the strip, and a form ranked as high as its lemma
would win the correction of a typo of a common word (мему over тему). A form two lemmas share
keeps the higher. A line `!form` leaves that one form out (кожу, a form of кодить that is also
кожа's). The output is an ordinary overlay for build-wordlist.py
--boost, and is generated: edit the lemma file and run this again, never edit the output.

The suffix tables are our own and deliberately small. Classes (the same names in both languages):

    noun-m    masculine, hard or sibilant stem (коммит, лог, пуш; uk коміт, мердж)
    noun-m-j  masculine in -й (деплой)
    noun-f    feminine in -а (фича, таска; the genitive plural only for a stem ending in one
              consonant, so тасок is listed by hand)
    adj       adjective (ru -ый/-ий/-ой, uk -ий)
    verb-i    second conjugation, ru -ить, uk -ити/-їти (пушить, закоммитить, задеплоить), with
              the consonant change in the first person (коммичу, фикшу; uk закомічу, пофікшу)
    verb-a    first conjugation in -ать/-ати (лайкать)
    verb-nu   in -нуть/-нути (лайкнуть)
    verb-ova  ru -овать, uk -увати (тестировать, тестувати)
    a verb class on a lemma ending in -ся (залогиниться) gives the reflexive forms.

Anything irregular is written out on its own lines with no class. With --asset, a form that is the
е spelling of a ё word the list knows (тещу, тёщу) is an error, because Candidates restores the ё
only in a word it does not know; and every form of four letters or fewer that outranks a common
one-edit neighbour (100 and up) is printed for review, which changes nothing.

    scripts/expand-paradigms.py --self-test
"""
import argparse
import re
import sys

VOWELS = "аеёиіїоуыэюяє"
VELARS = "гкх"
SIBILANTS = "жшчщ"
LABIALS = "бпвфм"


def ends_in_cluster(stem: str) -> bool:
    """Whether the stem ends in two consonants (фикс, гугл, мерж), which takes и in the imperative."""
    return len(stem) >= 2 and stem[-1] not in VOWELS + "ь'" and stem[-2] not in VOWELS + "ь'"


def mutate(stem: str, lang: str) -> str:
    """The stem of a second-conjugation first person: коммит -> коммич, фикс -> фикш, гугл stays."""
    rules = [("ст", "щ"), ("т", "ч"), ("с", "ш"), ("з", "ж")]
    rules.insert(2, ("д", "дж" if lang == "uk" else "ж"))
    if lang == "uk":
        rules.insert(0, ("зд", "ждж"))
    for old, new in rules:
        if stem.endswith(old):
            return stem[: -len(old)] + new
    if stem and stem[-1] in LABIALS:
        return stem + "л"
    return stem


def ru_noun(stem: str, ending: str) -> str:
    """Russian spelling rules on a noun or adjective ending: ы after г к х ж ш ч щ, unstressed о after a sibilant."""
    last = stem[-1:]
    if last in SIBILANTS + "ц" and ending == "ов":
        ending = "ей" if last in SIBILANTS else "ев"
    if last in VELARS + SIBILANTS and ending.startswith("ы"):
        ending = "и" + ending[1:]
    if last in SIBILANTS + "ц" and ending.startswith("о"):
        ending = "е" + ending[1:]
    return stem + ending


def uk_noun(stem: str, ending: str) -> str:
    """Ukrainian spelling rules on a noun ending: the mixed group after a sibilant (ключі, ключем)."""
    if stem[-1:] in SIBILANTS:
        ending = {"и": "і", "ом": "ем", "ові": "еві", "ою": "ею"}.get(ending, ending)
    return stem + ending


def noun_m(lemma: str, lang: str) -> list[str]:
    if lang == "ru":
        return [ru_noun(lemma, e) for e in ["", "а", "у", "ом", "е", "ы", "ов", "ам", "ами", "ах"]]
    # The genitive in -а and in -у are both in use for these words (коміта, коміту): both are known.
    forms = [uk_noun(lemma, e) for e in ["", "а", "у", "ові", "ом", "и", "ів", "ам", "ами", "ах"]]
    # The locative: -і, but -у after г к х (у логу), as the dative's -ові is already there.
    if lemma[-1] not in VELARS:
        forms.append(uk_noun(lemma, "і"))
    return forms


def noun_m_j(lemma: str, lang: str) -> list[str]:
    if not lemma.endswith("й"):
        raise ValueError("noun-m-j needs a lemma in -й")
    stem = lemma[:-1]
    endings = (
        ["й", "я", "ю", "ем", "е", "и", "ев", "ям", "ями", "ях"]
        if lang == "ru"
        else ["й", "я", "ю", "єві", "єм", "ї", "їв", "ям", "ями", "ях"]
    )
    return [stem + e for e in endings]


def noun_f(lemma: str, lang: str) -> list[str]:
    if not lemma.endswith("а"):
        raise ValueError("noun-f needs a lemma in -а")
    stem = lemma[:-1]
    if lang == "ru":
        forms = [ru_noun(stem, e) for e in ["а", "ы", "е", "у", "ой", "ою", "ам", "ами", "ах"]]
    else:
        changed = stem[:-1] + {"к": "ц", "г": "з", "х": "с"}.get(stem[-1], stem[-1])
        forms = [uk_noun(stem, e) for e in ["а", "и", "у", "ою", "ам", "ами", "ах"]] + [uk_noun(changed, "і")]
    # The genitive plural is the bare stem only when no vowel has to be put in (фич, либ; but тасок).
    if not ends_in_cluster(stem):
        forms.append(stem)
    return forms


def adj(lemma: str, lang: str) -> list[str]:
    stem = lemma[:-2]
    if lang == "ru":
        if lemma[-2:] not in ("ый", "ий", "ой"):
            raise ValueError("adj needs a lemma in -ый, -ий or -ой")
        # The lemma stands for the masculine nominative: крутой, not крутый.
        endings = ["ая", "ое", "ые", "ого", "ой", "ому", "ую", "ым", "ыми", "ых", "ом"]
        return [lemma] + [ru_noun(stem, e) for e in endings]
    if not lemma.endswith("ий"):
        raise ValueError("adj needs a lemma in -ий")
    return [stem + e for e in ["ий", "а", "е", "і", "ого", "ій", "ому", "у", "им", "ими", "их", "ім"]]


def imperative(stem: str, lang: str) -> list[str]:
    """Singular and plural: задеплой, закоммить, пофикси; uk задеплой, закоміть, пофікси, запуш."""
    if stem[-1] in VOWELS:
        return [stem + "й", stem + "йте"]
    if ends_in_cluster(stem):
        return [stem + "и", stem + ("іть" if lang == "uk" else "ите")]
    if lang == "uk" and stem[-1] in SIBILANTS + LABIALS:
        return [stem, stem + "те"]
    return [stem + "ь", stem + "ьте"]


def verb_i(lemma: str, lang: str) -> list[str]:
    if lang == "ru":
        if not lemma.endswith("ить"):
            raise ValueError("verb-i needs a lemma in -ить")
        stem = lemma[:-3]
        soft = stem[-1] not in SIBILANTS
        first = mutate(stem, lang)
        first += "ю" if first[-1] not in SIBILANTS else "у"
        third = stem + ("ят" if soft else "ат")
        return [lemma, stem + "ил", stem + "ила", stem + "ило", stem + "или", first, stem + "ишь", stem + "ит",
                stem + "им", stem + "ите", third] + imperative(stem, lang)
    if lemma.endswith("їти"):
        stem, i = lemma[:-3], "ї"
    elif lemma.endswith("ити"):
        stem, i = lemma[:-3], "и"
    else:
        raise ValueError("verb-i needs a lemma in -ити or -їти")
    first = stem if i == "ї" else mutate(stem, lang)
    first += "у" if first[-1] in SIBILANTS else "ю"
    third = stem + ("ать" if stem[-1] in SIBILANTS else "ять")
    return [lemma, stem + i + "в", stem + i + "ла", stem + i + "ло", stem + i + "ли", first, stem + i + "ш",
            stem + i + "ть", stem + i + "мо", stem + i + "те", third] + imperative(stem, lang)


def verb_a(lemma: str, lang: str) -> list[str]:
    infinitive = "ать" if lang == "ru" else "ати"
    if not lemma.endswith(infinitive):
        raise ValueError(f"verb-a needs a lemma in -{infinitive}")
    stem = lemma[:-3]
    endings = (
        ["ать", "ал", "ала", "ало", "али", "аю", "аешь", "ает", "аем", "аете", "ают", "ай", "айте"]
        if lang == "ru"
        else ["ати", "ав", "ала", "ало", "али", "аю", "аєш", "ає", "аємо", "аєте", "ають", "ай", "айте"]
    )
    return [stem + e for e in endings]


def verb_nu(lemma: str, lang: str) -> list[str]:
    infinitive = "нуть" if lang == "ru" else "нути"
    if not lemma.endswith(infinitive):
        raise ValueError(f"verb-nu needs a lemma in -{infinitive}")
    stem = lemma[:-4]
    endings = (
        ["нуть", "нул", "нула", "нуло", "нули", "ну", "нешь", "нет", "нем", "нете", "нут", "ни", "ните"]
        if lang == "ru"
        else ["нути", "нув", "нула", "нуло", "нули", "ну", "неш", "не", "немо", "нете", "нуть", "ни", "ніть"]
    )
    return [stem + e for e in endings]


def verb_ova(lemma: str, lang: str) -> list[str]:
    infinitive = "овать" if lang == "ru" else "увати"
    if not lemma.endswith(infinitive):
        raise ValueError(f"verb-ova needs a lemma in -{infinitive}")
    stem = lemma[:-5]
    endings = (
        ["овать", "овал", "овала", "овало", "овали", "ую", "уешь", "ует", "уем", "уете", "уют", "уй", "уйте"]
        if lang == "ru"
        else ["увати", "ував", "увала", "увало", "ували", "ую", "уєш", "ує", "уємо", "уєте", "ують", "уй", "уйте"]
    )
    return [stem + e for e in endings]


def reflexive(form: str, lang: str) -> str:
    """ru -ся after a consonant and -сь after a vowel (залогинился, залогинилась); uk always -ся."""
    if lang == "ru" and form[-1] in VOWELS:
        return form + "сь"
    return form + "ся"


CLASSES = {
    "noun-m": noun_m,
    "noun-m-j": noun_m_j,
    "noun-f": noun_f,
    "adj": adj,
    "verb-i": verb_i,
    "verb-a": verb_a,
    "verb-nu": verb_nu,
    "verb-ova": verb_ova,
}


def forms_of(lemma: str, paradigm: str, lang: str) -> list[str]:
    """Every form of [lemma] in [paradigm], the lemma first, without repeats."""
    if paradigm in ("", "-"):
        return [lemma]
    if paradigm not in CLASSES:
        raise ValueError(f"unknown class {paradigm!r}")
    if paradigm.startswith("verb-") and lemma.endswith("ся"):
        forms = [reflexive(f, lang) for f in CLASSES[paradigm](lemma[:-2], lang)]
    else:
        forms = CLASSES[paradigm](lemma, lang)
    seen: list[str] = []
    for form in [lemma] + forms:
        if form not in seen:
            seen.append(form)
    return seen


LEMMA_LINE = re.compile(r"([^\t]+)\t(\d+)(?:\t([^\t]*))?$")


def expand(path: str, lang: str, form_step: int) -> tuple[dict[str, int], list[str]]:
    """The overlay [path] expands to, and its header comment (the lemma file's, before the first word)."""
    words: dict[str, int] = {}
    excluded: set[str] = set()
    header: list[str] = []
    seen_word = False
    with open(path, encoding="utf-8") as lemmas:
        for number, line in enumerate(lemmas, 1):
            if line.startswith("#") and not seen_word:
                header.append(line.rstrip("\n"))
            text = line.split("#", 1)[0].strip()
            if not text:
                continue
            seen_word = True
            if text.startswith("!"):
                excluded.add(text[1:].strip())
                continue
            match = LEMMA_LINE.match(text)
            if not match or int(match.group(2)) > 255:
                sys.exit(f"{path}:{number}: expected 'lemma<TAB>tier[<TAB>class]', got {line.rstrip()!r}")
            lemma, tier, paradigm = match.group(1), int(match.group(2)), (match.group(3) or "").strip()
            try:
                forms = forms_of(lemma, paradigm, lang)
            except ValueError as error:
                sys.exit(f"{path}:{number}: {lemma}: {error}")
            for index, form in enumerate(forms):
                frequency = tier if index == 0 else max(tier - form_step, 0)
                words[form] = max(words.get(form, 0), frequency)
    unused = excluded - words.keys()
    if unused:
        sys.exit(f"{path}: excluded forms that no lemma generates: {', '.join(sorted(unused))}")
    return {w: f for w, f in words.items() if w not in excluded}, header


def one_edit(word: str, alphabet: set[str]) -> set[str]:
    out = set()
    for i in range(len(word)):
        out.add(word[:i] + word[i + 1:])
        if i + 1 < len(word):
            out.add(word[:i] + word[i + 1] + word[i] + word[i + 2:])
        for c in alphabet:
            out.add(word[:i] + c + word[i + 1:])
    for i in range(len(word) + 1):
        for c in alphabet:
            out.add(word[:i] + c + word[i:])
    out.discard(word)
    return out


def review(words: dict[str, int], asset: str) -> bool:
    """Prints the forms to look at; false when one is an е spelling of a known ё word."""
    known: dict[str, int] = {}
    with open(asset, encoding="utf-8") as source:
        for line in source:
            word, _, frequency = line.rstrip("\n").partition("\t")
            known[word] = int(frequency)
    alphabet = {c for word in known for c in word.lower() if c.isalpha()}
    clean = True
    for word, frequency in words.items():
        yo = [word[:i] + "ё" + word[i + 1:] for i, c in enumerate(word) if c == "е"]
        # Not "unless the list has it": once the list is rebuilt it has every form, тещу too.
        if any(spelling in known and spelling not in words for spelling in yo):
            print(f"error: {word} is an е spelling of a ё word; leave it out with !{word}", file=sys.stderr)
            clean = False
        if len(word) > 4:
            continue
        beaten = [n for n in one_edit(word, alphabet) if n in known and n not in words and COMMON <= known[n] < frequency]
        if beaten:
            top = sorted(beaten, key=lambda n: -known[n])[:4]
            print(f"review: {word} {frequency} outranks " + ", ".join(f"{n} {known[n]}" for n in top), file=sys.stderr)
    return clean


COMMON = 100


SELF_TEST = {
    ("ru", "коммит", "noun-m"): "коммит коммита коммиту коммитом коммите коммиты коммитов коммитам коммитами коммитах",
    ("ru", "пуш", "noun-m"): "пуш пуша пушу пушем пуше пуши пушей пушам пушами пушах",
    ("ru", "лог", "noun-m"): "лог лога логу логом логе логи логов логам логами логах",
    ("ru", "деплой", "noun-m-j"): "деплой деплоя деплою деплоем деплое деплои деплоев деплоям деплоями деплоях",
    ("ru", "фича", "noun-f"): "фича фичи фиче фичу фичей фичею фичам фичами фичах фич",
    ("ru", "таска", "noun-f"): "таска таски таске таску таской таскою таскам тасками тасках",
    ("ru", "кринжовый", "adj"): "кринжовый кринжовая кринжовое кринжовые кринжового кринжовой кринжовому кринжовую кринжовым кринжовыми кринжовых кринжовом",
    ("ru", "закоммитить", "verb-i"): "закоммитить закоммитил закоммитила закоммитило закоммитили закоммичу закоммитишь закоммитит закоммитим закоммитите закоммитят закоммить закоммитьте",
    ("ru", "пофиксить", "verb-i"): "пофиксить пофиксил пофиксила пофиксило пофиксили пофикшу пофиксишь пофиксит пофиксим пофиксите пофиксят пофикси",
    ("ru", "запушить", "verb-i"): "запушить запушил запушила запушило запушили запушу запушишь запушит запушим запушите запушат запушь запушьте",
    ("ru", "задеплоить", "verb-i"): "задеплоить задеплоил задеплоила задеплоило задеплоили задеплою задеплоишь задеплоит задеплоим задеплоите задеплоят задеплой задеплойте",
    ("ru", "залогиниться", "verb-i"): "залогиниться залогинился залогинилась залогинилось залогинились залогинюсь залогинишься залогинится залогинимся залогинитесь залогинятся залогинься залогиньтесь",
    ("ru", "крутой", "adj"): "крутой крутая крутое крутые крутого крутому крутую крутым крутыми крутых крутом",
    ("ru", "лайкать", "verb-a"): "лайкать лайкал лайкала лайкало лайкали лайкаю лайкаешь лайкает лайкаем лайкаете лайкают лайкай лайкайте",
    ("ru", "лайкнуть", "verb-nu"): "лайкнуть лайкнул лайкнула лайкнуло лайкнули лайкну лайкнешь лайкнет лайкнем лайкнете лайкнут лайкни лайкните",
    ("ru", "тестировать", "verb-ova"): "тестировать тестировал тестировала тестировало тестировали тестирую тестируешь тестирует тестируем тестируете тестируют тестируй тестируйте",
    ("uk", "коміт", "noun-m"): "коміт коміта коміту комітові комітом коміти комітів комітам комітами комітах коміті",
    ("uk", "мердж", "noun-m"): "мердж мерджа мерджу мерджеві мерджем мерджі мерджів мерджам мерджами мерджах",
    ("uk", "лог", "noun-m"): "лог лога логу логові логом логи логів логам логами логах",
    ("uk", "деплой", "noun-m-j"): "деплой деплоя деплою деплоєві деплоєм деплої деплоїв деплоям деплоями деплоях",
    ("uk", "фіча", "noun-f"): "фіча фічі фічу фічею фічам фічами фічах фіч",
    ("uk", "таска", "noun-f"): "таска таски таску таскою таскам тасками тасках тасці",
    ("uk", "крінжовий", "adj"): "крінжовий крінжова крінжове крінжові крінжового крінжовій крінжовому крінжову крінжовим крінжовими крінжових крінжовім",
    ("uk", "закомітити", "verb-i"): "закомітити закомітив закомітила закомітило закомітили закомічу закомітиш закомітить закомітимо закомітите закомітять закоміть закомітьте",
    ("uk", "пофіксити", "verb-i"): "пофіксити пофіксив пофіксила пофіксило пофіксили пофікшу пофіксиш пофіксить пофіксимо пофіксите пофіксять пофікси пофіксіть",
    ("uk", "задеплоїти", "verb-i"): "задеплоїти задеплоїв задеплоїла задеплоїло задеплоїли задеплою задеплоїш задеплоїть задеплоїмо задеплоїте задеплоять задеплой задеплойте",
    ("uk", "залогінитися", "verb-i"): "залогінитися залогінився залогінилася залогінилося залогінилися залогінюся залогінишся залогіниться залогінимося залогінитеся залогіняться залогінься залогіньтеся",
    ("uk", "лайкати", "verb-a"): "лайкати лайкав лайкала лайкало лайкали лайкаю лайкаєш лайкає лайкаємо лайкаєте лайкають лайкай лайкайте",
    ("uk", "лайкнути", "verb-nu"): "лайкнути лайкнув лайкнула лайкнуло лайкнули лайкну лайкнеш лайкне лайкнемо лайкнете лайкнуть лайкни лайкніть",
    ("uk", "тестувати", "verb-ova"): "тестувати тестував тестувала тестувало тестували тестую тестуєш тестує тестуємо тестуєте тестують тестуй тестуйте",
}


def self_test() -> int:
    failed = 0
    for (lang, lemma, paradigm), expected in SELF_TEST.items():
        got = " ".join(forms_of(lemma, paradigm, lang))
        if got != expected:
            failed += 1
            print(f"{lang} {lemma} {paradigm}:\n  expected {expected}\n  got      {got}", file=sys.stderr)
    print(f"{len(SELF_TEST) - failed} of {len(SELF_TEST)} paradigms as expected", file=sys.stderr)
    return 1 if failed else 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("lemmas", nargs="?")
    parser.add_argument("target", nargs="?")
    parser.add_argument("--lang", choices=["ru", "uk"])
    parser.add_argument("--form-step", type=int, help="how far below its lemma every other form is ranked")
    parser.add_argument("--asset", help="the list the overlay goes into, to review short forms against")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if not (args.lemmas and args.target and args.lang and args.form_step is not None):
        parser.error("lemmas, target, --lang and --form-step are required")
    words, header = expand(args.lemmas, args.lang, args.form_step)
    if args.asset and not review(words, args.asset):
        return 1
    asset = f"--asset {args.asset} " if args.asset else ""
    command = f"scripts/expand-paradigms.py --lang {args.lang} --form-step {args.form_step} {asset}{args.lemmas} {args.target}"
    with open(args.target, "w", encoding="utf-8") as target:
        target.write(f"# Generated from {args.lemmas} by\n#   {command}\n# Never edit by hand: change the lemma file and run it again.\n#\n")
        for line in header:
            target.write(line + "\n")
        target.write("\n")
        for word, frequency in words.items():
            target.write(f"{word}\t{frequency}\n")
    print(f"{len(words)} forms written to {args.target}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
