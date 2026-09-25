package net.matasar.keyboard.nlp

/**
 * Words with apostrophes inside them: don't, what's, розв'язок, c'est, dell'anno. The typewriter
 * apostrophe, the typographic one and Ukrainian's modifier letter are one character to the lists,
 * which always store the typewriter `'`; what the user typed is given back when a word is shown.
 */
object Apostrophes {

    /** `'`, `’` (the typographic apostrophe) and `ʼ` (the Ukrainian apostrophe sign). */
    val ALL: Set<Char> = setOf('\'', '’', 'ʼ')

    /** The apostrophe the lists store, and the one the keyboard types when it adds one itself. */
    const val STORED = '\''

    fun isApostrophe(c: Char): Boolean = c in ALL

    /** A character that can be part of a word: a letter, or an apostrophe between letters. */
    fun isWordChar(c: Char): Boolean = c.isLetter() || c in ALL

    /** [text] with every apostrophe written as [STORED]. */
    fun normalize(text: String): String =
        if (text.none { it in ALL && it != STORED }) text else buildString(text.length) {
            for (c in text) append(if (c in ALL) STORED else c)
        }

    /** Whether [text] is a word the lists can hold: letters, with apostrophes only between them. */
    fun isWord(text: String): Boolean =
        text.isNotEmpty() && text.first().isLetter() && text.last().isLetter() &&
            text.all { isWordChar(it) } &&
            text.zipWithNext().none { (a, b) -> a in ALL && b in ALL }

    /** A list [word], stored with [STORED], shown with the apostrophe the user put in [typed], if any. */
    fun restyle(word: String, typed: String): String {
        val mark = typed.firstOrNull { it in ALL } ?: return word
        return if (mark == STORED) word else word.replace(STORED, mark)
    }
}
