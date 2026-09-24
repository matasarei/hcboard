package net.matasar.keyboard.layout

/**
 * The marks an address field keeps beside the space bar on the letters page, as the iPhone's
 * e-mail and web keyboards do: without them `.` and `@` or `/` are a page away on every address.
 */
enum class FieldMarks(val beforeSpace: String?) { NONE(null), EMAIL("@"), URL("/") }

/**
 * The bottom row every phone layer shares, as on the iPhone: the page switch, the globe when more
 * than one language is enabled (where the iPhone has its emoji key), space named after the
 * language, and a wide return. No comma or period: a double space types ". ", and both are on
 * the symbol pages; an address field gets its [marks] around the space bar instead. Scales to
 * the layer's units.
 */
internal fun bottomRow(
    switchTo: LayerId,
    switchLabel: String,
    units: Float,
    spaceLabel: String,
    withGlobe: Boolean,
    marks: FieldMarks = FieldMarks.NONE,
): Row {
    val before = marks.beforeSpace
    val fixed = SWITCH_KEY + RETURN_KEY + (if (withGlobe) GLOBE_KEY else 0f) + (if (before != null) 2f else 0f)
    val keys = mutableListOf(function(switchLabel, KeyAction.SwitchLayer(switchTo), SWITCH_KEY))
    if (withGlobe) keys += Key("globe", KeyAction.SwitchLanguage, GLOBE_KEY, KeyStyle.FUNCTION, KeyIcon.GLOBE)
    if (before != null) keys += function(before, KeyAction.Text(before))
    keys += spaceKey(units - fixed, spaceLabel)
    if (before != null) keys += function(".", KeyAction.Text("."))
    keys += enterKey(RETURN_KEY)
    return Row(keys)
}

private const val SWITCH_KEY = 1.25f
private const val GLOBE_KEY = 1.25f
private const val RETURN_KEY = 2.5f

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
