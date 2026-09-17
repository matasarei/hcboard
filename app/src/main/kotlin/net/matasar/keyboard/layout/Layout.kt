package net.matasar.keyboard.layout

/** Which page of keys is showing. */
enum class LayerId { LETTERS, SYMBOLS, CODE }

/** A key that shows and sends a shifted symbol on top of its main one. */
internal fun dual(main: String, shifted: String, fnLegend: String? = null, fnAction: KeyAction? = null, width: Float = 1f) =
    Key(main, KeyAction.Text(main, shifted), width, shiftedLabel = shifted, fnLegend = fnLegend, fnAction = fnAction)

/** The latching modifiers. Shift here is the combination modifier on the strip and the 60% board. */
enum class ModifierKey { CTRL, ALT, SHIFT, META, FN }

/** How a key is painted. */
enum class KeyStyle { LETTER, FUNCTION, ACTION, MODIFIER, SPACE }

/** Vector icons a key can show instead of a text label. */
enum class KeyIcon {
    SHIFT, SHIFT_FILLED, BACKSPACE, ENTER, ARROW_LEFT, ARROW_RIGHT, ARROW_UP, ARROW_DOWN, UNDO, REDO,
    KEYBOARD_HIDE, GLOBE, SEARCH, SEND, CHECK,
}

/** What a key does when tapped. */
sealed interface KeyAction {
    /** A letter with a case: what is committed follows shift. */
    data class Letter(val lower: String, val upper: String) : KeyAction

    /** Any text committed as-is; [shifted] is what goes out while shift is active (dual legends). */
    data class Text(val text: String, val shifted: String? = null) : KeyAction

    /** Caps Lock on the 60% board: toggles locked shift. */
    data object CapsLock : KeyAction

    data object Space : KeyAction
    data object Backspace : KeyAction
    data object Enter : KeyAction
    data object Shift : KeyAction
    data class SwitchLayer(val layer: LayerId) : KeyAction
    data class Modifier(val modifier: ModifierKey) : KeyAction

    /** A key event with a key code, and the code it sends while Fn is active. */
    data class KeyCode(val keyCode: Int, val fnKeyCode: Int? = null) : KeyAction

    data object HideKeyboard : KeyAction
    data object SwitchLanguage : KeyAction
}

data class Key(
    val label: String,
    val action: KeyAction,
    val width: Float = 1f,
    val style: KeyStyle = KeyStyle.LETTER,
    val icon: KeyIcon? = null,
    /** Printed above the main glyph on the 60% board and sent when Shift is active. */
    val shiftedLabel: String? = null,
    /** Printed top-right in small type; what the key means while Fn is active. */
    val fnLegend: String? = null,
    /** Long-press alternatives (accents). */
    val longPress: List<String> = emptyList(),
    /** True for keys that repeat while held (backspace). */
    val repeats: Boolean = false,
    /** What the key does while Fn is active, when that is not a plain key code (60% board). */
    val fnAction: KeyAction? = null,
) {
    /** A stable identity for pressed-state tracking and tests. */
    val id: String get() = "$label:$action"
}

/**
 * A row of keys. [leadingUnits] and [trailingUnits] indent the row by whole or half key widths,
 * the way the home row sits half a key in from the top row.
 */
data class Row(
    val keys: List<Key>,
    val leadingUnits: Float = 0f,
    val trailingUnits: Float = 0f,
) {
    val totalUnits: Float get() = keys.sumOf { it.width.toDouble() }.toFloat() + leadingUnits + trailingUnits
}

/** A page of rows; every row adds up to [units]. */
data class Layer(val id: LayerId, val rows: List<Row>, val units: Float = 10f)

data class KeyboardLayout(val layers: Map<LayerId, Layer>, val units: Float = 10f) {
    fun layer(id: LayerId): Layer = layers.getValue(id)
}

// ---- small builders so the layer files read like the mocks ----

internal fun row(vararg keys: Key, leading: Float = 0f, trailing: Float = 0f) =
    Row(keys.toList(), leading, trailing)

internal fun letters(chars: String): Array<Key> =
    chars.map { c ->
        Key(
            label = c.toString(),
            action = KeyAction.Letter(c.toString(), c.uppercase()),
            longPress = Accents[c].orEmpty(),
        )
    }.toTypedArray()

internal fun symbols(chars: String): Array<Key> =
    chars.map { c -> Key(label = c.toString(), action = KeyAction.Text(c.toString())) }.toTypedArray()

internal fun function(label: String, action: KeyAction, width: Float = 1f, icon: KeyIcon? = null) =
    Key(label, action, width, KeyStyle.FUNCTION, icon)

internal fun shiftKey(width: Float = 1.5f) =
    function("shift", KeyAction.Shift, width, KeyIcon.SHIFT)

internal fun backspaceKey(width: Float = 1.5f) =
    Key("backspace", KeyAction.Backspace, width, KeyStyle.FUNCTION, KeyIcon.BACKSPACE, repeats = true)

internal fun enterKey(width: Float = 1.5f) =
    Key("enter", KeyAction.Enter, width, KeyStyle.ACTION, KeyIcon.ENTER)

/** The space bar shows the layout's language, the way Gboard does. */
internal fun spaceKey(width: Float = 5f, label: String = "English") =
    Key(label, KeyAction.Space, width, KeyStyle.SPACE)

/** Long-press accents for the letter layers; the first entry is what a plain long press selects. */
internal val Accents: Map<Char, List<String>> = mapOf(
    'a' to listOf("á", "à", "â", "ä", "ã", "å", "ā", "ą"),
    'c' to listOf("ç", "ć", "č"),
    'e' to listOf("é", "è", "ê", "ë", "ē", "ę", "ė"),
    'i' to listOf("í", "ì", "î", "ï", "ī", "į"),
    'n' to listOf("ñ", "ń"),
    'o' to listOf("ó", "ò", "ô", "ö", "õ", "ø", "ō"),
    's' to listOf("ś", "š", "ß"),
    'u' to listOf("ú", "ù", "û", "ü", "ū", "ų"),
    'y' to listOf("ý", "ÿ"),
    'z' to listOf("ž", "ź", "ż"),
)
