package net.matasar.keyboard.nlp

/**
 * The user's own words: language tag → word → frequency. A frequency above 0 adds the word to
 * that language's list or sets its frequency; [BLOCKED] takes it out (see [WordList.withOverrides]).
 */
typealias CustomWords = Map<String, Map<String, Int>>

object CustomWord {

    /** An added word's frequency: the everyday overlays' top tier, well above autocorrect's floor. */
    const val ADDED = 230

    const val BLOCKED = 0

    const val MAX_LENGTH = 48

    /** The Custom words screen offers a search once a language has more words than this. */
    const val SEARCH_FROM = 15

    /**
     * [input] trimmed, or null when it is not a word the lists could hold: empty, longer than
     * [MAX_LENGTH], or anything but letters with apostrophes between them, as [WordList.parse]
     * drops. Its case is kept; its apostrophe is stored as `'`, as the lists store it.
     */
    fun normalize(input: String): String? {
        val word = Apostrophes.normalize(input.trim())
        if (word.length > MAX_LENGTH || !Apostrophes.isWord(word)) return null
        return word
    }

    /**
     * The entries whose word contains [query], ignoring case and the apostrophe's style (a search
     * for розвʼязок finds розв'язок), in their order. A blank query keeps them all.
     */
    fun <V> matching(entries: List<Map.Entry<String, V>>, query: String): List<Map.Entry<String, V>> {
        val needle = Apostrophes.normalize(query.trim()).lowercase()
        if (needle.isEmpty()) return entries
        return entries.filter { it.key.lowercase().contains(needle) }
    }
}
