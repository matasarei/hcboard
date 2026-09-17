package net.matasar.keyboard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DarkThemeTest {

    /** A dynamic dark scheme as One UI fills it: a board lighter than its keys. */
    private val oneUi = KeyboardColors(
        background = Color(0xFF3A3A3C), toolbar = Color(0xFF3A3A3C), key = Color(0xFF252527),
        onKey = Color(0xFFE3E2E6), functionKey = Color(0xFF4E4D52), onFunctionKey = Color(0xFFC6C6CA),
        pressedKey = Color(0xFF5A5A5E), action = Color(0xFF3366FF), onAction = Color(0xFF001133),
        armed = Color(0xFF284777), onArmed = Color(0xFFD6E3FF), armedRing = Color(0xFFAAC7FF),
        locked = Color(0xFFAAC7FF), onLocked = Color(0xFF0A305F), popup = Color(0xFF23262F),
        onPopup = Color(0xFFF0F0F0), icon = Color(0xFF8E9099), chip = Color(0xFF284778),
        onChip = Color(0xFFD6E3FE), subtle = Color(0xFF8E9098), legend = Color(0x8C8E9099),
        keyShadow = Color(0x80000000),
    )
    private val systemBackground = Color(0xFF171719)
    private val systemText = Color(0xFFE3E2E6)

    @Test
    fun `the board is the system's own dark background`() {
        val dark = oneUi.darkSurfaces(systemBackground, systemText)
        assertEquals(systemBackground, dark.background)
        assertEquals(systemBackground, dark.toolbar)
    }

    @Test
    fun `each surface is lighter than the one it sits on, whatever the scheme's containers say`() {
        val dark = oneUi.darkSurfaces(systemBackground, systemText)
        val order = listOf(dark.background, dark.functionKey, dark.key, dark.popup, dark.pressedKey)
        order.zipWithNext().forEach { (below, above) ->
            assertTrue(above.luminance() > below.luminance(), "$above should be lighter than $below")
        }
        // Still a dark keyboard: a letter key stays far below mid grey.
        assertTrue(dark.key.luminance() < 0.05f, "${dark.key}")
    }

    @Test
    fun `labels and accents stay the scheme's`() {
        val dark = oneUi.darkSurfaces(systemBackground, systemText)
        assertEquals(oneUi.onKey, dark.onKey)
        assertEquals(oneUi.action, dark.action)
        assertEquals(oneUi.armed, dark.armed)
        assertEquals(oneUi.chip, dark.chip)
        assertEquals(oneUi.legend, dark.legend)
    }
}
