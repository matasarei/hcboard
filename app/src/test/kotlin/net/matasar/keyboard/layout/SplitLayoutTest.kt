package net.matasar.keyboard.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SplitLayoutTest {

    private val everyBoard = Languages.all.flatMap { language -> listOf(false, true).map { language to it } }

    @Test
    fun `the upper rows are the whole board cut in two, every key at its own width`() {
        for ((language, withGlobe) in everyBoard) {
            val whole = sixtyPercentLayer(language, withGlobe)
            val split = splitLayer(language, withGlobe)
            for (index in 0..3) {
                val halves = split.left.rows[index].keys + split.right.rows[index].keys
                assertEquals(whole.rows[index].keys, halves, "${language.tag} globe=$withGlobe row $index")
                // Esc, 1 to 6 on the digits; the row's first key and five letters below.
                assertEquals(if (index == 0) 7 else 6, split.left.rows[index].keys.size, "${language.tag} row $index")
            }
        }
    }

    @Test
    fun `every row of a half fills the half, padded on the inner side`() {
        for ((language, withGlobe) in everyBoard) {
            val split = splitLayer(language, withGlobe)
            for (row in split.left.rows) {
                assertEquals(split.left.units, row.totalUnits, "${language.tag} left")
                assertEquals(0f, row.leadingUnits)
            }
            for (row in split.right.rows) {
                assertEquals(split.right.units, row.totalUnits, "${language.tag} right")
                assertEquals(0f, row.trailingUnits)
            }
            // A half is as wide as its widest letter row, so that row needs no padding at all.
            assertTrue(split.left.rows.take(4).any { it.trailingUnits == 0f }, "${language.tag} left")
            assertTrue(split.right.rows.take(4).any { it.leadingUnits == 0f }, "${language.tag} right")
        }
    }

    @Test
    fun `english halves are the balanced board's`() {
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
