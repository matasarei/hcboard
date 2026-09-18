package net.matasar.keyboard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArrowSymbolTest {

    @Test
    fun `arrow direction resolves cardinal arrow symbols`() {
        assertEquals(ArrowDirection.UP, ArrowDirection.fromString("↑"))
        assertEquals(ArrowDirection.RIGHT, ArrowDirection.fromString("→"))
        assertEquals(ArrowDirection.DOWN, ArrowDirection.fromString("↓"))
        assertEquals(ArrowDirection.LEFT, ArrowDirection.fromString("←"))
    }

    @Test
    fun `arrow direction degrees rotate clockwise from up`() {
        assertEquals(0f, ArrowDirection.UP.degrees)
        assertEquals(90f, ArrowDirection.RIGHT.degrees)
        assertEquals(180f, ArrowDirection.DOWN.degrees)
        assertEquals(270f, ArrowDirection.LEFT.degrees)
    }

    @Test
    fun `non-arrow strings return null`() {
        assertNull(ArrowDirection.fromString(null))
        assertNull(ArrowDirection.fromString(""))
        assertNull(ArrowDirection.fromString("a"))
        assertNull(ArrowDirection.fromString("Home"))
        assertNull(ArrowDirection.fromString("End"))
        assertNull(ArrowDirection.fromString("PgUp"))
        assertNull(ArrowDirection.fromString("Del"))
        assertNull(ArrowDirection.fromString("↑↑"))
    }
}
