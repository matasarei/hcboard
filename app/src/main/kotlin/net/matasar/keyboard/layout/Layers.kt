package net.matasar.keyboard.layout

/**
 * What sits left of the space bar on the letters page: a comma, or in an address field what an
 * address needs more, as the iPhone's e-mail and web keyboards do. A period is right of it always.
 */
enum class FieldMarks(val beforeSpace: String) { NONE(","), EMAIL("@"), URL("/") }

/**
 * The bottom row every phone layer shares: the page switch, the globe when more than one
 * language is enabled, space named after the language, and a wide return. The letters page has
 * a comma (or its field's [marks]) and a period around the space bar; the symbol pages, whose
 * third row has both, pass none. Scales to the layer's units.
 */
internal fun bottomRow(
    switchTo: LayerId,
    switchLabel: String,
    units: Float,
    spaceLabel: String,
    withGlobe: Boolean,
    marks: FieldMarks? = null,
): Row {
    val fixed = SWITCH_KEY + RETURN_KEY + (if (withGlobe) GLOBE_KEY else 0f) + (if (marks != null) 2f else 0f)
    val keys = mutableListOf(function(switchLabel, KeyAction.SwitchLayer(switchTo), SWITCH_KEY))
    if (marks != null) keys += function(marks.beforeSpace, KeyAction.Text(marks.beforeSpace))
    if (withGlobe) keys += Key("globe", KeyAction.SwitchLanguage, GLOBE_KEY, KeyStyle.FUNCTION, KeyIcon.GLOBE)
    keys += spaceKey(units - fixed, spaceLabel)
    if (marks != null) keys += function(".", KeyAction.Text("."))
    keys += enterKey(RETURN_KEY)
    return Row(keys)
}

private const val SWITCH_KEY = 1.25f
private const val GLOBE_KEY = 1f
private const val RETURN_KEY = 2f

/** The five marks on the third row of both symbol pages, a little wider than a key, as on the iPhone. */
private fun marks(): Array<Key> = symbols(".,?!'").map { it.copy(width = 1.4f) }.toTypedArray()

/** Digits and common punctuation: the iPhone's 123 page. Shared by every language. */
fun symbolsLayer(spaceLabel: String, withGlobe: Boolean) = Layer(
    id = LayerId.SYMBOLS,
    rows = listOf(
        row(*symbols("1234567890")),
        row(*symbols("-/:;()$&@\"")),
        row(function("#+=", KeyAction.SwitchLayer(LayerId.CODE), 1.5f), *marks(), backspaceKey()),
        bottomRow(LayerId.LETTERS, "ABC", 10f, spaceLabel, withGlobe),
    ),
)

/** Brackets, maths and the rest: the iPhone's #+= page. */
fun codeLayer(spaceLabel: String, withGlobe: Boolean) = Layer(
    id = LayerId.CODE,
    rows = listOf(
        row(*symbols("[]{}#%^*+=")),
        row(*symbols("_\\|~<>€£¥•")),
        row(function("123", KeyAction.SwitchLayer(LayerId.SYMBOLS), 1.5f), *marks(), backspaceKey()),
        bottomRow(LayerId.LETTERS, "ABC", 10f, spaceLabel, withGlobe),
    ),
)

/**
 * The digits across the top of the letters page, as on Gboard: ten keys that share the page's
 * width whatever its unit count, so a twelve-letter top row keeps them in line with it.
 */
internal fun numberRow(units: Float) = Row(symbols("1234567890").map { it.copy(width = units / 10f) })

/**
 * The phone layout for one language: its letters plus the shared symbols and code pages, which
 * start with the digits already, so [numberRow] and an address field's [marks] reach the letters
 * page only.
 */
fun phoneLayout(language: Language, withGlobe: Boolean, numberRow: Boolean = false, marks: FieldMarks = FieldMarks.NONE): KeyboardLayout = KeyboardLayout(
    layers = mapOf(
        LayerId.LETTERS to language.lettersLayer(withGlobe, numberRow, marks),
        LayerId.SYMBOLS to symbolsLayer(language.nativeName, withGlobe),
        LayerId.CODE to codeLayer(language.nativeName, withGlobe),
    ),
)

/** QWERTY letters: English with no globe. */
val LettersLayer: Layer = Languages.english.lettersLayer(withGlobe = false)

val SymbolsLayer: Layer = symbolsLayer("English", withGlobe = false)

val CodeLayer: Layer = codeLayer("English", withGlobe = false)

val PhoneLayout: KeyboardLayout = phoneLayout(Languages.english, withGlobe = false)
