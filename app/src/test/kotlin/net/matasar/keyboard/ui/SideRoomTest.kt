package net.matasar.keyboard.ui

import androidx.compose.ui.unit.dp
import net.matasar.keyboard.settings.BoardSide
import kotlin.test.Test
import kotlin.test.assertEquals

class SideRoomTest {

    private val width = 400.dp

    /** Dp arithmetic is float arithmetic: 400 × 0.2 is 79.99999. */
    private fun assertRoom(start: Float, end: Float, room: SideRoom, what: String = "") {
        assertEquals(start, room.start.value, 0.01f, "start $what")
        assertEquals(end, room.end.value, 0.01f, "end $what")
    }

    @Test
    fun `a narrowed phone board splits its room evenly in the middle`() {
        assertRoom(40f, 40f, sideRoom(width, wide = false, KeyboardFeel(widthScale = 0.8f)))
    }

    @Test
    fun `against an edge, all the room goes to the other side`() {
        assertRoom(0f, 80f, sideRoom(width, wide = false, KeyboardFeel(widthScale = 0.8f, boardSide = BoardSide.LEFT)))
        assertRoom(80f, 0f, sideRoom(width, wide = false, KeyboardFeel(widthScale = 0.8f, boardSide = BoardSide.RIGHT)))
    }

    @Test
    fun `at full width there is no room, whatever the side`() {
        for (side in BoardSide.entries) {
            assertRoom(0f, 0f, sideRoom(width, wide = false, KeyboardFeel(boardSide = side)), "$side")
        }
    }

    @Test
    fun `a cutout's inset holds on each side on its own`() {
        val feel = KeyboardFeel(widthScale = 0.9f, boardSide = BoardSide.LEFT)
        assertRoom(24f, 40f, sideRoom(width, wide = false, feel, inset = 24.dp))
        assertRoom(48f, 48f, sideRoom(width, wide = false, feel, inset = 48.dp))
    }

    @Test
    fun `the wide board takes its own width and always centres`() {
        val feel = KeyboardFeel(widthScale = 0.7f, wideWidthScale = 0.9f, boardSide = BoardSide.LEFT)
        assertRoom(20f, 20f, sideRoom(width, wide = true, feel))
        assertRoom(60f, 60f, sideRoom(width, wide = false, feel.copy(boardSide = BoardSide.CENTRE)))
    }

    @Test
    fun `a scale outside the slider's range is held to it`() {
        assertRoom(60f, 60f, sideRoom(width, wide = false, KeyboardFeel(widthScale = 0.1f)))
        assertRoom(0f, 0f, sideRoom(width, wide = true, KeyboardFeel(wideWidthScale = 3f)))
    }
}
