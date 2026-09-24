package net.matasar.keyboard.layout

import kotlinx.serialization.Serializable

/** Which page of keys is showing. */
enum class LayerId { LETTERS, SYMBOLS, CODE }

/** A key that shows and sends a shifted symbol on top of its main one. */
internal fun dual(main: String, shifted: String, fnLegend: String? = null, fnAction: KeyAction? = null, width: Float = 1f) =
    Key(main, KeyAction.Text(main, shifted), width, shiftedLabel = shifted, fnLegend = fnLegend, fnAction = fnAction)

/** The latching modifiers. Shift here is the combination modifier on the strip and the 60% board. */
@Serializable
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
    /** What the key means while Fn is active, shown as its glyph then (F1, Home, an arrow, Del). */
    val fnLegend: String? = null,
    /** Long-press alternatives (accents). */
    val longPress: List<String> = emptyList(),
    /** True for keys that repeat while held (backspace). */
    val repeats: Boolean = false,
    /** What the key does while Fn is active, when that is not a plain key code (60% board). */
    val fnAction: KeyAction? = null,
    /**
     * The US character of the physical slot a letter sits in: what a modifier combination sends
     * and what the key shows while a combination modifier is active, so Ctrl+С on a Cyrillic
     * board is Ctrl+C as on a PC. Only letter keys carry one.
     */
    val slot: Char? = null,
) {
    /** A stable identity for pressed-state tracking and tests. */
    val id: String get() = "$label:$action"
}

/**
 * A row of keys. [leadingUnits] and [trailingUnits] indent the row by whole or half key widths,
 * the way the home row sits half a key in from the top row. [innerGapUnits] opens a gap of that
 * many units after the first key and another before the last, the way the iPhone sets Shift and
 * backspace apart from a short row of letters.
 */
data class Row(
    val keys: List<Key>,
    val leadingUnits: Float = 0f,
    val trailingUnits: Float = 0f,
    val innerGapUnits: Float = 0f,
) {
    val totalUnits: Float get() = keys.sumOf { it.width.toDouble() }.toFloat() + leadingUnits + trailingUnits + innerGapUnits * 2
}

/** A page of rows; every row adds up to [units]. */
data class Layer(val id: LayerId, val rows: List<Row>, val units: Float = 10f)

/** The layers of a board; the wide board also carries its [split] halves, drawn when the window asks for them. */
data class KeyboardLayout(val layers: Map<LayerId, Layer>, val units: Float = 10f, val split: SplitLayer? = null) {
    fun layer(id: LayerId): Layer = layers.getValue(id)
}

/**
 * The three letter rows of a US ANSI board as slot characters. A language's row fills them left
 * to right; the slot is what a letter sends in a modifier combination on any board.
 */
internal object AnsiSlots {
    val rows: List<String> = listOf("qwertyuiop[]", "asdfghjkl;'", "zxcvbnm,./")
}

// ---- small builders so the layer files read like the boards they build ----

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

internal fun symbols(chars: String, alternates: Map<Char, List<String>> = SymbolAlternates): Array<Key> =
    chars.map { c -> Key(label = c.toString(), action = KeyAction.Text(c.toString()), longPress = alternates[c].orEmpty()) }.toTypedArray()

/**
 * Long presses on the symbol pages, so what the iPhone's two pages leave out is still in reach:
 * the backtick on the apostrophe, typographic quotes and dashes.
 */
internal val SymbolAlternates: Map<Char, List<String>> = mapOf(
    '\'' to listOf("`", "’", "‘"),
    '"' to listOf("«", "»", "„", "“", "”"),
    '-' to listOf("–", "—"),
    '?' to listOf("¿"),
    '!' to listOf("¡"),
    '.' to listOf("…"),
)

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
