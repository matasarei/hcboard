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
        assertEquals("Tab й ц у к е н г ш щ з х ї €±", layer.rows[1].keys.joinToString(" ") { it.label })
        assertEquals(KeyAction.Text("[", "{"), keys.first { it.label == "х" }.fnAction)
        assertEquals("]}", keys.first { it.label == "ї" }.fnLegend)
        assertEquals("[{", keys.first { it.label == "х" }.fnLegend)
        assertEquals(']', keys.first { it.label == "ї" }.slot)
        assertEquals("Caps ф і в а п р о л д ж є enter", layer.rows[2].keys.joinToString(" ") { it.label })
        assertEquals(KeyAction.Text("'", "\""), keys.first { it.label == "є" }.fnAction)
        val shiftRow = layer.rows[3].keys
        assertEquals("Shift \\ я ч с м и т ь б ю / ` Shift", shiftRow.joinToString(" ") { it.label })
        assertEquals(listOf(2f, 1.25f), listOf(shiftRow.first().width, shiftRow.last().width))
        // The comma and period are on the bottom row; б and ю keep `,<` and `.>` on Fn.
        assertTrue(shiftRow.none { it.label == "." || it.label == "," })
        assertEquals(listOf(",<", ".>"), listOf("б", "ю").map { l -> shiftRow.first { it.label == l }.fnLegend })
        assertEquals(KeyAction.Text("/", "?"), shiftRow.first { it.label == "/" }.action)
    }

    @Test
    fun `russian fills the bracket slots the way ukrainian does`() {
        val layer = sixtyPercentLayer(Languages.russian, withGlobe = false)
        val keys = layer.rows.flatMap { it.keys }
        assertEquals("Tab й ц у к е н г ш щ з х ъ €±", layer.rows[1].keys.joinToString(" ") { it.label })
        assertEquals('[', keys.first { it.label == "х" }.slot)
        assertEquals(']', keys.first { it.label == "ъ" }.slot)
        assertEquals("[{", keys.first { it.label == "х" }.fnLegend)
        assertEquals("]}", keys.first { it.label == "ъ" }.fnLegend)
        assertEquals(KeyAction.Text("[", "{"), keys.first { it.label == "х" }.fnAction)
        assertEquals(KeyAction.Text("]", "}"), keys.first { it.label == "ъ" }.fnAction)
        // The same two keys on the Ukrainian board, so a language switch does not move the brackets.
        val ukrainian = sixtyPercentLayer(Languages.ukrainian, withGlobe = false).rows.flatMap { it.keys }
        assertEquals(
            listOf("[{", "]}"),
            listOf("х", "ї").map { l -> ukrainian.first { it.label == l }.fnLegend },
        )
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
        assertEquals("Tab q w e r t z u i o p ü ] €±", layer.rows[1].keys.joinToString(" ") { it.label })
        assertTrue(layer.rows[2].keys.none { it.label == ";" || it.label == "'" })
        assertEquals(KeyAction.Text("'", "\""), layer.rows[2].keys.first { it.label == "ä" }.fnAction)
        assertEquals(KeyAction.Text(";", ":"), layer.rows[2].keys.first { it.label == "ö" }.fnAction)
        assertEquals("ß", layer.rows[2].keys.first { it.label == "s" }.longPress.first())
    }

    @Test
    fun `bulgarian keeps the closing bracket on row 1 and puts brackets and punctuation on fn`() {
        val layer = sixtyPercentLayer(Languages.bulgarian, withGlobe = false)
        val keys = layer.rows.flatMap { it.keys }
        assertEquals("Tab я в е р т ъ у и о п ч ] €±", layer.rows[1].keys.joinToString(" ") { it.label })
        assertEquals('[', keys.first { it.label == "ч" }.slot)
        assertEquals("[{", keys.first { it.label == "ч" }.fnLegend)
        assertEquals(KeyAction.Text("[", "{"), keys.first { it.label == "ч" }.fnAction)
        assertEquals("]", layer.rows[1].keys[12].label)
        assertEquals(KeyAction.Text("]", "}"), layer.rows[1].keys[12].action)

        assertEquals("Caps а с д ф г х й к л ш щ enter", layer.rows[2].keys.joinToString(" ") { it.label })
        assertEquals(';', keys.first { it.label == "ш" }.slot)
        assertEquals('\'', keys.first { it.label == "щ" }.slot)
        assertEquals(";:", keys.first { it.label == "ш" }.fnLegend)
        assertEquals("'\"", keys.first { it.label == "щ" }.fnLegend)

        val shiftRow = layer.rows[3].keys
        assertEquals("Shift \\ з ь ц ж б н м ю / ` Shift", shiftRow.joinToString(" ") { it.label })
        assertEquals(listOf(2.5f, 1.75f), listOf(shiftRow.first().width, shiftRow.last().width))
        assertEquals(',', keys.first { it.label == "ю" }.slot)
        assertEquals(",<", keys.first { it.label == "ю" }.fnLegend)
    }

    @Test
    fun `bulgarian standard fills the slots left to right, with the nine-letter shift row`() {
        val layer = sixtyPercentLayer(Languages.bulgarianStandard, withGlobe = false)
        assertEquals("Tab у е и ш щ к с д з ц б ] €±", layer.rows[1].keys.joinToString(" ") { it.label })
        assertEquals("Shift \\ ю й ъ э ф х п р л / ` Shift", layer.rows[3].keys.joinToString(" ") { it.label })
        assertEquals('[', layer.rows[1].keys.first { it.label == "б" }.slot)
    }

    @Test
    fun `english renders the standard board and its letters carry their own slot`() {
        assertEquals("Shift \\ z x c v b n m / ` Shift", SixtyPercentLayer.rows[3].keys.joinToString(" ") { it.label })
        assertEquals(listOf(2.5f, 2.75f), listOf(SixtyPercentLayer.rows[3].keys.first().width, SixtyPercentLayer.rows[3].keys.last().width))
        assertEquals('c', SixtyPercentLayer.rows[3].keys.first { it.label == "c" }.slot)
        assertEquals(null, SixtyPercentLayer.rows[3].keys.first { it.label == "/" }.slot)
    }

    @Test
    fun `the bottom row is the phone's, with fn where the phone has 123`() {
        fun labels(withGlobe: Boolean) = sixtyPercentLayer(Languages.english, withGlobe).rows[4].keys.map { it.label }
        assertEquals(listOf("Fn", "globe", ",", "English", ".", "Alt", "Ctrl"), labels(withGlobe = true))
        assertEquals(listOf("Fn", "Meta", ",", "English", ".", "Alt", "Ctrl"), labels(withGlobe = false))
        val bottom = sixtyPercentLayer(Languages.english, withGlobe = true).rows[4].keys
        assertEquals(KeyIcon.GLOBE, bottom[1].icon)
        assertEquals(8f, bottom.first { it.action == KeyAction.Space }.width)
        assertEquals(8f, SixtyPercentLayer.rows[4].keys.first { it.action == KeyAction.Space }.width)
        assertEquals(KeyAction.Text(",", "<"), bottom[2].action)
        assertEquals(KeyAction.Text(".", ">"), bottom[4].action)
    }

    @Test
    fun `a row longer than its slots or a shift row over nine letters is refused`() {
        val thirteenOnTop = Languages.english.copy(rows = listOf("qwertyuiopasd", "asdfghjkl", "zxcvbnm"))
        assertFailsWith<IllegalArgumentException> { sixtyPercentLayer(thirteenOnTop, withGlobe = false) }
        val tenOnShiftRow = Languages.english.copy(rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnmqwe"))
        assertFailsWith<IllegalArgumentException> { sixtyPercentLayer(tenOnShiftRow, withGlobe = false) }
    }

    @Test
    fun `one of each modifier, ctrl in the corner, and meta only without the globe`() {
        for (language in Languages.all) {
            for (withGlobe in listOf(false, true)) {
                val keys = sixtyPercentLayer(language, withGlobe).rows.flatMap { it.keys }.map { it.action }
                for (modifier in listOf(ModifierKey.CTRL, ModifierKey.ALT, ModifierKey.FN)) {
                    assertEquals(1, keys.count { it == KeyAction.Modifier(modifier) }, "${language.tag} $modifier globe=$withGlobe")
                }
                assertEquals(if (withGlobe) 0 else 1, keys.count { it == KeyAction.Modifier(ModifierKey.META) }, "${language.tag} globe=$withGlobe")
                assertEquals(KeyAction.Modifier(ModifierKey.CTRL), keys.last())
            }
        }
    }

    @Test
    fun `alt carries meta on fn only with the globe`() {
        val withGlobe = sixtyPercentLayer(Languages.english, withGlobe = true).rows[4].keys.first { it.label == "Alt" }
        assertEquals(KeyAction.Modifier(ModifierKey.META), withGlobe.fnAction)
        assertEquals("Meta", withGlobe.fnLegend)
        val without = SixtyPercentLayer.rows[4].keys.first { it.label == "Alt" }
        assertEquals(null, without.fnAction)
        assertEquals(null, without.fnLegend)
    }

    @Test
    fun `the symbols key ends the top row, and backslash and backtick sit on the shift row`() {
        val top = SixtyPercentLayer.rows[1].keys
        assertEquals(KeyAction.SwitchLayer(LayerId.SYMBOLS), top.last().action)
        assertEquals("€±", top.last().label)
        val shiftRow = SixtyPercentLayer.rows[3].keys
        assertEquals(KeyAction.Text("\\", "|"), shiftRow[1].action)
        val backtick = shiftRow[shiftRow.size - 2]
        assertEquals(KeyAction.Text("`", "~"), backtick.action)
        assertEquals(0.75f, backtick.width)
        val esc = SixtyPercentLayer.rows[0].keys.first()
        assertEquals(null, esc.fnAction)
        assertEquals(null, esc.fnLegend)
    }

    @Test
    fun `every board still types the ascii punctuation`() {
        // On a key, with Shift, or on Fn where a letter took the slot.
        val wanted = "`~!@#$%^&*()-_=+[{]}\\|;:'\",<.>/?".toSet()
        for (language in Languages.all + Languages.bulgarianStandard) {
            for (withGlobe in listOf(false, true)) {
                val typed = sixtyPercentLayer(language, withGlobe).rows.flatMap { it.keys }.flatMap { key ->
                    listOf(key.action, key.fnAction).filterIsInstance<KeyAction.Text>().flatMap { listOfNotNull(it.text, it.shifted) }
                }.joinToString("").toSet()
                assertEquals(emptySet(), wanted - typed, "${language.tag} globe=$withGlobe")
            }
        }
    }

    @Test
    fun `no hide key on the wide board`() {
        for (language in Languages.all) {
            for (withGlobe in listOf(false, true)) {
                val keys = sixtyPercentLayer(language, withGlobe).rows.flatMap { it.keys }
                assertTrue(keys.none { it.action == KeyAction.HideKeyboard }, "${language.tag} globe=$withGlobe")
            }
        }
    }

    @Test
    fun `the letters split between the hands near the middle`() {
        // Where the key after the left hand's last letter starts, in units from the left edge.
        fun startOf(row: Row, label: String): Float = row.keys.takeWhile { it.label != label }.sumOf { it.width.toDouble() }.toFloat()
        assertEquals(7f, startOf(SixtyPercentLayer.rows[1], "y"))
        assertEquals(7.25f, startOf(SixtyPercentLayer.rows[2], "h"))
        // `\|` beside the left Shift moves B under the right hand.
        assertEquals(7.5f, startOf(SixtyPercentLayer.rows[3], "b"))
        assertEquals(1.5f, SixtyPercentLayer.rows[0].keys.last().width) // Backspace
        assertEquals(1.75f, SixtyPercentLayer.rows[2].keys.last().width) // Enter
    }
}
