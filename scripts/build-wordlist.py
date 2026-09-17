#!/usr/bin/env python3
"""Turns an AOSP LatinIME `*_wordlist.combined(.gz)` file into the keyboard's dictionary asset.

Keeps letter-only words (any script, so Cyrillic, accented Latin and German capitalised nouns
pass) at or above a frequency floor, drops words flagged offensive, and writes
`word<TAB>frequency` sorted by frequency, highest first, capped at --max words.

    scripts/build-wordlist.py en_US_wordlist.combined.gz app/src/main/assets/dictionaries/en_US.txt --floor 60 --max 80000
"""
import argparse
import gzip
import re
import sys

LINE = re.compile(r" word=([^,]*),f=(-?\d+)(?:,flags=([^,]*))?")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("target")
    parser.add_argument("--floor", type=int, default=60, help="minimum AOSP frequency (0-255)")
    parser.add_argument("--max", type=int, default=80000, help="keep at most this many words, highest frequency first")
    args = parser.parse_args()

    opener = gzip.open if args.source.endswith(".gz") else open
    words: dict[str, int] = {}
    with opener(args.source, "rt", encoding="utf-8") as source:
        for line in source:
            match = LINE.match(line)
            if not match:
                continue
            word, frequency, flags = match.group(1), int(match.group(2)), match.group(3) or ""
            if not word.isalpha() or frequency < args.floor or "offensive" in flags:
                continue
            words[word] = max(words.get(word, 0), frequency)

    kept = sorted(words.items(), key=lambda item: (-item[1], item[0]))[: args.max]
    with open(args.target, "w", encoding="utf-8") as target:
        for word, frequency in kept:
            target.write(f"{word}\t{frequency}\n")
    print(f"{len(kept)} of {len(words)} words written to {args.target}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
