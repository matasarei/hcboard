package net.matasar.keyboard.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SplitLayoutTest {

    private val everyBoard = Languages.all.flatMap { language -> listOf(false, true).map { language to it } }

    /** Every split page: letters and symbols, every language, with and without the globe. */
    private val everySplit = everyBoard.flatMap { (language, withGlobe) ->
        wideLayout(language, withGlobe).splits.values.map { "${language.tag} globe=$withGlobe ${it.left.id}" to it }
    }

    private fun Key.widthless() = copy(width = 0f)

    private fun Row.widths() = keys.joinToString(" ") { "${it.label}=${it.width}" }

    @Test
    fun `the upper rows are the whole board cut in two, every inner key at its own width`() {
        for ((language, withGlobe) in everyBoard) {
            val whole = sixtyPercentLayer(language, withGlobe)
            val split = splitLayer(language, withGlobe)
            for (index in 0..3) {
                val left = split.left.rows[index].keys
                val right = split.right.rows[index].keys
                val halves = left + right
                val tag = "${language.tag} globe=$withGlobe row $index"
                assertEquals(whole.rows[index].keys.map { it.widthless() }, halves.map { it.widthless() }, tag)
                // Only the outer keys and `~ change width.
                val inner = left.drop(1) + right.dropLast(1)
                val wholeInner = whole.rows[index].keys.let { it.subList(1, it.size - 1) }
                for ((key, original) in inner.zip(wholeInner)) {
                    if (key.action != KeyAction.Text("`", "~")) assertEquals(original.width, key.width, "$tag ${key.label}")
                }
                // Esc, 1 to 6 on the digits; the row's first key and five letters below.
                assertEquals(if (index == 0) 7 else 6, split.left.rows[index].keys.size, "${language.tag} row $index")
            }
        }
    }

    @Test
    fun `every row of a half fills the half with no padding, its outer function key taking the slack`() {
        for ((tag, split) in everySplit) {
            for ((half, rows, units) in listOf(Triple("left", split.left.rows, split.left.units), Triple("right", split.right.rows, split.right.units))) {
                for (row in rows) {
                    assertEquals(units, row.totalUnits, "$tag $half ${row.widths()}")
                    assertEquals(0f, row.leadingUnits, "$tag $half")
                    assertEquals(0f, row.trailingUnits, "$tag $half")
                    assertTrue(row.keys.all { it.width >= 0.75f }, "$tag $half ${row.widths()}")
                }
                // A letter or a digit is never stretched: only function keys stand on the outer edge.
                val outer = rows.take(4).map { if (half == "left") it.keys.first() else it.keys.last() }
                assertTrue(outer.none { it.style == KeyStyle.LETTER }, "$tag $half ${outer.map { it.label }}")
            }
        }
    }

    @Test
    fun `the backtick is a full key on every right half, and the right shift never under one unit`() {
        for ((tag, split) in everySplit) {
            val shiftRow = split.right.rows[3]
            assertEquals(1f, shiftRow.keys.single { it.action == KeyAction.Text("`", "~") }.width, tag)
            assertTrue(shiftRow.keys.last().width >= 1f, "$tag ${shiftRow.widths()}")
        }
    }

    @Test
    fun `english halves cut the board after 6, t, g and v`() {
        val split = splitLayer(Languages.english, withGlobe = true)
        assertEquals(7.25f, split.left.units)
        // The digits no longer set the right half's width: the Y row does, half a key narrower.
        assertEquals(8f, split.right.units)
        assertEquals("Esc 1 2 3 4 5 6", split.left.rows[0].keys.joinToString(" ") { it.label })
        assertEquals("7 8 9 0 - = backspace", split.right.rows[0].keys.joinToString(" ") { it.label })
        assertEquals("y u i o p [ ] €±", split.right.rows[1].keys.joinToString(" ") { it.label })
        assertEquals("h j k l ; ' enter", split.right.rows[2].keys.joinToString(" ") { it.label.lowercase() })
        // B goes to the right hand: `\|` beside the left Shift takes its place on the left.
        assertEquals("Shift \\ z x c v", split.left.rows[3].keys.joinToString(" ") { it.label })
        assertEquals("b n m / ` Shift", split.right.rows[3].keys.joinToString(" ") { it.label })
        // The outer keys take the slack, so both edges are straight.
        assertEquals("Esc=1.25", split.left.rows[0].keys.first().let { "${it.label}=${it.width}" })
        assertEquals(listOf(1.25f, 2.25f, 2.25f, 2.25f), split.left.rows.take(4).map { it.keys.first().width })
        assertEquals(listOf(2f, 1f, 2f, 3f), split.right.rows.take(4).map { it.keys.last().width })
    }

    @Test
    fun `a shift row too long for its half is refused rather than drawn with a sliver of a shift`() {
        // Six letters on the right of a one-unit Shift: the row sets the half's width, and the
        // full-size `~ would leave that Shift three quarters of a unit.
        val whole = sixtyPercentLayer(Languages.english, withGlobe = false)
        val shiftRow = whole.rows[3].keys
        val crowded = shiftRow.take(6) + "qwerty".map { Key(it.toString(), KeyAction.Text(it.toString())) } +
            shiftRow.subList(shiftRow.size - 3, shiftRow.size - 1) + shiftRow.last().copy(width = 1f)
        val rows = whole.rows.toMutableList().also { it[3] = Row(crowded) }
        assertFailsWith<IllegalArgumentException> { splitLayer(whole.copy(rows = rows)) }
    }

    @Test
    fun `nine letters on the shift row narrow the right shift to one unit`() {
        val split = splitLayer(Languages.russian, withGlobe = true)
        assertEquals(7f, split.left.units)
        assertEquals(8f, split.right.units)
        assertEquals(listOf(1f, 2f, 2f, 2f), split.left.rows.take(4).map { it.keys.first().width })
        assertEquals(listOf(2f, 1f, 2f, 1f), split.right.rows.take(4).map { it.keys.last().width })
        assertEquals("и т ь б ю / ` Shift", split.right.rows[3].keys.joinToString(" ") { it.label })
    }

    @Test
    fun `eight letters on the shift row leave the right shift two units`() {
        val split = splitLayer(Languages.bulgarian, withGlobe = true)
        assertEquals(7f, split.left.units)
        assertEquals(listOf(1f, 2f, 2f, 2f), split.left.rows.take(4).map { it.keys.first().width })
        assertEquals(listOf(2f, 1f, 2f, 2f), split.right.rows.take(4).map { it.keys.last().width })
    }

    @Test
    fun `each thumb gets a space and the language is named on the right one`() {
        for ((language, withGlobe) in everyBoard) {
            val split = splitLayer(language, withGlobe)
            val leftSpace = split.left.rows[4].keys.single { it.action == KeyAction.Space }
            val rightSpace = split.right.rows[4].keys.single { it.action == KeyAction.Space }
            assertEquals(language.nativeName, rightSpace.label)
            assertTrue(leftSpace.id != rightSpace.id)
            // The left thumb has Fn, the globe (or Meta) and the comma; the right one the period, Alt and Ctrl.
            val left = split.left.rows[4].keys.map { it.label }
            val right = split.right.rows[4].keys.map { it.label }
            assertEquals(listOf("Fn", if (withGlobe) "globe" else "Meta", ",", ""), left, language.tag)
            assertEquals(listOf(language.nativeName, ".", "Alt", "Ctrl"), right, language.tag)
        }
    }

    @Test
    fun `no key but space appears twice, and nothing of the whole board is lost`() {
        for ((language, withGlobe) in everyBoard) {
            val whole = sixtyPercentLayer(language, withGlobe).rows.flatMap { it.keys }.filter { it.action != KeyAction.Space }
            val halves = splitLayer(language, withGlobe).let { it.left.rows + it.right.rows }.flatMap { it.keys }.filter { it.action != KeyAction.Space }
            assertEquals(whole.map { it.action }.sortedBy { it.toString() }, halves.map { it.action }.sortedBy { it.toString() }, language.tag)
        }
    }

    @Test
    fun `the wide layout carries a split for each page`() {
        val layout = wideLayout(Languages.ukrainian, withGlobe = true)
        assertEquals(setOf(LayerId.LETTERS, LayerId.SYMBOLS), layout.splits.keys)
        val symbols = assertNotNull(layout.splits[LayerId.SYMBOLS])
        assertEquals(LayerId.SYMBOLS, symbols.left.id)
        // The symbols page has the letters page's geometry, so its halves are as wide.
        val letters = assertNotNull(layout.splits[LayerId.LETTERS])
        assertEquals(letters.left.units, symbols.left.units)
        assertEquals(letters.right.units, symbols.right.units)
        assertEquals("ABC", symbols.right.rows[1].keys.last().label)
    }
}
