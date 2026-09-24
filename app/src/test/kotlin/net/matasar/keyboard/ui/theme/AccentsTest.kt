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
    fun `a fixed accent changes the four accent roles and nothing else`() {
        val scheme = darkColorScheme()
        val red = scheme.withAccent(AccentColour.RED, dark = true)
        val tones = Accents.getValue(AccentColour.RED).dark
        assertEquals(tones, AccentTones(red.primary, red.onPrimary, red.primaryContainer, red.onPrimaryContainer))
        // ColorScheme has no equals of its own; its toString lists every role.
        assertEquals(
            scheme.copy(primary = red.primary, onPrimary = red.onPrimary, primaryContainer = red.primaryContainer, onPrimaryContainer = red.onPrimaryContainer).toString(),
            red.toString(),
        )
        assertTrue(scheme.toString() != red.toString())
    }
}
