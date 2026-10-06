package net.matasar.keyboard.layout

import android.view.KeyEvent

private fun fkey(index: Int) = KeyAction.KeyCode(KeyEvent.KEYCODE_F1 + index)
private fun nav(code: Int) = KeyAction.KeyCode(code)
private fun mod(label: String, key: ModifierKey, width: Float) =
    Key(label, KeyAction.Modifier(key), width, KeyStyle.MODIFIER)
private fun fn(label: String, action: KeyAction, width: Float = 1f, fnLegend: String? = null, fnAction: KeyAction? = null) =
    Key(label, action, width, KeyStyle.FUNCTION, fnLegend = fnLegend, fnAction = fnAction)
private fun shift(width: Float) = Key("Shift", KeyAction.Shift, width, KeyStyle.MODIFIER)

/**
 * Fn meanings by slot, so they stay under the same fingers on every language: arrows on I J K L,
 * Home and End on U O, PgUp and PgDn on H N.
 */
private val FnOnSlots: Map<Char, Pair<String, KeyAction>> = mapOf(
    'i' to ("↑" to nav(KeyEvent.KEYCODE_DPAD_UP)),
    'j' to ("←" to nav(KeyEvent.KEYCODE_DPAD_LEFT)),
    'k' to ("↓" to nav(KeyEvent.KEYCODE_DPAD_DOWN)),
    'l' to ("→" to nav(KeyEvent.KEYCODE_DPAD_RIGHT)),
    'u' to ("Home" to nav(KeyEvent.KEYCODE_MOVE_HOME)),
    'o' to ("End" to nav(KeyEvent.KEYCODE_MOVE_END)),
    'h' to ("PgUp" to nav(KeyEvent.KEYCODE_PAGE_UP)),
    'n' to ("PgDn" to nav(KeyEvent.KEYCODE_PAGE_DOWN)),
)

/** The punctuation slots a long row of letters may take over, with their shifted symbols. */
private val SlotPunctuation: Map<Char, String> = mapOf(
    '[' to "{", ']' to "}", ';' to ":", '\'' to "\"", ',' to "<", '.' to ">", '/' to "?",
)

private fun punctuation(slot: Char) = dual(slot.toString(), SlotPunctuation.getValue(slot))

/**
 * A letters row of the 60% board: the language's letters fill the row's ANSI slots left to right,
 * each carrying its slot and the slot's Fn meaning. A letter that takes a punctuation slot keeps
 * that punctuation as its Fn legend, both symbols (`[{`: Fn+х is `[`, Fn+Shift+х is `{`); the slots left over keep
 * their punctuation keys, so English renders the standard board.
 */
private fun wideRow(language: Language, index: Int): List<Key> {
    val slots = AnsiSlots.rows[index]
    val letters = language.rows[index]
    require(letters.length <= slots.length) { "${language.tag} row $index has ${letters.length} letters for ${slots.length} slots" }
    val onSlots = letters.mapIndexed { i, c ->
        val slot = slots[i]
        val key = language.keyFor(c, slot)
        val fnPair = FnOnSlots[slot]
        val displaced = SlotPunctuation[slot]
        when {
            fnPair != null -> key.copy(fnLegend = fnPair.first, fnAction = fnPair.second)
            // Both symbols of the slot in the legend (`[{`): the shifted one has no other home on
            // a Cyrillic board, and Fn+Shift on this key types it.
            displaced != null -> key.copy(fnLegend = slot.toString() + displaced, fnAction = KeyAction.Text(slot.toString(), displaced))
            else -> key
        }
    }
    return onSlots + slots.drop(letters.length).map(::punctuation)
}

/**
 * The Shift row: `\|` beside the left Shift, where ISO boards keep a key, then the letters, `/`,
 * a narrow `` `~ `` and the right Shift, which takes what is left. The comma and period are on
 * the bottom row by the space bar, so the `,` and `.` slots hold only a letter that sits on them
 * (б and ю, which keep `,<` and `.>` on Fn). Nine letters (Cyrillic) take half a unit from the
 * left Shift.
 */
private fun wideShiftRow(language: Language): Row {
    val letters = language.rows[2]
    require(letters.length <= 9) { "${language.tag} bottom row has ${letters.length} letters; at most 9 fit" }
    val keys = listOf(shift(if (letters.length == 9) 2f else 2.5f), dual("\\", "|")) +
        wideRow(language, 2).take(letters.length) + punctuation('/') + dual("`", "~", width = 0.75f)
    return Row(keys + shift(15f - keys.units()))
}

/** Opens the symbols page; on that page the same place reads ABC and comes back. */
private fun pageKey(to: LayerId) =
    Key(if (to == LayerId.SYMBOLS) "€±" else "ABC", KeyAction.SwitchLayer(to), 1f, KeyStyle.FUNCTION)

/**
 * The bottom row, the phone's in order: Fn where the phone has 123, the globe when there is more
 * than one language to switch to (Meta otherwise), the comma by the space bar, the space naming
 * the language, the period, and the combination modifiers on the right, one of each: a latching
 * modifier is tapped and then the key, so a second copy for the other hand earns nothing. With
 * the globe on the board, Meta is Fn+Alt. No hide key: the toolbar has one, and so does the
 * system's navigation bar.
 */
