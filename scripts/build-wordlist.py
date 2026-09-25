#!/usr/bin/env python3
"""Builds a keyboard dictionary asset from a word list, with optional curated boosts.

The source is either an AOSP LatinIME `*_wordlist.combined(.gz)` file or an asset already in
the keyboard's own `word<TAB>frequency` format (so a shipped list can be rebuilt without its
original corpus). Keeps words of letters (any script, so Cyrillic, accented Latin and German
capitalised nouns pass) with apostrophes only between letters (don't, розв'язок, c'est; the
typographic ’ and the Ukrainian ʼ are stored as '), at or above a frequency floor, drops words
flagged offensive, merges every `--boost` file (`word<TAB>frequency`, `#` comments) by taking the
higher frequency, and writes `word<TAB>frequency` sorted by frequency, highest first. --max caps
the words without an apostrophe; words with one come on top, so adding them never pushes out a
word a list already had.

    scripts/build-wordlist.py en_US_wordlist.combined.gz app/src/main/assets/dictionaries/en_US.txt --floor 60 --max 80000
    scripts/build-wordlist.py app/src/main/assets/dictionaries/uk.txt app/src/main/assets/dictionaries/uk.txt \
        --boost scripts/wordlists/uk-everyday.tsv

Running the second form twice changes nothing: a boost only ever raises or adds a word.
"""
import argparse
import gzip
import re
import sys
from typing import Iterator, TextIO

AOSP_LINE = re.compile(r" word=([^,]*),f=(-?\d+)(?:,flags=([^,]*))?")
ASSET_LINE = re.compile(r"([^\t#]+)\t(\d+)\s*$")
APOSTROPHES = "'’ʼ"


def normalize(word: str) -> str:
    """The word with every apostrophe written as the typewriter ', as the keyboard stores it."""
    return word.replace("’", "'").replace("ʼ", "'")


def is_word(word: str) -> bool:
    """Letters, with apostrophes only between them: what the keyboard's Apostrophes.isWord accepts."""
    return (
        word != ""
        and word[0].isalpha()
        and word[-1].isalpha()
        and all(c.isalpha() or c == "'" for c in word)
        and "''" not in word
    )


def read_source(source: TextIO) -> Iterator[tuple[str, int, str]]:
    """Yields (word, frequency, flags) from either supported format; other lines are skipped."""
    for line in source:
        aosp = AOSP_LINE.match(line)
        if aosp:
            yield aosp.group(1), int(aosp.group(2)), aosp.group(3) or ""
            continue
        asset = ASSET_LINE.match(line)
        if asset:
            yield asset.group(1), int(asset.group(2)), ""


def read_boost(path: str) -> dict[str, int]:
    """Reads a curated `word<TAB>frequency` overlay; blank lines and `#` comments are ignored."""
    boosts: dict[str, int] = {}
    with open(path, encoding="utf-8") as overlay:
        for number, line in enumerate(overlay, 1):
            text = line.split("#", 1)[0].strip()
            if not text:
                continue
            match = ASSET_LINE.match(text)
            word = normalize(match.group(1)) if match else ""
            if not match or not is_word(word) or int(match.group(2)) > 255:
                sys.exit(f"{path}:{number}: expected 'word<TAB>frequency' with frequency 0-255, got {line.rstrip()!r}")
            boosts[word] = int(match.group(2))
    return boosts


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("target")
    parser.add_argument("--floor", type=int, default=60, help="minimum AOSP frequency (0-255)")
    parser.add_argument("--max", type=int, default=80000, help="keep at most this many words, highest frequency first")
    parser.add_argument("--boost", action="append", default=[], metavar="TSV", help="curated overlay merged by max; repeatable")
    args = parser.parse_args()

    opener = gzip.open if args.source.endswith(".gz") else open
    words: dict[str, int] = {}
    with opener(args.source, "rt", encoding="utf-8") as source:
        for word, frequency, flags in read_source(source):
            word = normalize(word)
            if not is_word(word) or frequency < args.floor or "offensive" in flags:
                continue
            words[word] = max(words.get(word, 0), frequency)

    added = raised = 0
    for path in args.boost:
        for word, frequency in read_boost(path).items():
            current = words.get(word)
            if current is None:
                added += 1
            elif frequency > current:
                raised += 1
            words[word] = max(current or 0, frequency)

    by_frequency = sorted(words.items(), key=lambda item: (-item[1], item[0]))
    plain = [item for item in by_frequency if "'" not in item[0]][: args.max]
    elided = [item for item in by_frequency if "'" in item[0]]
    kept = sorted(plain + elided, key=lambda item: (-item[1], item[0]))
    with open(args.target, "w", encoding="utf-8") as target:
        for word, frequency in kept:
            target.write(f"{word}\t{frequency}\n")
    print(f"{len(kept)} of {len(words)} words written to {args.target}", file=sys.stderr)
    if args.boost:
        print(f"boost: {added} words added, {raised} raised", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
