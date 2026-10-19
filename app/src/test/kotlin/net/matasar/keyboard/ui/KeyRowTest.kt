package net.matasar.keyboard.ui

import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.LettersLayer
import net.matasar.keyboard.layout.Row
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyRowTest {

    private val unit = 36.6f
    private val gap = 5f

    /** Each key's right edge, the way a Compose Row shares out weights: what the gaps leave, by weight. */
    private fun rightEdges(row: Row, units: Float): List<Float> {
        val width = units * (unit + gap) - gap - (unit + gap) * (row.leadingUnits + row.trailingUnits)
        val spans = row.keys.map { keySpan(it.width, unit, gap) }
        val share = (width - gap * (row.keys.size - 1)) / spans.sum()
        var x = (unit + gap) * row.leadingUnits
        return spans.map { span -> (x + span * share).also { x = it + gap } }
    }

    @Test
    fun `the bottom row's keys end where the Shift row's do above them`() {
        for (withGlobe in listOf(false, true)) {
            val layer = Languages.english.lettersLayer(withGlobe)
            val shiftRow = rightEdges(layer.rows[2], layer.units)
            val bottom = rightEdges(layer.rows[3], layer.units)
            // Shift, then z x c v b n m, then backspace: ?123 ends with Shift and return with backspace.
            assertEquals(shiftRow.first(), bottom.first(), 0.01f, "globe=$withGlobe")
            assertEquals(shiftRow.last(), bottom.last(), 0.01f, "globe=$withGlobe")
            // The period ends with m; with the globe, the globe ends with z and the comma with x.
            assertEquals(shiftRow[7], bottom[bottom.size - 2], 0.01f, "globe=$withGlobe")
            if (withGlobe) {
                assertEquals(shiftRow[1], bottom[1], 0.01f)
                assertEquals(shiftRow[2], bottom[2], 0.01f)
            }
        }
    }

    @Test
    fun `a key of one unit is one unit wide, and two units cover the gap between them`() {
        assertEquals(unit, keySpan(1f, unit, gap), 0.001f)
        assertEquals(2 * unit + gap, keySpan(2f, unit, gap), 0.001f)
        assertEquals(1.5f * unit + 0.5f * gap, keySpan(1.5f, unit, gap), 0.001f)
    }

    @Test
    fun `the home row still sits half a key in from the top row`() {
        val top = rightEdges(LettersLayer.rows[0], LettersLayer.units)
        val home = rightEdges(LettersLayer.rows[1], LettersLayer.units)
        assertEquals(top[0] + (unit + gap) / 2, home[0], 0.01f)
    }

    @Test
    fun `a span is never zero or less, before the board has a width`() {
        assertEquals(0.01f, keySpan(1f, 0f, gap))
    }
}
