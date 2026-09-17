package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.glide.GlideEngine
import net.matasar.keyboard.input.glide.GlideKey
import net.matasar.keyboard.input.glide.GlidePoint
import net.matasar.keyboard.layout.DeveloperStrip
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.LettersLayer
import net.matasar.keyboard.layout.ModifierKey
import net.matasar.keyboard.nlp.WordList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlideControllerTest {

    private val port = FakeEditorPort()
    private val controller = KeyboardController(
        InputDispatcher(port),
        clock = { 1000L },
        background = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
    ).apply {
        scope = CoroutineScope(Dispatchers.Unconfined)
        glideEngine = GlideEngine(WordList.of("hello" to 200, "halo" to 60, "hollow" to 90, "hero" to 110, "help" to 150, "world" to 190))
    }

    /** The phone letters layer at density 1, from the same geometry the classifier tests use. */
    private val keys: List<GlideKey> = net.matasar.keyboard.input.glide.QwertyGeometry.keys
    private fun path(letters: String): List<GlidePoint> = net.matasar.keyboard.input.glide.QwertyGeometry.path(letters)

    @Test
    fun `a glide commits the best word with a space and keeps the alternatives`() {
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.onGlideEnd(path("helo"), keys)
        assertEquals(listOf("hello "), port.committed)
        assertEquals("hello", controller.candidates!!.words.first())
        assertTrue(controller.candidates!!.words.size > 1)
        controller.onSelectionChanged()
        assertEquals("hello", controller.candidates!!.words.first())
    }

    @Test
    fun `tapping an alternative replaces the glided word and its space`() {
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.onGlideEnd(path("helo"), keys)
        val alternative = controller.candidates!!.words[1]
        controller.pickCandidate(alternative)
        assertEquals(listOf(6 to 0), port.deletions) // "hello" + the space
        assertEquals("$alternative ", port.committed.last())
    }

    @Test
    fun `shift capitalises the glided word and is consumed`() {
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.onKey(LettersLayer.rows[2].keys[0]) // shift
        controller.onGlideEnd(path("helo"), keys)
        assertEquals(listOf("Hello "), port.committed)
        assertFalse(controller.shift.active)
    }

    @Test
    fun `no glide in password or terminal fields or while a modifier is active`() {
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        assertFalse(controller.glideAvailable)
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_NULL })
        assertFalse(controller.glideAvailable)
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        assertTrue(controller.glideAvailable)
        controller.onKey(DeveloperStrip.keys.first { it.action == KeyAction.Modifier(ModifierKey.CTRL) })
        assertFalse(controller.glideAvailable)
        controller.onGlideEnd(path("helo"), keys)
        assertTrue(port.committed.isEmpty())
    }

    @Test
    fun `typing a key afterwards clears the alternatives`() {
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.onGlideEnd(path("helo"), keys)
        controller.onKey(LettersLayer.rows[0].keys[0])
        assertEquals(null, controller.candidates)
    }
}
