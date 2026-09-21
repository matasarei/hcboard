package net.matasar.keyboard.nlp

/** What the strip shows for the word under the cursor. */
data class WordCandidates(
    /** The word as typed; always the first of [words]. */
    val typed: String,
    /** At most [Candidates.MAX_WORDS] words: [typed], then [correction] when there is one, then the rest by frequency. */
    val words: List<String>,
    /** The word a separator will put in place of [typed], or null when nothing will be applied. */
    val correction: String? = null,
)

/**
 * Completions and corrections for a word being typed, from one language's [WordList]: words
 * that continue the typed prefix, and words one edit away from it when it is not in the list.
 * Both are ranked by frequency. The typed word's case (capitalised, all caps) is applied to
 * every candidate; a list word that is capitalised itself (a German noun) keeps its case.
 */
class Candidates(private val list: WordList) {

    /** Every letter the list uses, lowercase; substitutions and insertions try each of them. */
    private val alphabet: CharArray by lazy {
        val letters = HashSet<Char>()
        for (word in list.words) for (c in word) letters += c.lowercaseChar()
        letters.sorted().toCharArray()
    }

    /** Builds the lazy indexes now, so the first key does not pay for them on the main thread. */
    fun warmUp() {
        alphabet
        list.completions("a", 1)
    }

    /** Candidates for [typed], or null when it is empty or nothing but the word itself is known. */
    fun forWord(typed: String): WordCandidates? {
        if (typed.isEmpty()) return null
        val key = typed.lowercase()
        val known = list.contains(key) || list.contains(typed)
        val corrections = if (known) emptyList() else corrections(key)
        // A ё restoration is the typed word spelled right, not a guess, so neither gate applies.
        val correction = corrections.firstOrNull()?.takeIf {
            isYoRestoration(key, it) ||
                typed.length >= MIN_CORRECTED_LENGTH && list.frequency(it) >= AUTOCORRECT_MIN_FREQUENCY
        }
        val completions = list.completions(key, MAX_WORDS) +
            if (key != typed) list.completions(typed, MAX_WORDS) else emptyList()
        val rest = (completions + corrections).distinct().sortedByDescending { list.frequency(it) }
        val ordered = listOfNotNull(correction) + rest.filter { it != correction }
        val words = (listOf(typed) + ordered.map { cased(it, typed) }).distinct().take(MAX_WORDS)
        if (words.size == 1 && correction == null) return null
        return WordCandidates(typed, words, correction?.let { cased(it, typed) })
    }

    /** The list's words one edit away from [key]: a ё restoration of it first, then most frequent first. */
    private fun corrections(key: String): List<String> {
        val found = HashSet<String>()
        fun consider(candidate: String) {
            if (candidate != key && list.contains(candidate)) found += candidate
        }
        for (i in key.indices) {
            consider(key.removeRange(i, i + 1))
            if (i + 1 < key.length && key[i] != key[i + 1]) {
                consider(key.substring(0, i) + key[i + 1] + key[i] + key.substring(i + 2))
            }
            for (c in alphabet) if (c != key[i]) consider(key.substring(0, i) + c + key.substring(i + 1))
        }
        for (i in 0..key.length) for (c in alphabet) consider(key.substring(0, i) + c + key.substring(i))
        return found.sortedWith(
            compareByDescending<String> { isYoRestoration(key, it) }
                .thenByDescending { list.frequency(it) }
                .thenBy { it },
        )
    }

    /** Whether [candidate] is [key] with an е written as ё (идет and идёт), the usual Russian shortcut. */
    private fun isYoRestoration(key: String, candidate: String): Boolean =
        candidate.length == key.length && candidate != key &&
            key.indices.all { key[it] == candidate[it] || key[it] == 'е' && candidate[it] == 'ё' }

    /** [word] in the case pattern of [typed]: all caps, capitalised, or as the list has it. */
    private fun cased(word: String, typed: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() } -> word.uppercase()
        typed.first().isUpperCase() -> word.replaceFirstChar { it.uppercase() }
        else -> word
    }

    companion object {
        const val MAX_WORDS = 3

        /** A typed word shorter than this is never replaced on its own: two letters are too little evidence. */
        const val MIN_CORRECTED_LENGTH = 3

        /** A correction rarer than this is offered to tap but never applied by a separator. */
        const val AUTOCORRECT_MIN_FREQUENCY = 80
    }
}
