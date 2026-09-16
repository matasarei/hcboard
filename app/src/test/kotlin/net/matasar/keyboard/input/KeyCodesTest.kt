package net.matasar.keyboard.input

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyCodesTest {

    @Test
    fun `letters digits and punctuation map to their keys`() {
        assertEquals(KeyStroke(KeyEvent.KEYCODE_C), keyStrokeFor('c'))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_7), keyStrokeFor('7'))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_RIGHT_BRACKET), keyStrokeFor(']'))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_SPACE), keyStrokeFor(" "))
    }

    @Test
    fun `shifted symbols and capitals carry shift`() {
        val shift = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        assertEquals(KeyStroke(KeyEvent.KEYCODE_RIGHT_BRACKET, shift), keyStrokeFor('}'))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_BACKSLASH, shift), keyStrokeFor('|'))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_Z, shift), keyStrokeFor('Z'))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_GRAVE, shift), keyStrokeFor('~'))
    }

    @Test
    fun `characters without a key have no stroke`() {
        assertNull(keyStrokeFor('é'))
        assertNull(keyStrokeFor("ab"))
    }
}
