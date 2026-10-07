package net.matasar.keyboard.ui

import androidx.compose.ui.graphics.Color
import net.matasar.keyboard.ime.KeyboardController
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.ModifierKey
import net.matasar.keyboard.layout.sixtyPercentLayer
import net.matasar.keyboard.ui.theme.KeyboardColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** While Fn is on, the keys it gives a meaning are filled as Fn's, so they can be told at a glance. */
class FnKeysVisualTest {

    private val colors = KeyboardColors(
        background = Color(0xFF000001), toolbar = Color(0xFF000002), key = Color(0xFF000003),
        onKey = Color(0xFF000004), functionKey = Color(0xFF000005), onFunctionKey = Color(0xFF000006),
        pressedKey = Color(0xFF000007), action = Color(0xFF000008), onAction = Color(0xFF000009),
        armed = Color(0xFF00000A), onArmed = Color(0xFF00000B), armedRing = Color(0xFF00000C),
        locked = Color(0xFF00000D), onLocked = Color(0xFF00000E), popup = Color(0xFF00000F),
        onPopup = Color(0xFF000010), icon = Color(0xFF000011), chip = Color(0xFF000012),
        onChip = Color(0xFF000013), subtle = Color(0xFF000014), legend = Color(0xFF000015),
        keyShadow = Color(0xFF000016),
    )
    private val fnFill = KeyVisual(colors.armed, colors.onArmed)

    private val controller = KeyboardController(InputDispatcher(FakeEditorPort()), clock = { 1000L })
    private val keys = sixtyPercentLayer(Languages.english, withGlobe = true).rows.flatMap { it.keys }
    private fun key(label: String) = keys.first { it.label == label }
    private val fnKey = keys.first { it.action == KeyAction.Modifier(ModifierKey.FN) }
    private val altKey = keys.first { it.action == KeyAction.Modifier(ModifierKey.ALT) }

    private fun visual(label: String) = visualFor(key(label), controller, colors)

    @Test
    fun `with fn idle no key wears fn's fill`() {
        for (key in keys) assertNotEquals(fnFill, visualFor(key, controller, colors), key.label)
    }

    @Test
    fun `fn fills the keys it gives a meaning, without the armed ring`() {
        controller.onKey(fnKey)
        assertEquals(fnFill, visual("1")) // F1
        assertEquals(fnFill, visual("backspace")) // Del
        assertEquals(fnFill, visual("i")) // up arrow
        assertEquals(fnFill, visual("u")) // Home
        assertEquals(fnFill, visualFor(altKey, controller, colors)) // Meta
        // Keys Fn does nothing to look as they always do.
        assertEquals(KeyVisual(colors.key, colors.onKey), visual("q"))
        assertEquals(KeyVisual(colors.key, colors.onKey), visual("/"))
        // Fn itself keeps its armed look, ring and all.
        assertEquals(KeyVisual(colors.armed, colors.onArmed, ring = colors.armedRing), visualFor(fnKey, controller, colors))
    }

    @Test
    fun `a cyrillic letter on a punctuation slot is fn's too`() {
        val kha = sixtyPercentLayer(Languages.ukrainian, withGlobe = true).rows.flatMap { it.keys }.first { it.label == "х" }
        controller.onKey(fnKey)
        assertEquals("[", controller.displayLabel(kha))
        assertEquals(fnFill, visualFor(kha, controller, colors))
    }

    @Test
    fun `a locked modifier keeps its locked look under fn`() {
        controller.onKey(altKey)
        controller.onKey(altKey) // a double tap locks it
        controller.onKey(fnKey)
        assertEquals("Meta", controller.displayLabel(altKey))
        assertEquals(KeyVisual(colors.locked, colors.onLocked), visualFor(altKey, controller, colors))
    }
}
