package net.matasar.keyboard.input

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackpadGestureTest {

    @Test
    fun `whole steps come out as the finger travels`() {
        val gesture = TrackpadGesture(stepPx = 20f)
        assertEquals(2, gesture.move(45f))
        assertEquals(0, gesture.move(10f))
        assertEquals(1, gesture.move(5f))
    }

    @Test
    fun `travel to the left moves the cursor back`() {
        val gesture = TrackpadGesture(stepPx = 20f)
        assertEquals(-1, gesture.move(-25f))
        assertEquals(-1, gesture.move(-15f))
    }

    @Test
    fun `reset drops the remainder`() {
        val gesture = TrackpadGesture(stepPx = 20f)
        gesture.move(15f)
        gesture.reset()
        assertEquals(0, gesture.move(10f))
    }
}
