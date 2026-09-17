package net.matasar.keyboard.ime

import kotlin.test.Test
import kotlin.test.assertEquals

class BottomBarOverlapTest {

    @Test
    fun `a decor that already pads for the bar leaves nothing to cover`() {
        assertEquals(0, bottomBarOverlap(barPx = 64, spaceBelowViewPx = 64))
    }

    @Test
    fun `a window that leaves nothing under the view needs the whole bar`() {
        assertEquals(64, bottomBarOverlap(barPx = 64, spaceBelowViewPx = 0))
    }

    @Test
    fun `more space than the bar and no bar at all both give zero, never a negative`() {
        assertEquals(0, bottomBarOverlap(barPx = 64, spaceBelowViewPx = 96))
        assertEquals(0, bottomBarOverlap(barPx = 0, spaceBelowViewPx = 0))
    }
}
