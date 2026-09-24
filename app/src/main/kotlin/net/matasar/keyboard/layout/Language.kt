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
    /**
     * Three rows: the top, the home row and the bottom letters row. A non-letter character is a
     * plain text key. The 60% board fills its ANSI slots with these.
     */
    val rows: List<String>,
    /** Long-press alternatives per character; the first is what a plain long press selects. */
    val accents: Map<Char, List<String>>,
    /**
     * The rows of the phone letters page, as the iPhone lays this language out. They differ from
     * [rows] where the iPhone's shape does not fit the ANSI slots (Ukrainian's `'` and `ґ`) or
     * leaves a letter to a long press (Russian's `ъ`).
     */
    val phoneRows: List<String> = rows,
) {
    /** The widest phone row decides the unit: rows 1 and 2 as they are, row 3 with shift and backspace beside it. */
    val units: Float
        get() = maxOf(phoneRows[0].length, phoneRows[1].length, phoneRows[2].length + MIN_EDGE_KEYS).toFloat()

    /**
     * The letters layer for this language, with a globe key when more than one language is
     * enabled, and the digits across the top when [numberRow] asks for them.
     */
    fun lettersLayer(withGlobe: Boolean, numberRow: Boolean = false): Layer {
        val units = units
        // Shift and backspace take what the letters leave, up to a key and a half each, as on the
        // iPhone; anything beyond that is a gap between them and the letters.
        val side = (units - phoneRows[2].length) / 2f
        val edge = minOf(side, MAX_EDGE_KEY)
        return Layer(
            id = LayerId.LETTERS,
            units = units,
            rows = listOfNotNull(
                if (numberRow) numberRow(units) else null,
                row(*phoneKeys(0), leading = (units - phoneRows[0].length) / 2f, trailing = (units - phoneRows[0].length) / 2f),
                row(*phoneKeys(1), leading = (units - phoneRows[1].length) / 2f, trailing = (units - phoneRows[1].length) / 2f),
                Row(listOf(shiftKey(edge), *phoneKeys(2), backspaceKey(edge)), innerGapUnits = side - edge),
                bottomRow(LayerId.SYMBOLS, "123", units, nativeName, withGlobe),
            ),
        )
    }

    /** The keys of phone row [index]; each letter carries the ANSI slot it sits in. */
    private fun phoneKeys(index: Int): Array<Key> =
        phoneRows[index].mapIndexed { i, c -> keyFor(c, AnsiSlots.rows[index].getOrNull(i)) }.toTypedArray()

    /** One key of a letters row: a letter with its accents and slot, or a plain text key with its alternatives. */
    fun keyFor(c: Char, slot: Char?): Key =
        if (c.isLetter()) {
            Key(label = c.toString(), action = KeyAction.Letter(c.toString(), c.uppercase()), longPress = accents[c].orEmpty(), slot = slot)
        } else {
            Key(label = c.toString(), action = KeyAction.Text(c.toString()), longPress = accents[c].orEmpty())
        }

    companion object {
        /** Shift and backspace take at least one unit each, the width of a letter. */
        private const val MIN_EDGE_KEYS = 2

        /** And at most a key and a half. */
        private const val MAX_EDGE_KEY = 1.5f
    }
}
