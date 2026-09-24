package net.matasar.keyboard.layout

/**
 * What sits left of the space bar: a comma, or in an address field what an address needs more,
 * as the iPhone's e-mail and web keyboards do. A period is right of it always.
 */
enum class FieldMarks(val beforeSpace: String) { NONE(","), EMAIL("@"), URL("/") }

/**
 * The bottom row every phone page shares, the same keys at the same places on every page and in
 * every language: the page switch, a comma (or its field's [marks]), the globe when more than one
 * language is enabled, space named after the language, a period and a wide return. Its widths are
 * shares of the board's width, set on a ten-unit board and scaled to the layer's [units], so a
 * twelve-key Ukrainian page or a symbols page does not move or resize a key under the thumb.
 */
internal fun bottomRow(
    switchTo: LayerId,
    switchLabel: String,
    units: Float,
    spaceLabel: String,
    withGlobe: Boolean,
    marks: FieldMarks = FieldMarks.NONE,
): Row {
    val scale = units / BOTTOM_ROW_UNITS
    val fixed = SWITCH_KEY + MARK_KEY * 2 + RETURN_KEY + (if (withGlobe) GLOBE_KEY else 0f)
    val keys = mutableListOf(
        function(switchLabel, KeyAction.SwitchLayer(switchTo), SWITCH_KEY * scale),
        function(marks.beforeSpace, KeyAction.Text(marks.beforeSpace), MARK_KEY * scale),
    )
    if (withGlobe) keys += Key("globe", KeyAction.SwitchLanguage, GLOBE_KEY * scale, KeyStyle.FUNCTION, KeyIcon.GLOBE)
    keys += spaceKey((BOTTOM_ROW_UNITS - fixed) * scale, spaceLabel)
    keys += function(".", KeyAction.Text("."), MARK_KEY * scale)
    keys += enterKey(RETURN_KEY * scale)
    return Row(keys)
}

/** The board the bottom row's widths are set on; any other width scales them. */
private const val BOTTOM_ROW_UNITS = 10f
private const val SWITCH_KEY = 1.25f
private const val MARK_KEY = 1f
private const val GLOBE_KEY = 1f
private const val RETURN_KEY = 2f

/**
 * The five marks on the third row of both symbol pages, a little wider than a key, as on the
 * iPhone: ? ! ' and two the page has nowhere else. Comma and period are on the bottom row of
 * every page, so they are not here a second time.
 */
private fun marks(extra: String): Array<Key> = symbols("?!'$extra").map { it.copy(width = 1.4f) }.toTypedArray()

/** Digits and common punctuation: the iPhone's 123 page. Shared by every language. */
fun symbolsLayer(spaceLabel: String, withGlobe: Boolean, marks: FieldMarks = FieldMarks.NONE) = Layer(
    id = LayerId.SYMBOLS,
    rows = listOf(
        row(*symbols("1234567890")),
        row(*symbols("-/:;()$&@\"")),
        row(function("#+=", KeyAction.SwitchLayer(LayerId.CODE), 1.5f), *marks("*#"), backspaceKey()),
        bottomRow(LayerId.LETTERS, "ABC", 10f, spaceLabel, withGlobe, marks),
    ),
)

/** Brackets, maths and the rest: the iPhone's #+= page. */
fun codeLayer(spaceLabel: String, withGlobe: Boolean, marks: FieldMarks = FieldMarks.NONE) = Layer(
    id = LayerId.CODE,
    rows = listOf(
        row(*symbols("[]{}#%^*+=")),
        row(*symbols("_\\|~<>€£¥•")),
        row(function("123", KeyAction.SwitchLayer(LayerId.SYMBOLS), 1.5f), *marks("`…"), backspaceKey()),
        bottomRow(LayerId.LETTERS, "ABC", 10f, spaceLabel, withGlobe, marks),
    ),
)

/**
 * The symbols page of a board whose letters page has the digits across the top already: no digit
 * row of its own, but the symbol rows of both iPhone pages and every mark, the backtick included,
 * so it is one page, as tall as the letters page it replaces.
 */
fun symbolsBesideDigitsLayer(spaceLabel: String, withGlobe: Boolean, marks: FieldMarks = FieldMarks.NONE) = Layer(
    id = LayerId.SYMBOLS,
    rows = listOf(
        row(*symbols("-/:;()$&@\"")),
        row(*symbols("[]{}#%^*+=")),
        row(*symbols("_\\|~<>€£¥•")),
        // Comma and period are on the bottom row; * and # are above already.
        row(*symbols("`?!'…").map { it.copy(width = 1.68f) }.toTypedArray(), backspaceKey(1.6f)),
        bottomRow(LayerId.LETTERS, "ABC", 10f, spaceLabel, withGlobe, marks),
    ),
)

/**
 * The digits across the top of the letters page, as on Gboard: ten keys that share the page's
 * width whatever its unit count, so a twelve-letter top row keeps them in line with it.
 */
internal fun numberRow(units: Float) = Row(symbols("1234567890").map { it.copy(width = units / 10f) })

/**
 * The phone layout for one language: its letters plus the shared symbols and code pages. With
 * [numberRow] the digits sit on the letters page, and its symbols key opens one page of symbols
 * with no digits of its own. An address field's [marks] reach every page's bottom row.
 */
fun phoneLayout(language: Language, withGlobe: Boolean, numberRow: Boolean = false, marks: FieldMarks = FieldMarks.NONE): KeyboardLayout = KeyboardLayout(
    layers = mapOf(
        LayerId.LETTERS to language.lettersLayer(withGlobe, numberRow, marks),
        LayerId.SYMBOLS to
            if (numberRow) symbolsBesideDigitsLayer(language.nativeName, withGlobe, marks) else symbolsLayer(language.nativeName, withGlobe, marks),
        LayerId.CODE to codeLayer(language.nativeName, withGlobe, marks),
    ),
)

/** QWERTY letters: English with no globe. */
val LettersLayer: Layer = Languages.english.lettersLayer(withGlobe = false)

val SymbolsLayer: Layer = symbolsLayer("English", withGlobe = false)

val CodeLayer: Layer = codeLayer("English", withGlobe = false)

val PhoneLayout: KeyboardLayout = phoneLayout(Languages.english, withGlobe = false)
