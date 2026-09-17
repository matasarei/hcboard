package net.matasar.keyboard.nlp

import android.content.Context

/**
 * The words the glide classifier and the candidate engine can produce, with AOSP-style
 * frequencies (0–255). Loaded once from `assets/dictionaries/<language>.txt`, one
 * `word<TAB>frequency` per line.
 */
class WordList private constructor(private val frequencies: Map<String, Int>) {

    val words: List<String> = frequencies.keys.toList()

    val size: Int get() = frequencies.size

    /** The words in code-point order, built on first use; prefix lookups binary-search it. */
    private val sorted: Array<String> by lazy { words.sorted().toTypedArray() }

    /** The word's frequency, or 0 when it is not in the list. */
    fun frequency(word: String): Int = frequencies[word] ?: 0

    fun contains(word: String): Boolean = word in frequencies

    /**
     * The most frequent words that start with [prefix] and are longer than it, best first, at
     * most [max]. Nothing for an empty prefix: every word would match.
     */
    fun completions(prefix: String, max: Int): List<String> {
        if (prefix.isEmpty() || max <= 0) return emptyList()
        var index = lowerBound(prefix)
        val best = ArrayList<String>(max)
        while (index < sorted.size && sorted[index].startsWith(prefix)) {
            val word = sorted[index++]
            if (word.length == prefix.length) continue
            val frequency = frequency(word)
            // Insert in frequency order; the list is tiny, so a scan beats a heap.
            val at = best.indexOfFirst { frequency(it) < frequency }.let { if (it < 0) best.size else it }
            if (at < max) {
                best.add(at, word)
                if (best.size > max) best.removeAt(best.size - 1)
            }
        }
        return best
    }

    /** The index of the first sorted word not less than [prefix]. */
    private fun lowerBound(prefix: String): Int {
        var low = 0
        var high = sorted.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sorted[mid] < prefix) low = mid + 1 else high = mid
        }
        return low
    }

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
