#!/usr/bin/env python3
"""Turns an AOSP LatinIME `*_wordlist.combined(.gz)` file into the keyboard's dictionary asset.

Keeps lowercase letter-only words at or above a frequency floor, drops words flagged
offensive, and writes `word<TAB>frequency` sorted by frequency, highest first.

    scripts/build-wordlist.py en_US_wordlist.combined.gz app/src/main/assets/dictionaries/en_US.txt --floor 60
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
    args = parser.parse_args()

    opener = gzip.open if args.source.endswith(".gz") else open
    words: dict[str, int] = {}
    with opener(args.source, "rt", encoding="utf-8") as source:
        for line in source:
            match = LINE.match(line)
            if not match:
                continue
            word, frequency, flags = match.group(1), int(match.group(2)), match.group(3) or ""
            if not re.fullmatch(r"[a-z]+", word) or frequency < args.floor or "offensive" in flags:
                continue
            words[word] = max(words.get(word, 0), frequency)

    with open(args.target, "w", encoding="utf-8") as target:
        for word, frequency in sorted(words.items(), key=lambda item: (-item[1], item[0])):
            target.write(f"{word}\t{frequency}\n")
    print(f"{len(words)} words written to {args.target}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