private fun wideBottomRow(spaceLabel: String, withGlobe: Boolean): Row {
    val left = listOf(
        mod("Fn", ModifierKey.FN, 1.25f),
        if (withGlobe) Key("globe", KeyAction.SwitchLanguage, 1.25f, KeyStyle.FUNCTION, KeyIcon.GLOBE) else mod("Meta", ModifierKey.META, 1.25f),
        dual(",", "<"),
    )
    val alt = mod("Alt", ModifierKey.ALT, 1.25f)
    val right = listOf(
        dual(".", ">"),
        if (withGlobe) alt.copy(fnLegend = "Meta", fnAction = KeyAction.Modifier(ModifierKey.META)) else alt,
        mod("Ctrl", ModifierKey.CTRL, 1.25f),
    )
    return Row(left + Key(spaceLabel, KeyAction.Space, 15f - (left + right).units(), KeyStyle.SPACE) + right)
}

private val digitRow: Array<Key> = "1234567890".mapIndexed { i, c ->
    dual(c.toString(), "!@#$%^&*()"[i].toString(), fnLegend = "F${i + 1}", fnAction = fkey(i))
}.toTypedArray()

/**
 * A 60% ANSI board for one language, 15 units per row, for windows 600 dp and wider: every key
 * visible, shifted symbols printed above the digits and punctuation, F1–F12 and navigation on
 * Fn, the letters and accents of [language] on the ANSI slots. The edges are balanced rather than
 * standard: Esc, Tab and Caps are wider and Backspace and Enter narrower, so the split between
 * the hands (T|Y 7, G|H 7.25 of 15) sits nearer the middle of the screen. Shaped for thumbs
 * rather than a desk: `\|` beside the left Shift moves B under the right hand, the comma and
 * period sit by the space bar as on the phone, and the top row ends with the symbols page key.
 */
fun sixtyPercentLayer(language: Language, withGlobe: Boolean) = Layer(
    id = LayerId.LETTERS,
    units = 15f,
    rows = listOf(
        row(
            fn("Esc", KeyAction.KeyCode(KeyEvent.KEYCODE_ESCAPE), 1.5f),
            *digitRow,
            dual("-", "_", fnLegend = "F11", fnAction = fkey(10)),
            dual("=", "+", fnLegend = "F12", fnAction = fkey(11)),
            Key("backspace", KeyAction.Backspace, 1.5f, KeyStyle.FUNCTION, KeyIcon.BACKSPACE, fnLegend = "Del", repeats = true),
        ),
        row(
            fn("Tab", KeyAction.KeyCode(KeyEvent.KEYCODE_TAB), 2f),
            *wideRow(language, 0).toTypedArray(),
            pageKey(LayerId.SYMBOLS),
        ),
        row(
            Key("Caps", KeyAction.CapsLock, 2.25f, KeyStyle.MODIFIER),
            *wideRow(language, 1).toTypedArray(),
            enterKey(1.75f),
        ),
        wideShiftRow(language),
        wideBottomRow(language.nativeName, withGlobe),
    ),
)

/**
 * What the symbols page puts on the letter and punctuation keys, row by row, in order: what the
 * board has no key for. The Shift row takes as many as it has keys between the left Shift and
 * `` `~ `` (9 on English, 11 on Ukrainian), so its list is the longest a board needs.
 */
private val SymbolRows = listOf("€£¥₴¢©®™°§¶•", "«»„“”‘’…–—·", "±×÷≠≈≤≥∞¿¡‰")

/** This row with its keys from [from] until [until] replaced by [symbols], in order. */
private fun Row.withSymbols(from: Int, until: Int, symbols: String): Row {
    require(until - from <= symbols.length) { "${until - from} keys for ${symbols.length} symbols" }
    val replaced = keys.toMutableList()
    for (i in from until until) replaced[i] = Key(symbols[i - from].toString(), KeyAction.Text(symbols[i - from].toString()))
    return copy(keys = replaced)
}

/**
 * The symbols page of a 60% [letters] page: the same geometry, so nothing moves under a thumb,
 * with every key between a row's edge keys typing a symbol the board has no key for. The digits,
 * Tab, Caps, Enter, the Shifts, `` `~ `` and the bottom row stay; €± reads ABC and comes back.
 */
fun sixtyPercentSymbolsLayer(letters: Layer): Layer {
    val (digits, top, home, shiftRow) = letters.rows
    val rows = listOf(
        digits,
        top.withSymbols(1, top.keys.size - 1, SymbolRows[0]).let { it.copy(keys = it.keys.dropLast(1) + pageKey(LayerId.LETTERS)) },
        home.withSymbols(1, home.keys.size - 1, SymbolRows[1]),
        shiftRow.withSymbols(1, shiftRow.keys.size - 2, SymbolRows[2]),
        letters.rows[4],
    )
    return Layer(LayerId.SYMBOLS, rows, letters.units)
}

/** The layout for wide windows: letters and a symbols page, each whole or split. */
fun wideLayout(language: Language, withGlobe: Boolean): KeyboardLayout {
    val letters = sixtyPercentLayer(language, withGlobe)
    val layers = mapOf(LayerId.LETTERS to letters, LayerId.SYMBOLS to sixtyPercentSymbolsLayer(letters))
    return KeyboardLayout(layers = layers, units = 15f, splits = layers.mapValues { splitLayer(it.value) })
}

/** The English board with no globe. */
val SixtyPercentLayer: Layer = sixtyPercentLayer(Languages.english, withGlobe = false)

val WideLayout: KeyboardLayout = wideLayout(Languages.english, withGlobe = false)
