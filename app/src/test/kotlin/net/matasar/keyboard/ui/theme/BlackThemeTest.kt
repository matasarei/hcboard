package net.matasar.keyboard.ui.theme

import androidx.compose.ui.graphics.Color
import net.matasar.keyboard.settings.ThemeChoice
import net.matasar.keyboard.settings.asDarkTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class BlackThemeTest {

    /** A dark scheme's colours, each one distinct, so a copy shows what it kept. */
    private val dark = KeyboardColors(
        background = Color(0xFF101010), toolbar = Color(0xFF101011), key = Color(0xFF202020),
        onKey = Color(0xFFE0E0E0), functionKey = Color(0xFF303030), onFunctionKey = Color(0xFFD0D0D0),
        pressedKey = Color(0xFF404040), action = Color(0xFF3366FF), onAction = Color(0xFF001133),
        armed = Color(0xFF284777), onArmed = Color(0xFFD6E3FF), armedRing = Color(0xFFAAC7FF),
        locked = Color(0xFFAAC7FF), onLocked = Color(0xFF0A305F), popup = Color(0xFF505050),
        onPopup = Color(0xFFF0F0F0), icon = Color(0xFF8E9099), chip = Color(0xFF284778),
        onChip = Color(0xFFD6E3FE), subtle = Color(0xFF8E9098), legend = Color(0x8C8E9099),
        keyShadow = Color(0x80000000),
    )

    @Test
    fun `the black theme is Samsung's black board with the function keys and toolbar one step up`() {
        val black = dark.black()
        assertEquals(Color(0xFF000000), black.background)
        assertEquals(Color(0xFF171719), black.toolbar)
        assertEquals(Color(0xFF39393B), black.key)
        assertEquals(Color(0xFF171719), black.functionKey)
        // Samsung's Enter is a function key like the rest, not an accent.
        assertEquals(black.functionKey, black.action)
        assertEquals(black.onKey, black.onAction)
    }

    @Test
    fun `the accents stay the dark scheme's`() {
        val black = dark.black()
        assertEquals(dark.armed, black.armed)
        assertEquals(dark.armedRing, black.armedRing)
        assertEquals(dark.locked, black.locked)
        assertEquals(dark.chip, black.chip)
        assertEquals(dark.subtle, black.subtle)
        assertEquals(dark.legend, black.legend)
    }

    @Test
    fun `black is a dark theme for everything that only asks light or dark`() {
        assertEquals(true, ThemeChoice.BLACK.asDarkTheme())
        assertEquals(true, ThemeChoice.DARK.asDarkTheme())
        assertEquals(false, ThemeChoice.LIGHT.asDarkTheme())
        assertEquals(null, ThemeChoice.SYSTEM.asDarkTheme())
    }
}
