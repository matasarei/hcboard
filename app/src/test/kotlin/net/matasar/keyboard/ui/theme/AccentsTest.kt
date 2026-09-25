package net.matasar.keyboard.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import net.matasar.keyboard.settings.AccentColour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AccentsTest {

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `every swatch but the phone's own has tones`() {
        assertEquals(AccentColour.entries - AccentColour.SYSTEM, Accents.keys.toList())
    }

    @Test
    fun `text on each accent reads at 4_5 to 1 or better, light and dark`() {
        for ((accent, pair) in Accents) for (dark in listOf(false, true)) {
            val t = pair.of(dark)
            assertTrue(contrast(t.primary, t.onPrimary) >= 4.5f, "$accent dark=$dark: onPrimary on primary")
            assertTrue(contrast(t.primaryContainer, t.onPrimaryContainer) >= 4.5f, "$accent dark=$dark: onPrimaryContainer on primaryContainer")
        }
    }

    @Test
    fun `the phone's palette leaves the scheme as it is`() {
        val scheme = lightColorScheme()
        assertSame(scheme, scheme.withAccent(AccentColour.SYSTEM, dark = false))
    }

    @Test
    fun `a fixed accent changes the four accent roles and the chips' container, and nothing else`() {
        val scheme = darkColorScheme()
        val red = scheme.withAccent(AccentColour.RED, dark = true)
        val tones = Accents.getValue(AccentColour.RED).dark
        assertEquals(tones, AccentTones(red.primary, red.onPrimary, red.primaryContainer, red.onPrimaryContainer))
        // ColorScheme has no equals of its own; its toString lists every role.
        assertEquals(
            scheme.copy(
                primary = tones.primary, onPrimary = tones.onPrimary, primaryContainer = tones.primaryContainer, onPrimaryContainer = tones.onPrimaryContainer,
                secondaryContainer = tones.primaryContainer, onSecondaryContainer = tones.onPrimaryContainer,
            ).toString(),
            red.toString(),
        )
        assertTrue(scheme.toString() != red.toString())
    }

    @Test
    fun `on Black the accent colours the modifiers and chips, and Enter stays grey`() {
        val tones = Accents.getValue(AccentColour.GREEN).dark
        val colors = darkColorScheme().withAccent(AccentColour.GREEN, dark = true).toKeyboardColors(dark = true).black()
        assertEquals(BlackPalette.band, colors.action)
        assertEquals(tones.primary, colors.armedRing)
        assertEquals(tones.primary, colors.locked)
        assertEquals(tones.primaryContainer, colors.armed)
        assertEquals(tones.primaryContainer, colors.chip)
    }

    @Test
    fun `on Light and Dark the accent fills Enter`() {
        for (dark in listOf(false, true)) {
            val tones = Accents.getValue(AccentColour.ORANGE).of(dark)
            val scheme = if (dark) darkColorScheme() else lightColorScheme()
            assertEquals(tones.primary, scheme.withAccent(AccentColour.ORANGE, dark).toKeyboardColors(dark).action)
        }
    }

    @Test
    fun `neutral in dark mode stands out by lightness, as it has no hue to do it with`() {
        val board = Color(0xFF171719) // the system's dark background and text, as DarkThemeTest has them
        val text = Color(0xFFE3E2E6)
        // A pressed key on that board, stepped exactly as the keyboard steps it.
        val pressedKey = darkColorScheme().toKeyboardColors(dark = true).darkSurfaces(board, text).pressedKey
        val tones = Accents.getValue(AccentColour.NEUTRAL).dark
        // WCAG's 3:1 for a control's state: an armed key and an on switch against the board.
        assertTrue(contrast(tones.primaryContainer, board) >= 3f, "armed key on the board")
        assertTrue(contrast(tones.primaryContainer, pressedKey) >= 3f, "armed key against a pressed one")
        assertTrue(contrast(tones.primary, board) >= 3f, "on switch track and locked key on the board")
        // An on switch's thumb (onArmed) on its track (armedRing): a dark thumb on a light track.
        assertTrue(contrast(tones.onPrimaryContainer, tones.primary) >= 3f, "switch thumb on its track")
    }
}
