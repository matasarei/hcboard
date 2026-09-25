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
 *
 * Apostrophes: the lists store `'`, and a word is looked up with whichever of the three the user
 * typed written as `'`; what is shown carries the user's own apostrophe back ([Apostrophes.restyle]).
 * A word typed without its apostrophe (whats, dont, розвязок) is restored, first and whatever its
 * length or frequency, as a ё is; a real word with an apostrophe twin (its, cant) is kept and the
 * twin offered next to it. A word ending in an apostrophe is only completed: that may be a quote.
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
        val stored = Apostrophes.normalize(typed)
        val key = stored.lowercase()
        val known = list.contains(key) || list.contains(stored)
        // A trailing apostrophe may be a closing quote ('hello'): complete it, never correct it.
        val quoteAtEnd = Apostrophes.isApostrophe(typed.last())
        val corrections = if (known || quoteAtEnd) emptyList() else corrections(key)
        // A ё or apostrophe restoration is the typed word spelled right, not a guess, so neither gate applies.
        val correction = corrections.firstOrNull()?.takeIf {
            isYoRestoration(key, it) || isApostropheRestoration(key, it) || it.lowercase() == key ||
                typed.length >= MIN_CORRECTED_LENGTH && list.frequency(it) >= AUTOCORRECT_MIN_FREQUENCY
        }
        // A real word's apostrophe twin (its and it's) is offered right after it, never applied.
        val twins = if (known) apostropheTwins(key) else emptyList()
        val completions = list.completions(key, MAX_WORDS) +
            if (key != stored) list.completions(stored, MAX_WORDS) else emptyList()
        val rest = (completions + corrections).distinct().sortedByDescending { list.frequency(it) }
        val ordered = listOfNotNull(correction) + twins + rest.filter { it != correction && it !in twins }
        val words = (listOf(typed) + ordered.map { shown(it, typed) }).distinct().take(MAX_WORDS)
        if (words.size == 1 && correction == null) return null
        return WordCandidates(typed, words, correction?.let { shown(it, typed) })
    }

    /** A list [word] as the strip shows it for [typed]: in its case, with its apostrophe. */
    private fun shown(word: String, typed: String): String = Apostrophes.restyle(cased(word, typed), typed)

    /** The list's words that are [key] with one apostrophe put between two of its letters, most frequent first. */
    private fun apostropheTwins(key: String): List<String> =
        withApostrophe(key).sortedByDescending { list.frequency(it) }

    /**
     * [key] with an apostrophe put between two of its letters, as the list has it: as typed, or
     * capitalised where the list only has it so (I'm, I'll from im, ill).
     */
    private fun withApostrophe(key: String): List<String> =
        (1 until key.length).mapNotNull { at ->
            val candidate = key.substring(0, at) + Apostrophes.STORED + key.substring(at)
            val capitalised = candidate.replaceFirstChar { it.uppercase() }
            when {
                !Apostrophes.isWord(candidate) -> null
                list.contains(candidate) -> candidate
                list.contains(capitalised) -> capitalised
                else -> null
            }
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
        found += withApostrophe(key)
        capitalisedWithApostrophe(key)?.let { found += it }
        return found.sortedWith(
            compareByDescending<String> { isYoRestoration(key, it) }
                .thenByDescending { isApostropheRestoration(key, it) || it.lowercase() == key }
                .thenByDescending { list.frequency(it) }
                .thenBy { it },
        )
    }

    /** Whether [candidate] is [key] with an е written as ё (идет and идёт), the usual Russian shortcut. */
    private fun isYoRestoration(key: String, candidate: String): Boolean =
        candidate.length == key.length && candidate != key &&
            key.indices.all { key[it] == candidate[it] || key[it] == 'е' && candidate[it] == 'ё' }

    /**
     * [key], which has an apostrophe, as the list has it capitalised (i'm and I'm, i'll and I'll);
     * null otherwise. Only a word with an apostrophe: a name typed in lowercase stays as typed.
     */
    private fun capitalisedWithApostrophe(key: String): String? =
        key.takeIf { Apostrophes.STORED in it }?.replaceFirstChar { it.uppercase() }?.takeIf { it != key && list.contains(it) }

    /** Whether [candidate] is [key] with one apostrophe put in (whats and what's, розвязок and розв'язок). */
    private fun isApostropheRestoration(key: String, candidate: String): Boolean =
        candidate.length == key.length + 1 &&
            candidate.indices.any { candidate[it] == Apostrophes.STORED && candidate.removeRange(it, it + 1).lowercase() == key }

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
