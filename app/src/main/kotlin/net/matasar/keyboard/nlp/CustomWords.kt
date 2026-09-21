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

    /**
     * [input] trimmed, or null when it is not a word the lists could hold: empty, longer than
     * [MAX_LENGTH], or with anything but letters, as [WordList.parse] drops. Its case is kept.
     */
    fun normalize(input: String): String? {
        val word = input.trim()
        if (word.isEmpty() || word.length > MAX_LENGTH || word.any { !it.isLetter() }) return null
        return word
    }
}
