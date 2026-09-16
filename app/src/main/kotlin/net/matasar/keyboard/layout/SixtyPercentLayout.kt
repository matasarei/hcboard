package net.matasar.keyboard.layout

import android.view.KeyEvent

private fun fkey(index: Int) = KeyAction.KeyCode(KeyEvent.KEYCODE_F1 + index)
private fun nav(code: Int) = KeyAction.KeyCode(code)
private fun mod(label: String, key: ModifierKey, width: Float) =
    Key(label, KeyAction.Modifier(key), width, KeyStyle.MODIFIER)
private fun fn(label: String, action: KeyAction, width: Float = 1f, fnLegend: String? = null, fnAction: KeyAction? = null) =
    Key(label, action, width, KeyStyle.FUNCTION, fnLegend = fnLegend, fnAction = fnAction)

/** Letters on the 60% board, with the Fn meanings of I J K L (arrows), U O (Home, End), H N (PgUp, PgDn). */
private val FnOnLetters: Map<Char, Pair<String, KeyAction>> = mapOf(
    'i' to ("↑" to nav(KeyEvent.KEYCODE_DPAD_UP)),
    'j' to ("←" to nav(KeyEvent.KEYCODE_DPAD_LEFT)),
    'k' to ("↓" to nav(KeyEvent.KEYCODE_DPAD_DOWN)),
    'l' to ("→" to nav(KeyEvent.KEYCODE_DPAD_RIGHT)),
    'u' to ("Home" to nav(KeyEvent.KEYCODE_MOVE_HOME)),
    'o' to ("End" to nav(KeyEvent.KEYCODE_MOVE_END)),
    'h' to ("PgUp" to nav(KeyEvent.KEYCODE_PAGE_UP)),
    'n' to ("PgDn" to nav(KeyEvent.KEYCODE_PAGE_DOWN)),
)

private fun wideLetters(chars: String): Array<Key> = chars.map { c ->
    val fnPair = FnOnLetters[c]
    Key(
        label = c.toString(),
        action = KeyAction.Letter(c.toString(), c.uppercase()),
        longPress = Accents[c].orEmpty(),
        fnLegend = fnPair?.first,
        fnAction = fnPair?.second,
    )
}.toTypedArray()

private val digitRow: Array<Key> = "1234567890".mapIndexed { i, c ->
    dual(c.toString(), "!@#$%^&*()"[i].toString(), fnLegend = "F${i + 1}", fnAction = fkey(i))
}.toTypedArray()

/**
 * A standard 60% ANSI board, 15 units per row, for windows 600 dp and wider: every key visible,
 * shifted symbols printed above the digits and punctuation, F1–F12 and navigation as Fn legends.
 */
val SixtyPercentLayer = Layer(
    id = LayerId.LETTERS,
    units = 15f,
    rows = listOf(
        row(
            fn("Esc", KeyAction.KeyCode(KeyEvent.KEYCODE_ESCAPE), fnLegend = "`", fnAction = KeyAction.Text("`", "~")),
            *digitRow,
            dual("-", "_", fnLegend = "F11", fnAction = fkey(10)),
            dual("=", "+", fnLegend = "F12", fnAction = fkey(11)),
            Key("backspace", KeyAction.Backspace, 2f, KeyStyle.FUNCTION, KeyIcon.BACKSPACE, fnLegend = "Del", repeats = true),
        ),
        row(
            fn("Tab", KeyAction.KeyCode(KeyEvent.KEYCODE_TAB), 1.5f),
            *wideLetters("qwertyuiop"),
            dual("[", "{"),
            dual("]", "}"),
            dual("\\", "|", width = 1.5f),
        ),
        row(
            Key("Caps", KeyAction.CapsLock, 1.75f, KeyStyle.MODIFIER),
            *wideLetters("asdfghjkl"),
            dual(";", ":"),
            dual("'", "\""),
            enterKey(2.25f),
        ),
        row(
            Key("Shift", KeyAction.Shift, 2.25f, KeyStyle.MODIFIER),
            *wideLetters("zxcvbnm"),
            dual(",", "<"),
            dual(".", ">"),
            dual("/", "?"),
            Key("Shift", KeyAction.Shift, 2.75f, KeyStyle.MODIFIER),
        ),
        row(
            mod("Ctrl", ModifierKey.CTRL, 1.25f),
            mod("Meta", ModifierKey.META, 1.25f),
            mod("Alt", ModifierKey.ALT, 1.25f),
            Key("English", KeyAction.Space, 6.25f, KeyStyle.SPACE, fnLegend = "Lang", fnAction = KeyAction.SwitchLanguage),
            mod("Alt", ModifierKey.ALT, 1.25f),
            mod("Fn", ModifierKey.FN, 1.25f),
            mod("Ctrl", ModifierKey.CTRL, 1.25f),
            Key("hide", KeyAction.HideKeyboard, 1.25f, KeyStyle.FUNCTION, KeyIcon.KEYBOARD_HIDE),
        ),
    ),
)

/** The layout for wide windows: one board carries letters, symbols and modifiers. */
val WideLayout = KeyboardLayout(layers = mapOf(LayerId.LETTERS to SixtyPercentLayer), units = 15f)
