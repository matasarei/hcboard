package net.matasar.keyboard.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LatchTest {

    @Test
    fun `tap arms, second quick tap locks, tap on locked releases`() {
        val armed = Latch().tap(1000)
        assertEquals(LatchState.ARMED, armed.state)
        val locked = armed.tap(1200)
        assertEquals(LatchState.LOCKED, locked.state)
        assertEquals(LatchState.IDLE, locked.tap(5000).state)
    }

    @Test
    fun `a slow second tap cancels instead of locking`() {
        val armed = Latch().tap(1000)
        assertEquals(LatchState.IDLE, armed.tap(1000 + Latch.DOUBLE_TAP_WINDOW_MS + 1).state)
    }

    @Test
    fun `sending a key consumes an armed latch but not a locked one`() {
        assertEquals(LatchState.IDLE, Latch(LatchState.ARMED).consume().state)
        assertEquals(LatchState.LOCKED, Latch(LatchState.LOCKED).consume().state)
        assertEquals(LatchState.IDLE, Latch(LatchState.IDLE).consume().state)
    }

    @Test
    fun `long press locks from any state`() {
        assertEquals(LatchState.LOCKED, Latch().longPress().state)
        assertEquals(LatchState.LOCKED, Latch(LatchState.ARMED).longPress().state)
    }

    @Test
    fun `active means anything but idle`() {
        assertFalse(Latch().active)
        assertTrue(Latch(LatchState.ARMED).active)
        assertTrue(Latch(LatchState.LOCKED).active)
    }
}
