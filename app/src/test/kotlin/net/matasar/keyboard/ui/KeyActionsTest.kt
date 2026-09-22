package net.matasar.keyboard.ui

import net.matasar.keyboard.ime.KeyboardController
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.LettersLayer
import net.matasar.keyboard.layout.phoneLayout
import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyActionsTest {

    private val port = FakeEditorPort()
    private val controller = KeyboardController(InputDispatcher(port), clock = { 1000L })
    private val letters = LettersLayer.rows.flatMap { it.keys }
    private fun actions(key: Key, withGlobe: Boolean = false) = keyActions(key, controller.accentsFor(key), withGlobe)

    @Test
    fun `a letter offers its accents, in the case it would type them`() {
        val e = letters.first { it.label == "e" }
        assertEquals(e.longPress.map { KeyAccessibilityAction.Accent(it) }, actions(e))
        assertTrue(e.longPress.isNotEmpty(), "the e key is expected to carry accents")
        controller.onKey(letters.first { it.action == KeyAction.Shift })
        assertEquals(e.longPress.map { KeyAccessibilityAction.Accent(it.uppercase()) }, actions(e))
    }

    @Test
    fun `a letter without accents offers nothing`() {
        assertEquals(emptyList(), actions(letters.first { it.label == "t" }))
    }

    @Test
    fun `a password field takes the accents away`() {
        val e = letters.first { it.label == "e" }
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        assertEquals(emptyList(), actions(e))
    }

    @Test
    fun `space offers the cursor moves the trackpad makes`() {
        assertEquals(
            listOf(
                KeyAccessibilityAction.MoveCursor(-1, byWord = false),
                KeyAccessibilityAction.MoveCursor(1, byWord = false),
                KeyAccessibilityAction.MoveCursor(-1, byWord = true),
                KeyAccessibilityAction.MoveCursor(1, byWord = true),
            ),
            actions(letters.first { it.action == KeyAction.Space }),
        )
    }

    @Test
    fun `the globe offers the language picker, and only when there is one`() {
        val globe = phoneLayout(Languages.english, withGlobe = true).layers.values
            .flatMap { layer -> layer.rows.flatMap { it.keys } }
            .first { it.action == KeyAction.SwitchLanguage }
        assertEquals(listOf(KeyAccessibilityAction.ChooseLanguage), actions(globe, withGlobe = true))
        assertEquals(emptyList(), actions(globe, withGlobe = false))
    }

    @Test
    fun `only the keys that hold something behind a long press offer anything`() {
        val offering = letters.filter { actions(it).isNotEmpty() }
        val expected = letters.filter { it.longPress.isNotEmpty() || it.action == KeyAction.Space }
        assertEquals(expected.map { it.id }, offering.map { it.id })
    }
}
