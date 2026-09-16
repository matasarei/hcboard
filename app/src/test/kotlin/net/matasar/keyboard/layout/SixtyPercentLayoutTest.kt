package net.matasar.keyboard.layout

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SixtyPercentLayoutTest {

    @Test
    fun `every row adds up to fifteen units`() {
        for ((index, row) in SixtyPercentLayer.rows.withIndex()) {
            assertEquals(15f, row.totalUnits, "row $index")
        }
    }

    @Test
    fun `digits and punctuation carry their shifted symbol`() {
        val digits = SixtyPercentLayer.rows[0].keys.filter { it.label.length == 1 && it.label[0].isDigit() }
        assertEquals("!@#$%^&*()".map { it.toString() }, digits.map { it.shiftedLabel })
        val slash = SixtyPercentLayer.rows[3].keys.first { it.label == "/" }
        assertEquals(KeyAction.Text("/", "?"), slash.action)
    }

    @Test
    fun `fn turns the digit row into F1 to F12 and IJKL into arrows`() {
        val row = SixtyPercentLayer.rows[0].keys
        assertEquals(KeyAction.KeyCode(KeyEvent.KEYCODE_F1), row.first { it.label == "1" }.fnAction)
        assertEquals(KeyAction.KeyCode(KeyEvent.KEYCODE_F12), row.first { it.label == "=" }.fnAction)
        val letters = SixtyPercentLayer.rows.flatMap { it.keys }
        assertEquals(KeyAction.KeyCode(KeyEvent.KEYCODE_DPAD_UP), letters.first { it.label == "i" }.fnAction)
        assertEquals(KeyAction.KeyCode(KeyEvent.KEYCODE_MOVE_HOME), letters.first { it.label == "u" }.fnAction)
        assertNotNull(letters.first { it.label == "i" }.fnLegend)
    }

    @Test
    fun `modifiers sit on the bottom row`() {
        val bottom = SixtyPercentLayer.rows[4].keys.map { it.action }
        assertTrue(KeyAction.Modifier(ModifierKey.CTRL) in bottom)
        assertTrue(KeyAction.Modifier(ModifierKey.META) in bottom)
        assertTrue(KeyAction.Modifier(ModifierKey.FN) in bottom)
        assertEquals(KeyAction.HideKeyboard, bottom.last())
    }
}
