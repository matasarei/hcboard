package net.matasar.keyboard.macro

import android.view.KeyEvent
import net.matasar.keyboard.input.KeyStroke
import net.matasar.keyboard.input.keyStrokeFor

/** A key a macro can press by name, and the picker group it is listed under. */
data class NamedKey(val name: String, val keyCode: Int, val group: KeyGroup)

enum class KeyGroup { EDITING, MOVEMENT, FUNCTION, MODIFIER }

/**
 * The keys a macro names in its JSON ("Esc", "F1"): readable, and independent of the key codes'
 * values. A single character that is not a name ("a", "Z", "/") is its US key.
 */
object MacroKeys {

    val named: List<NamedKey> = buildList {
        add(NamedKey("Esc", KeyEvent.KEYCODE_ESCAPE, KeyGroup.EDITING))
        add(NamedKey("Tab", KeyEvent.KEYCODE_TAB, KeyGroup.EDITING))
        add(NamedKey("Enter", KeyEvent.KEYCODE_ENTER, KeyGroup.EDITING))
        add(NamedKey("Backspace", KeyEvent.KEYCODE_DEL, KeyGroup.EDITING))
        add(NamedKey("Delete", KeyEvent.KEYCODE_FORWARD_DEL, KeyGroup.EDITING))
        add(NamedKey("Insert", KeyEvent.KEYCODE_INSERT, KeyGroup.EDITING))
        add(NamedKey("Space", KeyEvent.KEYCODE_SPACE, KeyGroup.EDITING))
        add(NamedKey("Left", KeyEvent.KEYCODE_DPAD_LEFT, KeyGroup.MOVEMENT))
        add(NamedKey("Right", KeyEvent.KEYCODE_DPAD_RIGHT, KeyGroup.MOVEMENT))
        add(NamedKey("Up", KeyEvent.KEYCODE_DPAD_UP, KeyGroup.MOVEMENT))
        add(NamedKey("Down", KeyEvent.KEYCODE_DPAD_DOWN, KeyGroup.MOVEMENT))
        add(NamedKey("Home", KeyEvent.KEYCODE_MOVE_HOME, KeyGroup.MOVEMENT))
        add(NamedKey("End", KeyEvent.KEYCODE_MOVE_END, KeyGroup.MOVEMENT))
        add(NamedKey("PgUp", KeyEvent.KEYCODE_PAGE_UP, KeyGroup.MOVEMENT))
        add(NamedKey("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, KeyGroup.MOVEMENT))
        for (n in 1..12) add(NamedKey("F$n", KeyEvent.KEYCODE_F1 + n - 1, KeyGroup.FUNCTION))
        add(NamedKey("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, KeyGroup.MODIFIER))
        add(NamedKey("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, KeyGroup.MODIFIER))
        add(NamedKey("Alt", KeyEvent.KEYCODE_ALT_LEFT, KeyGroup.MODIFIER))
        add(NamedKey("Meta", KeyEvent.KEYCODE_META_LEFT, KeyGroup.MODIFIER))
    }

    private val byName: Map<String, NamedKey> = named.associateBy { it.name }

    /** The stroke for a key name or a single character, or null when it is neither. */
    fun strokeFor(name: String): KeyStroke? = byName[name]?.let { KeyStroke(it.keyCode) } ?: keyStrokeFor(name)
}
