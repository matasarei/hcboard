package net.matasar.keyboard.input

import android.view.KeyEvent

/** A key code plus the meta state it needs — the shifted symbols carry Shift. */
data class KeyStroke(val keyCode: Int, val metaState: Int = 0)

private const val SHIFT = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

private val plain: Map<Char, Int> = buildMap {
    ('a'..'z').forEachIndexed { i, c -> put(c, KeyEvent.KEYCODE_A + i) }
    ('0'..'9').forEachIndexed { i, c -> put(c, KeyEvent.KEYCODE_0 + i) }
    put(' ', KeyEvent.KEYCODE_SPACE)
    put('-', KeyEvent.KEYCODE_MINUS)
    put('=', KeyEvent.KEYCODE_EQUALS)
    put('[', KeyEvent.KEYCODE_LEFT_BRACKET)
    put(']', KeyEvent.KEYCODE_RIGHT_BRACKET)
    put('\\', KeyEvent.KEYCODE_BACKSLASH)
    put(';', KeyEvent.KEYCODE_SEMICOLON)
    put('\'', KeyEvent.KEYCODE_APOSTROPHE)
    put(',', KeyEvent.KEYCODE_COMMA)
    put('.', KeyEvent.KEYCODE_PERIOD)
    put('/', KeyEvent.KEYCODE_SLASH)
    put('`', KeyEvent.KEYCODE_GRAVE)
}

/** Shifted symbol → the unshifted key it lives on, US layout. */
private val shifted: Map<Char, Char> = mapOf(
    '!' to '1', '@' to '2', '#' to '3', '$' to '4', '%' to '5', '^' to '6', '&' to '7', '*' to '8',
    '(' to '9', ')' to '0', '_' to '-', '+' to '=', '{' to '[', '}' to ']', '|' to '\\', ':' to ';',
    '"' to '\'', '<' to ',', '>' to '.', '?' to '/', '~' to '`',
)

/**
 * The key stroke that types [char] on a US keyboard, or null for characters with no key
 * (accents, emoji). Uppercase letters carry Shift.
 */
fun keyStrokeFor(char: Char): KeyStroke? {
    plain[char]?.let { return KeyStroke(it) }
    if (char in 'A'..'Z') return KeyStroke(KeyEvent.KEYCODE_A + (char - 'A'), SHIFT)
    shifted[char]?.let { base -> return KeyStroke(plain.getValue(base), SHIFT) }
    return null
}

/** The stroke for a one-character string, or null. */
fun keyStrokeFor(text: String): KeyStroke? = text.singleOrNull()?.let { keyStrokeFor(it) }
