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

    @Test
    fun `a reported inset wins, gesture navigation falls back to the system height, buttons never do`() {
        assertEquals(64, bottomBarHeight(reportedPx = 64, gestureNavigation = true, systemBarHeightPx = 96))
        assertEquals(96, bottomBarHeight(reportedPx = 0, gestureNavigation = true, systemBarHeightPx = 96))
        assertEquals(0, bottomBarHeight(reportedPx = 0, gestureNavigation = false, systemBarHeightPx = 96))
        assertEquals(0, bottomBarHeight(reportedPx = 0, gestureNavigation = true, systemBarHeightPx = -1))
    }

    @Test
    fun `the reported bar is the taller of the navigation bar and the tappable zone, else the gesture area`() {
        assertEquals(126, reportedBottomBar(navigationBarPx = 39, tappablePx = 126, gestureAreaPx = 126)) // Galaxy Z Fold6
        assertEquals(96, reportedBottomBar(navigationBarPx = 64, tappablePx = 96, gestureAreaPx = 96)) // stock gesture navigation
        assertEquals(63, reportedBottomBar(navigationBarPx = 63, tappablePx = 0, gestureAreaPx = 0))
        assertEquals(96, reportedBottomBar(navigationBarPx = 0, tappablePx = 0, gestureAreaPx = 96))
    }

    @Test
    fun `side insets clear only what the decor did not already pad`() {
        assertEquals(0, sideInsetOverlap(requiredPx = 80, spacePx = 80))
        assertEquals(0, sideInsetOverlap(requiredPx = 80, spacePx = 100))
        assertEquals(80, sideInsetOverlap(requiredPx = 80, spacePx = 0))
        assertEquals(20, sideInsetOverlap(requiredPx = 80, spacePx = 60))
        assertEquals(0, sideInsetOverlap(requiredPx = 0, spacePx = 0))
    }
}
