package net.matasar.keyboard.layout

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `every language fills the board to fifteen units with and without the globe`() {
        for (language in Languages.all) {
            for (withGlobe in listOf(false, true)) {
                val layer = sixtyPercentLayer(language, withGlobe)
                for ((index, row) in layer.rows.withIndex()) {
                    assertEquals(15f, row.totalUnits, "${language.tag} row $index globe=$withGlobe")
                }
                assertEquals(withGlobe, layer.rows[4].keys.any { it.label == "globe" }, "${language.tag} globe=$withGlobe")
                assertEquals(language.nativeName, layer.rows[4].keys.first { it.action == KeyAction.Space }.label)
            }
        }
    }

    @Test
    fun `ukrainian takes the punctuation slots and keeps their symbols on fn`() {
        val layer = sixtyPercentLayer(Languages.ukrainian, withGlobe = false)
        val keys = layer.rows.flatMap { it.keys }
        assertEquals("Tab й ц у к е н г ш щ з х ї \\", layer.rows[1].keys.joinToString(" ") { it.label })
        assertEquals(KeyAction.Text("[", "{"), keys.first { it.label == "х" }.fnAction)
        assertEquals("]", keys.first { it.label == "ї" }.fnLegend)
        assertEquals(']', keys.first { it.label == "ї" }.slot)
        assertEquals("Caps ф і в а п р о л д ж є enter", layer.rows[2].keys.joinToString(" ") { it.label })
        assertEquals(KeyAction.Text("'", "\""), keys.first { it.label == "є" }.fnAction)
        val shiftRow = layer.rows[3].keys
        assertEquals("Shift я ч с м и т ь б ю . / Shift", shiftRow.joinToString(" ") { it.label })
        assertEquals(listOf(2.25f, 1.75f), listOf(shiftRow.first().width, shiftRow.last().width))
        assertEquals(KeyAction.Text(".", ","), shiftRow.first { it.label == "." }.action)
        assertEquals(KeyAction.Text("<", ">"), shiftRow.first { it.label == "." }.fnAction)
        assertEquals(KeyAction.Text("/", "?"), shiftRow.first { it.label == "/" }.action)
    }

    @Test
    fun `fn navigation stays on the slots of i j k l for every alphabet`() {
        val keys = sixtyPercentLayer(Languages.ukrainian, withGlobe = false).rows.flatMap { it.keys }
        assertEquals(KeyAction.KeyCode(KeyEvent.KEYCODE_DPAD_UP), keys.first { it.label == "ш" }.fnAction)
        assertEquals(KeyAction.KeyCode(KeyEvent.KEYCODE_DPAD_RIGHT), keys.first { it.label == "д" }.fnAction)
        assertEquals(KeyAction.KeyCode(KeyEvent.KEYCODE_MOVE_HOME), keys.first { it.label == "г" }.fnAction)
        assertEquals(KeyAction.KeyCode(KeyEvent.KEYCODE_PAGE_DOWN), keys.first { it.label == "т" }.fnAction)
    }

    @Test
    fun `german keeps the closing bracket and puts the displaced apostrophe on fn`() {
        val layer = sixtyPercentLayer(Languages.german, withGlobe = false)
        assertEquals("Tab q w e r t z u i o p ü ] \\", layer.rows[1].keys.joinToString(" ") { it.label })
        assertTrue(layer.rows[2].keys.none { it.label == ";" || it.label == "'" })
        assertEquals(KeyAction.Text("'", "\""), layer.rows[2].keys.first { it.label == "ä" }.fnAction)
        assertEquals(KeyAction.Text(";", ":"), layer.rows[2].keys.first { it.label == "ö" }.fnAction)
        assertEquals("ß", layer.rows[2].keys.first { it.label == "s" }.longPress.first())
    }

    @Test
    fun `english renders the standard board and its letters carry their own slot`() {
        assertEquals("Shift z x c v b n m , . / Shift", SixtyPercentLayer.rows[3].keys.joinToString(" ") { it.label })
        assertEquals(listOf(2.75f, 2.25f), listOf(SixtyPercentLayer.rows[3].keys.first().width, SixtyPercentLayer.rows[3].keys.last().width))
        assertEquals('c', SixtyPercentLayer.rows[3].keys.first { it.label == "c" }.slot)
        assertEquals(null, SixtyPercentLayer.rows[3].keys.first { it.label == "/" }.slot)
    }

    @Test
    fun `the globe sits left of space and space shrinks to make room`() {
        val with = sixtyPercentLayer(Languages.english, withGlobe = true).rows[4].keys
        assertEquals(KeyAction.SwitchLanguage, with[3].action)
        assertEquals(KeyIcon.GLOBE, with[3].icon)
        assertEquals(KeyAction.Space, with[4].action)
        assertEquals(5f, with[4].width)
        assertEquals(6.25f, SixtyPercentLayer.rows[4].keys.first { it.action == KeyAction.Space }.width)
        assertEquals(KeyAction.SwitchLanguage, with[4].fnAction)
    }

    @Test
    fun `a row longer than its slots or a shift row over nine letters is refused`() {
        val thirteenOnTop = Languages.english.copy(rows = listOf("qwertyuiopasd", "asdfghjkl", "zxcvbnm"))
        assertFailsWith<IllegalArgumentException> { sixtyPercentLayer(thirteenOnTop, withGlobe = false) }
        val tenOnShiftRow = Languages.english.copy(rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnmqwe"))
        assertFailsWith<IllegalArgumentException> { sixtyPercentLayer(tenOnShiftRow, withGlobe = false) }
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
