package net.matasar.keyboard.autofill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineSuggestionsRequestTest {

    @Test
    fun `chips are 32dp tall and between 48dp and sixty percent of the screen wide`() {
        val sizes = inlineSizes(screenWidthPx = 1080, density = 2.625f)
        assertEquals(84, sizes.height)
        assertEquals(126, sizes.minWidth)
        assertEquals(648, sizes.maxWidth)
    }

    @Test
    fun `a tiny screen still allows the minimum width`() {
        val sizes = inlineSizes(screenWidthPx = 100, density = 2f)
        assertTrue(sizes.maxWidth >= sizes.minWidth)
    }
}
