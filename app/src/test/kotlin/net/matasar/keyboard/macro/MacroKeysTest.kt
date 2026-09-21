package net.matasar.keyboard.macro

import android.view.KeyEvent
import net.matasar.keyboard.input.KeyStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MacroKeysTest {

    @Test
    fun `special keys resolve by name`() {
        assertEquals(KeyStroke(KeyEvent.KEYCODE_ESCAPE), MacroKeys.strokeFor("Esc"))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_F1), MacroKeys.strokeFor("F1"))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_F12), MacroKeys.strokeFor("F12"))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_SHIFT_LEFT), MacroKeys.strokeFor("Shift"))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_FORWARD_DEL), MacroKeys.strokeFor("Delete"))
    }

    @Test
    fun `every named key resolves and names are unique`() {
        for (key in MacroKeys.named) assertNotNull(MacroKeys.strokeFor(key.name), key.name)
        assertEquals(MacroKeys.named.size, MacroKeys.named.map { it.name }.toSet().size)
    }

    @Test
    fun `a single character is its US key`() {
        assertEquals(KeyStroke(KeyEvent.KEYCODE_A), MacroKeys.strokeFor("a"))
        assertEquals(KeyStroke(KeyEvent.KEYCODE_SLASH), MacroKeys.strokeFor("/"))
        assertEquals(KeyEvent.KEYCODE_Z, MacroKeys.strokeFor("Z")?.keyCode)
    }

    @Test
    fun `anything else has no stroke`() {
        assertNull(MacroKeys.strokeFor("Foo"))
        assertNull(MacroKeys.strokeFor("é"))
        assertNull(MacroKeys.strokeFor(""))
    }
}
