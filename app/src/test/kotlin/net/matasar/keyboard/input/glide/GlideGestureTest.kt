package net.matasar.keyboard.input.glide

import kotlin.test.Test
import kotlin.test.assertEquals

class GlideGestureTest {

    private fun gesture() = GlideGesture(startKey = 'h', keyWidthPx = 40f, longPressMs = 400, downTimeMs = 1000, startX = 100f, startY = 100f)

    @Test
    fun `a short press that stays on its key is neither a glide nor abandoned`() {
        val g = gesture()
        assertEquals(GlideGesture.State.PENDING, g.add(102f, 101f, 1050, 'h'))
        assertEquals(GlideGesture.State.PENDING, g.add(104f, 101f, 1100, 'h'))
    }

    @Test
    fun `a press held still past the long-press timeout is abandoned for good`() {
        val g = gesture()
        assertEquals(GlideGesture.State.ABANDONED, g.add(101f, 100f, 1500, 'h'))
        // Sliding afterwards (choosing an accent) never turns it into a glide.
        assertEquals(GlideGesture.State.ABANDONED, g.add(200f, 100f, 1600, 'j'))
    }

    @Test
    fun `travelling most of a key width onto another letter starts a glide and keeps the path`() {
        val g = gesture()
        assertEquals(GlideGesture.State.PENDING, g.add(120f, 100f, 1050, 'h'))
        assertEquals(GlideGesture.State.PENDING, g.add(125f, 100f, 1060, 'j')) // not far enough yet
        assertEquals(GlideGesture.State.GLIDING, g.add(140f, 100f, 1080, 'j'))
        g.add(180f, 100f, 1100, 'k')
        assertEquals(5, g.path.size)
        assertEquals(GlidePoint(100f, 100f), g.path.first())
        assertEquals(GlidePoint(180f, 100f), g.path.last())
    }

    @Test
    fun `travel over no key or back over the start key does not start a glide`() {
        val g = gesture()
        assertEquals(GlideGesture.State.PENDING, g.add(160f, 100f, 1050, null))
        assertEquals(GlideGesture.State.PENDING, g.add(160f, 100f, 1060, 'h'))
    }

    @Test
    fun `a finger crossing the split's gap when the long-press timeout passes is still gliding`() {
        val g = gesture()
        // Off the start key into the gap between the halves: no key under the finger...
        assertEquals(GlideGesture.State.PENDING, g.add(160f, 100f, 1200, null))
        // ...still there when the long-press timeout passes: it has moved, so it is no long press.
        assertEquals(GlideGesture.State.PENDING, g.add(200f, 100f, 1450, null))
        assertEquals(GlideGesture.State.GLIDING, g.add(240f, 100f, 1500, 'k'))
    }
}
