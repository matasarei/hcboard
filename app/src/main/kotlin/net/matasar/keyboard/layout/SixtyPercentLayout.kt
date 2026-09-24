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
 * The Shift row. The left Shift is the wider one on every board (the thumb's side of a held
 * phone); the right one takes what is left. Seven letters leave `, . /` as on the standard
 * board. Nine (Cyrillic) would leave only `/`, so the Shifts give up a unit between them and
 * the row ends with `.` (`,` shifted) and `/`: the way Cyrillic PC keyboards do it. The `.` key
 * has nothing on Fn: б and ю, on the `,` and `.` slots, already carry `,<` and `.>` there.
 */
private fun wideShiftRow(language: Language): Row {
    val letters = language.rows[2]
    require(letters.length <= 9) { "${language.tag} bottom row has ${letters.length} letters; at most 9 fit" }
    if (letters.length < 9) return row(shift(2.75f), *wideRow(language, 2).toTypedArray(), shift(2.25f))
    val onSlots = wideRow(language, 2).take(letters.length)
    val period = dual(".", ",")
    return row(shift(2.25f), *onSlots.toTypedArray(), period, punctuation('/'), shift(1.75f))
}

/**
 * Modifiers around the space bar, which names the language. The globe sits left of Space when
 * there is more than one language to switch to. No hide key: the toolbar has one, and so does
 * the system's navigation bar.
 */
private fun wideBottomRow(spaceLabel: String, withGlobe: Boolean): Row {
    val keys = mutableListOf(mod("Ctrl", ModifierKey.CTRL, 1.25f), mod("Meta", ModifierKey.META, 1.25f), mod("Alt", ModifierKey.ALT, 1.25f))
    if (withGlobe) keys += Key("globe", KeyAction.SwitchLanguage, 1.25f, KeyStyle.FUNCTION, KeyIcon.GLOBE)
    keys += Key(spaceLabel, KeyAction.Space, if (withGlobe) 6.25f else 7.5f, KeyStyle.SPACE)
    keys += mod("Alt", ModifierKey.ALT, 1.25f)
    keys += mod("Fn", ModifierKey.FN, 1.25f)
    keys += mod("Ctrl", ModifierKey.CTRL, 1.25f)
    return Row(keys)
}

private val digitRow: Array<Key> = "1234567890".mapIndexed { i, c ->
    dual(c.toString(), "!@#$%^&*()"[i].toString(), fnLegend = "F${i + 1}", fnAction = fkey(i))
}.toTypedArray()

/**
 * A 60% ANSI board for one language, 15 units per row, for windows 600 dp and wider: every key
 * visible, shifted symbols printed above the digits and punctuation, F1–F12 and navigation on
 * Fn, the letters and accents of [language] on the ANSI slots. The edges are balanced rather than
 * standard: Esc, Tab and Caps are wider and Backspace, `\` and Enter narrower, so the split
 * between the hands (T|Y 7, G|H 7.25 of 15) sits nearer the middle of the screen. No key moves.
 */
fun sixtyPercentLayer(language: Language, withGlobe: Boolean) = Layer(
    id = LayerId.LETTERS,
    units = 15f,
    rows = listOf(
        row(
            fn("Esc", KeyAction.KeyCode(KeyEvent.KEYCODE_ESCAPE), 1.5f, fnLegend = "`~", fnAction = KeyAction.Text("`", "~")),
            *digitRow,
            dual("-", "_", fnLegend = "F11", fnAction = fkey(10)),
            dual("=", "+", fnLegend = "F12", fnAction = fkey(11)),
            Key("backspace", KeyAction.Backspace, 1.5f, KeyStyle.FUNCTION, KeyIcon.BACKSPACE, fnLegend = "Del", repeats = true),
        ),
        row(
            fn("Tab", KeyAction.KeyCode(KeyEvent.KEYCODE_TAB), 2f),
            *wideRow(language, 0).toTypedArray(),
            dual("\\", "|"),
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

/** The layout for wide windows: one board carries letters, symbols and modifiers, whole or split. */
fun wideLayout(language: Language, withGlobe: Boolean): KeyboardLayout =
    KeyboardLayout(
        layers = mapOf(LayerId.LETTERS to sixtyPercentLayer(language, withGlobe)),
        units = 15f,
        split = splitLayer(language, withGlobe),
    )

/** The English board with no globe. */
val SixtyPercentLayer: Layer = sixtyPercentLayer(Languages.english, withGlobe = false)

val WideLayout: KeyboardLayout = wideLayout(Languages.english, withGlobe = false)
