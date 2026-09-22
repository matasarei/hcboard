package net.matasar.keyboard.layout

/**
 * A language the keyboard can type: its tag (also the word-list asset name), its name in its own
 * script for the space bar, and its letters layer as three rows of characters plus the
 * long-press accents. Everything about a language is data; adding one is adding an entry.
 */
data class Language(
    val tag: String,
    val nativeName: String,
    val englishName: String,
    /**
     * The fixed id of this language's subtype in `res/xml/method.xml`, which is also its hash
     * code: Android's keyboard list names the enabled subtypes, and the keyboard enables its own.
     */
    val subtypeId: Int,
    /** Three rows: the top, the home row and the bottom letters row. A non-letter character is a plain text key. */
    val rows: List<String>,
    /** Long-press alternatives per letter; the first is what a plain long press selects. */
    val accents: Map<Char, List<String>>,
) {
    /** The widest row decides the unit: rows 1 and 2 as they are, row 3 with shift and backspace beside it. */
    val units: Float
        get() = maxOf(rows[0].length, rows[1].length, rows[2].length + MIN_EDGE_KEYS).toFloat()

    /** The letters layer for this language, with a globe key when more than one language is enabled. */
    fun lettersLayer(withGlobe: Boolean): Layer {
        val units = units
        val edge = (units - rows[2].length) / 2f
        return Layer(
            id = LayerId.LETTERS,
            units = units,
            rows = listOf(
                row(*keysFor(0), leading = (units - rows[0].length) / 2f, trailing = (units - rows[0].length) / 2f),
                row(*keysFor(1), leading = (units - rows[1].length) / 2f, trailing = (units - rows[1].length) / 2f),
                row(shiftKey(edge), *keysFor(2), backspaceKey(edge)),
                bottomRow(LayerId.SYMBOLS, "?123", units, nativeName, withGlobe),
            ),
        )
    }

    /** The keys of row [index]; each letter carries the ANSI slot it sits in. */
    fun keysFor(index: Int): Array<Key> = rows[index].mapIndexed { i, c -> keyFor(c, AnsiSlots.rows[index].getOrNull(i)) }.toTypedArray()

    /** One key of a letters row: a letter with its accents and slot, or a plain text key. */
    fun keyFor(c: Char, slot: Char?): Key =
        if (c.isLetter()) {
            Key(label = c.toString(), action = KeyAction.Letter(c.toString(), c.uppercase()), longPress = accents[c].orEmpty(), slot = slot)
        } else {
            Key(label = c.toString(), action = KeyAction.Text(c.toString()))
        }

    companion object {
        /** Shift and backspace take at least 1.5 units each. */
        private const val MIN_EDGE_KEYS = 3
    }
}
