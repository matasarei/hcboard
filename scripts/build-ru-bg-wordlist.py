#!/usr/bin/env python3
"""Builds a combined Russian and Bulgarian dictionary asset with collision protection.

Preserves 100% of Russian words at their native frequencies (79-192).
For words unique to Bulgarian:
- If a Bulgarian word is 1-edit distance away from ANY Russian word, its frequency
  is capped at 75 (below AUTOCORRECT_MIN_FREQUENCY = 80). It remains a known word
  (isKnown == true, never autocorrected away when intentionally typed), but will
  never hijack a typo meant for a Russian word (e.g. 'савет' -> 'совет', not 'съвет').
- If a Bulgarian word has zero Russian 1-edit neighbors (e.g. 'кметство', 'български'),
  its frequency is scaled into the active range (80-140) for full autocomplete,
  glide typing, and independent typo correction.

Usage:
    scripts/build-ru-bg-wordlist.py app/src/main/assets/dictionaries/ru.txt \
        app/src/main/assets/dictionaries/bg.txt \
        app/src/main/assets/dictionaries/ru_bg.txt
"""
import argparse
import sys
from typing import Optional

CYRILLIC_ALPHABET = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"


def load_wordlist(path: str) -> dict[str, int]:
    words = {}
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split("\t")
            if len(parts) == 2 and parts[1].isdigit():
                words[parts[0]] = int(parts[1])
    return words


def find_min_ru_neighbor(word: str, ru_words: dict[str, int]) -> Optional[int]:
    min_freq = 999
    # Deletions
    for i in range(len(word)):
        candidate = word[:i] + word[i + 1:]
        if candidate in ru_words:
            min_freq = min(min_freq, ru_words[candidate])

    # Transpositions (adjacent)
    for i in range(len(word) - 1):
        candidate = word[:i] + word[i + 1] + word[i] + word[i + 2:]
        if candidate in ru_words:
            min_freq = min(min_freq, ru_words[candidate])

    # Substitutions
    for i in range(len(word)):
        for c in CYRILLIC_ALPHABET:
            if c != word[i]:
                candidate = word[:i] + c + word[i + 1:]
                if candidate in ru_words:
                    min_freq = min(min_freq, ru_words[candidate])

    # Insertions
    for i in range(len(word) + 1):
        for c in CYRILLIC_ALPHABET:
            candidate = word[:i] + c + word[i:]
            if candidate in ru_words:
                min_freq = min(min_freq, ru_words[candidate])

    return min_freq if min_freq != 999 else None


def scale_safe_bg_frequency(raw_freq: int) -> int:
    """Scales raw Bulgarian frequency (2..255) into the active Russian range (80..140)."""
    clamped = max(2, min(255, raw_freq))
    return 80 + int((clamped - 2) / 253.0 * 60)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build combined RU+BG dictionary with collision protection.")
    parser.add_argument("ru_source", help="Path to ru.txt")
    parser.add_argument("bg_source", help="Path to bg.txt")
    parser.add_argument("target", help="Path to output ru_bg.txt")
    args = parser.parse_args()

    ru_words = load_wordlist(args.ru_source)
    bg_words = load_wordlist(args.bg_source)

    combined = dict(ru_words)
    collided_count = 0
    safe_count = 0

    for word, raw_freq in bg_words.items():
        if word in combined:
            continue  # Exact homograph: keep Russian frequency

        min_ru = find_min_ru_neighbor(word, ru_words)
        if min_ru is not None:
            collided_count += 1
            # Capped strictly below 80 and below neighbor to prevent stealing Russian typos
            freq = min(75, min_ru - 10)
        else:
            safe_count += 1
            freq = scale_safe_bg_frequency(raw_freq)

        combined[word] = max(1, freq)

    # Sort highest frequency first, then alphabetically
    kept = sorted(combined.items(), key=lambda item: (-item[1], item[0]))

    with open(args.target, "w", encoding="utf-8") as out:
        for word, freq in kept:
            out.write(f"{word}\t{freq}\n")

    print(
        f"Generated {len(kept)} words ({len(ru_words)} RU base, {safe_count} safe BG, {collided_count} collision-capped BG) -> {args.target}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
