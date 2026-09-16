package net.matasar.keyboard.nlp

import android.content.Context

/**
 * The words the glide classifier can produce, with AOSP-style frequencies (0–255). Loaded once
 * from `assets/dictionaries/<language>.txt`, one `word<TAB>frequency` per line.
 */
class WordList private constructor(private val frequencies: Map<String, Int>) {

    val words: List<String> = frequencies.keys.toList()

    val size: Int get() = frequencies.size

    /** The word's frequency, or 0 when it is not in the list. */
    fun frequency(word: String): Int = frequencies[word] ?: 0

    companion object {
        val EMPTY = WordList(emptyMap())

        /** Parses `word<TAB>frequency` lines; blank and malformed lines and non-letter words are dropped. */
        fun parse(lines: Sequence<String>): WordList {
            val map = LinkedHashMap<String, Int>()
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val word = line.substring(0, tab)
                val frequency = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                if (word.any { !it.isLetter() }) continue
                map[word] = frequency.coerceIn(0, 255)
            }
            return WordList(map)
        }

        fun of(vararg entries: Pair<String, Int>): WordList = WordList(entries.toMap())

        /** Loads the bundled list for a language tag such as `en_US`; empty when there is none. */
        fun load(context: Context, language: String): WordList = runCatching {
            context.assets.open("dictionaries/$language.txt").bufferedReader().useLines { parse(it) }
        }.getOrDefault(EMPTY)
    }
}
