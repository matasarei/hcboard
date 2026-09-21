package net.matasar.keyboard.ime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HingeTest {

    @Test
    fun `a hinge is measured from the keyboard's left edge`() {
        assertEquals(1080f..1140f, hingeInView(leftInWindow = 1080, rightInWindow = 1140, viewLeftInWindow = 0, viewWidth = 2220))
        assertEquals(980f..1040f, hingeInView(1080, 1140, viewLeftInWindow = 100, viewWidth = 2020))
    }

    @Test
    fun `a crease with no width is still a hinge`() {
        assertEquals(1104f..1104f, hingeInView(1104, 1104, 0, 2208))
    }

    @Test
    fun `a hinge beside the keyboard is none of its business`() {
        assertNull(hingeInView(leftInWindow = 10, rightInWindow = 40, viewLeftInWindow = 100, viewWidth = 1000))
        assertNull(hingeInView(leftInWindow = 1200, rightInWindow = 1240, viewLeftInWindow = 100, viewWidth = 1000))
    }

    @Test
    fun `the diagnostics line names the hinge or its absence, and where the answer came from`() {
        assertEquals("hinge: 1080..1140 px (window)", hingeLine(1080f..1140f, "window"))
        assertEquals("hinge: none (unavailable: IllegalArgumentException)", hingeLine(null, "unavailable: IllegalArgumentException"))
    }
}
