package net.matasar.keyboard.ui

import net.matasar.keyboard.settings.SplitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitGeometryTest {

    @Test
    fun `off never splits and always splits every wide board`() {
        for (hinge in listOf(false, true)) for (landscape in listOf(false, true)) {
            assertFalse(shouldSplit(SplitMode.OFF, wide = true, hingeSeparating = hinge, phoneLandscape = landscape))
            assertTrue(shouldSplit(SplitMode.ALWAYS, wide = true, hingeSeparating = hinge, phoneLandscape = landscape))
        }
    }

    @Test
    fun `auto splits for a hinge or a sideways phone only`() {
        assertTrue(shouldSplit(SplitMode.AUTO, wide = true, hingeSeparating = true, phoneLandscape = false))
        assertTrue(shouldSplit(SplitMode.AUTO, wide = true, hingeSeparating = false, phoneLandscape = true))
        assertFalse(shouldSplit(SplitMode.AUTO, wide = true, hingeSeparating = false, phoneLandscape = false))
    }

    @Test
    fun `a narrow board never splits, whatever the mode`() {
        for (mode in SplitMode.entries) {
            assertFalse(shouldSplit(mode, wide = false, hingeSeparating = true, phoneLandscape = true))
        }
    }

    @Test
    fun `a phone on its side is landscape and short, a flat fold or a tablet is not`() {
        assertTrue(isPhoneLandscape(915, 412))
        assertFalse(isPhoneLandscape(412, 915))
        assertFalse(isPhoneLandscape(841, 701)) // an unfolded Fold
        assertFalse(isPhoneLandscape(1280, 800)) // a tablet
    }

    @Test
    fun `without a hinge the halves and the gap fill the width`() {
        val unit = splitUnit(width = 840f, sidePadding = 8f, gap = 5f, leftUnits = 7.75f, rightUnits = 8f, hinge = null, hingeMargin = 12f, gapUnits = 2f)
        val left = halfWidth(7.75f, unit, 5f)
        val right = halfWidth(8f, unit, 5f)
        val centre = 840f - 16f - left - right
        assertEquals(halfWidth(2f, unit, 5f) + 2 * 5f, centre, 0.01f)
        assertTrue(unit > 0f)
    }

    @Test
    fun `with a hinge no key comes within the margin of it`() {
        for (hinge in listOf(540f..560f, 400f..420f, 700f..710f)) {
            val unit = splitUnit(width = 1100f, sidePadding = 8f, gap = 5f, leftUnits = 7.75f, rightUnits = 8f, hinge = hinge, hingeMargin = 12f, gapUnits = 2f)
            val leftEnd = 8f + halfWidth(7.75f, unit, 5f)
            val rightStart = 1100f - 8f - halfWidth(8f, unit, 5f)
            assertTrue(leftEnd <= hinge.start - 12f + 0.01f, "hinge $hinge: left half ends at $leftEnd")
            assertTrue(rightStart >= hinge.endInclusive + 12f - 0.01f, "hinge $hinge: right half starts at $rightStart")
        }
    }

    @Test
    fun `the tighter side sets the size, and a hinge at an edge leaves no room rather than a negative unit`() {
        val centred = splitUnit(1100f, 8f, 5f, 7.75f, 8f, 540f..560f, 12f, 2f)
        val offCentre = splitUnit(1100f, 8f, 5f, 7.75f, 8f, 300f..320f, 12f, 2f)
        assertTrue(offCentre < centred)
        assertEquals(0f, splitUnit(1100f, 8f, 5f, 7.75f, 8f, 0f..10f, 12f, 2f))
    }
}
