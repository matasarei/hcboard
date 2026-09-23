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
        assertEquals(7.75f, split.left.units)
        // The digits no longer set the right half's width: the Y row does, half a key narrower.
        assertEquals(8f, split.right.units)
        assertEquals("Esc 1 2 3 4 5 6", split.left.rows[0].keys.joinToString(" ") { it.label })
        assertEquals("7 8 9 0 - = backspace", split.right.rows[0].keys.joinToString(" ") { it.label })
        assertEquals("y u i o p [ ] \\", split.right.rows[1].keys.joinToString(" ") { it.label })
        assertEquals("h j k l ; ' enter", split.right.rows[2].keys.joinToString(" ") { it.label.lowercase() })
    }

    @Test
    fun `each thumb gets a space and the language is named on the right one`() {
        for ((language, withGlobe) in everyBoard) {
            val split = splitLayer(language, withGlobe)
            val leftSpace = split.left.rows[4].keys.single { it.action == KeyAction.Space }
            val rightSpace = split.right.rows[4].keys.single { it.action == KeyAction.Space }
            assertEquals(language.nativeName, rightSpace.label)
            assertTrue(leftSpace.id != rightSpace.id)
            assertEquals(withGlobe, split.right.rows[4].keys.any { it.action == KeyAction.SwitchLanguage })
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
    fun `the wide layout carries its split`() {
        assertNotNull(wideLayout(Languages.ukrainian, withGlobe = true).split)
    }
}
