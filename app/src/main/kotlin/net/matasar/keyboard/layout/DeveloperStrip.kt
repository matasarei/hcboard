package net.matasar.keyboard.layout

import android.view.KeyEvent

private fun modifier(label: String, key: ModifierKey) =
    Key(label, KeyAction.Modifier(key), style = KeyStyle.MODIFIER)

private fun keyCode(label: String, code: Int, icon: KeyIcon? = null, fnCode: Int? = null, fnLegend: String? = null, repeats: Boolean = false) =
    Key(label, KeyAction.KeyCode(code, fnCode), style = KeyStyle.FUNCTION, icon = icon, fnLegend = fnLegend, repeats = repeats)

/**
 * The developer-mode strip that sits under the toolbar, above whatever layer is open:
 * Esc, Tab, the modifiers, arrows (Home/PgUp/PgDn/End with Fn) and Fn.
 */
val DeveloperStrip = Row(
    listOf(
        keyCode("Esc", KeyEvent.KEYCODE_ESCAPE),
        keyCode("Tab", KeyEvent.KEYCODE_TAB),
        modifier("Ctrl", ModifierKey.CTRL),
        modifier("Alt", ModifierKey.ALT),
        modifier("Shift", ModifierKey.SHIFT),
        keyCode("left", KeyEvent.KEYCODE_DPAD_LEFT, KeyIcon.ARROW_LEFT, KeyEvent.KEYCODE_MOVE_HOME, "Home", repeats = true),
        keyCode("up", KeyEvent.KEYCODE_DPAD_UP, KeyIcon.ARROW_UP, KeyEvent.KEYCODE_PAGE_UP, "PgUp", repeats = true),
        keyCode("down", KeyEvent.KEYCODE_DPAD_DOWN, KeyIcon.ARROW_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, "PgDn", repeats = true),
        keyCode("right", KeyEvent.KEYCODE_DPAD_RIGHT, KeyIcon.ARROW_RIGHT, KeyEvent.KEYCODE_MOVE_END, "End", repeats = true),
        modifier("Fn", ModifierKey.FN),
    ),
)
