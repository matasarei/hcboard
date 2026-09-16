package net.matasar.keyboard.layout

/** The bottom row every phone layer shares: page switch, comma, space, period, enter. */
private fun bottomRow(switchTo: LayerId, switchLabel: String) = row(
    function(switchLabel, KeyAction.SwitchLayer(switchTo), 1.5f),
    function(",", KeyAction.Text(",")),
    spaceKey(),
    function(".", KeyAction.Text(".")),
    enterKey(),
)

/** QWERTY letters, as in the Main mock. */
val LettersLayer = Layer(
    id = LayerId.LETTERS,
    rows = listOf(
        row(*letters("qwertyuiop")),
        row(*letters("asdfghjkl"), leading = 0.5f, trailing = 0.5f),
        row(shiftKey(), *letters("zxcvbnm"), backspaceKey()),
        bottomRow(LayerId.SYMBOLS, "?123"),
    ),
)

/** Digits and common punctuation, as in the Symbols mock. */
val SymbolsLayer = Layer(
    id = LayerId.SYMBOLS,
    rows = listOf(
        row(*symbols("1234567890")),
        row(*symbols("@#$%&-+()/")),
        row(function("{ }", KeyAction.SwitchLayer(LayerId.CODE), 1.5f), *symbols("*\"':;!?"), backspaceKey()),
        bottomRow(LayerId.LETTERS, "ABC"),
    ),
)

/** The code page: braces, brackets, pipes and the rest that is slow to reach elsewhere. */
val CodeLayer = Layer(
    id = LayerId.CODE,
    rows = listOf(
        row(*symbols("{}[]|\\~`<>")),
        row(*symbols("!@#$%^&*-=")),
        row(function("?123", KeyAction.SwitchLayer(LayerId.SYMBOLS), 1.5f), *symbols(";:'\"/_+"), backspaceKey()),
        bottomRow(LayerId.LETTERS, "ABC"),
    ),
)

val PhoneLayout = KeyboardLayout(
    layers = mapOf(
        LayerId.LETTERS to LettersLayer,
        LayerId.SYMBOLS to SymbolsLayer,
        LayerId.CODE to CodeLayer,
    ),
)
