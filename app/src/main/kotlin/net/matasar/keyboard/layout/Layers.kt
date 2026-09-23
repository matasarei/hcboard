package net.matasar.keyboard.layout

/**
 * The bottom row every phone layer shares: page switch, comma, the globe when more than one
 * language is enabled, space named after the language, period, enter. Scales to the layer's units.
 */
internal fun bottomRow(switchTo: LayerId, switchLabel: String, units: Float, spaceLabel: String, withGlobe: Boolean): Row {
    val fixed = 1.5f + 1f + 1f + 1.5f + (if (withGlobe) 1f else 0f)
    val keys = mutableListOf(
        function(switchLabel, KeyAction.SwitchLayer(switchTo), 1.5f),
        function(",", KeyAction.Text(",")),
    )
    if (withGlobe) keys += Key("globe", KeyAction.SwitchLanguage, 1f, KeyStyle.FUNCTION, KeyIcon.GLOBE)
    keys += spaceKey(units - fixed, spaceLabel)
    keys += function(".", KeyAction.Text("."))
    keys += enterKey()
    return Row(keys)
}

/** Digits and common punctuation, as in the Symbols mock. Shared by every language. */
fun symbolsLayer(spaceLabel: String, withGlobe: Boolean) = Layer(
    id = LayerId.SYMBOLS,
    rows = listOf(
        row(*symbols("1234567890")),
        row(*symbols("@#$%&-+()/")),
        row(function("{ }", KeyAction.SwitchLayer(LayerId.CODE), 1.5f), *symbols("*\"':;!?"), backspaceKey()),
        bottomRow(LayerId.LETTERS, "ABC", 10f, spaceLabel, withGlobe),
    ),
)

/** The code page: braces, brackets, pipes and the rest that is slow to reach elsewhere. */
fun codeLayer(spaceLabel: String, withGlobe: Boolean) = Layer(
    id = LayerId.CODE,
    rows = listOf(
        row(*symbols("{}[]|\\~`<>")),
        row(*symbols("!@#$%^&*-=")),
        row(function("?123", KeyAction.SwitchLayer(LayerId.SYMBOLS), 1.5f), *symbols(";:'\"/_+"), backspaceKey()),
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
 * start with the digits already, so [numberRow] reaches the letters page only.
 */
fun phoneLayout(language: Language, withGlobe: Boolean, numberRow: Boolean = false): KeyboardLayout = KeyboardLayout(
    layers = mapOf(
        LayerId.LETTERS to language.lettersLayer(withGlobe, numberRow),
        LayerId.SYMBOLS to symbolsLayer(language.nativeName, withGlobe),
        LayerId.CODE to codeLayer(language.nativeName, withGlobe),
    ),
)

/** QWERTY letters, as in the Main mock: English with no globe. */
val LettersLayer: Layer = Languages.english.lettersLayer(withGlobe = false)

val SymbolsLayer: Layer = symbolsLayer("English", withGlobe = false)

val CodeLayer: Layer = codeLayer("English", withGlobe = false)

val PhoneLayout: KeyboardLayout = phoneLayout(Languages.english, withGlobe = false)
