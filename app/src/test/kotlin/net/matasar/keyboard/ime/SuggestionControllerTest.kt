package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.layout.DeveloperStrip
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.LettersLayer
import net.matasar.keyboard.layout.ModifierKey
import net.matasar.keyboard.nlp.Candidates
import net.matasar.keyboard.nlp.WordList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestionControllerTest {

    private val port = FakeEditorPort()
    private val controller = KeyboardController(InputDispatcher(port), clock = { 1000L }).apply {
        candidateEngine = Candidates(WordList.of("check" to 200, "checking" to 150, "chef" to 90, "spell" to 100, "spelling" to 90, "spelled" to 120, "hello" to 200))
    }

    private val keys = LettersLayer.rows.flatMap { it.keys }
    private val space = keys.first { it.action == KeyAction.Space }
    private val backspace = keys.first { it.action == KeyAction.Backspace }
    private val dot = space.copy(label = ".", action = KeyAction.Text("."))
    private fun key(letter: String): Key = keys.first { it.label == letter }
    private fun type(word: String) = word.forEach { controller.onKey(key(it.toString())) }
    private fun textField(inputType: Int = InputType.TYPE_CLASS_TEXT) =
        controller.onStartInput(EditorInfo().apply { this.inputType = inputType })

    @Test
    fun `typing shows the word, its correction and a completion`() {
        textField()
        type("chek")
        val candidates = assertNotNull(controller.candidates)
        assertEquals(listOf("chek", "check", "chef"), candidates.words)
        assertEquals("check", candidates.correction)
    }

    @Test
    fun `space applies the correction and backspace right after takes it back`() {
        textField()
        type("chek")
        controller.onKey(space)
        assertEquals("check ", port.before)
        assertNull(controller.candidates)
        controller.onKey(backspace)
        assertEquals("chek", port.before)
        controller.onSelectionChanged() // the field reports our own replacement; the undo must survive it
        assertNull(controller.candidates?.correction)
        assertEquals("chek", controller.candidates?.typed)
        controller.onKey(space)
        assertEquals("chek ", port.before)
        type("chek")
        assertEquals("check", controller.candidates?.correction) // a new word, corrected again
    }

    @Test
    fun `the correction and the separator after it reach the app as one batch, and so does the undo`() {
        textField()
        type("chek")
        port.edits.clear()
        controller.onKey(space)
        assertEquals(listOf("begin", "begin", "delete:4,0", "commit:check", "end", "commit: ", "end"), port.edits)
        port.edits.clear()
        controller.onKey(backspace)
        assertEquals(listOf("begin", "delete:6,0", "commit:chek", "end"), port.edits)
    }

    @Test
    fun `punctuation applies the correction too and a known word is left alone`() {
        textField()
        type("chek")
        controller.onKey(dot)
        assertEquals("check.", port.before)
        type("spell")
        controller.onKey(space)
        assertEquals("check.spell ", port.before)
    }

    @Test
    fun `a key after the correction makes backspace ordinary again`() {
        textField()
        type("chek")
        controller.onKey(space)
        type("a")
        controller.onKey(backspace)
        assertEquals("check ", port.before)
    }

    @Test
    fun `the undo is refused when the text no longer ends with the correction`() {
        textField()
        type("chek")
        controller.onKey(space)
        port.before = "elsewhere"
        controller.onKey(backspace)
        assertEquals("elsewher", port.before)
    }

    @Test
    fun `tapping the typed word keeps it and the next space does not correct it`() {
        textField()
        type("chek")
        assertEquals("check", controller.candidates?.correction)
        controller.pickCandidate("chek")
        assertEquals("chek", port.before)
        assertNull(controller.candidates)
        controller.onSelectionChanged() // a stray cursor report brings the strip back without the correction
        assertNull(controller.candidates?.correction)
        controller.onKey(space)
        assertEquals("chek ", port.before)
        type("chek")
        assertEquals("check", controller.candidates?.correction) // a new word, corrected again
    }

    @Test
    fun `tapping a candidate replaces the word with no space`() {
        textField()
        type("spel")
        controller.pickCandidate("spelling")
        // No trailing space: what follows the word is the user's to type.
        assertEquals("spelling", port.before)
        assertNull(controller.candidates)
    }

    @Test
    fun `no candidates and no read of the field where they are not allowed`() {
        for (inputType in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_NULL,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        )) {
            textField(inputType)
            type("chek")
            assertNull(controller.candidates, "input type $inputType")
            controller.onKey(space)
            assertTrue(port.before.endsWith("chek "), port.before)
        }
        controller.onFinishInput()
        port.before = "spel"
        controller.onSelectionChanged()
        assertNull(controller.candidates)
        assertEquals(0, port.textReads)
    }

    @Test
    fun `an allowed app gets candidates in a field that asked for none`() {
        val youtube = 0xa4001 // text, sentence caps, multi-line, no suggestions
        textField(youtube)
        controller.autoCapitalize = false // the field asks for sentence caps; this test is not about them
        assertTrue(controller.suggestInAppOffered)
        assertFalse(controller.suggestInApp)
        type("chek")
        assertNull(controller.candidates)
        // The stored answer arrives a moment after the field, and applies to the field that is open.
        controller.restoreSuggestInApp(true)
        controller.onKey(space)
        type("chek")
        assertEquals("check", controller.candidates?.correction)
    }

    @Test
    fun `the gear sheet's row takes effect in the field that is open`() {
        textField(0xa4001)
        controller.autoCapitalize = false
        controller.settingsSheetOpen = true
        controller.toggleSuggestInApp()
        assertTrue(controller.suggestInApp)
        assertFalse(controller.settingsSheetOpen)
        type("chek")
        assertEquals("check", controller.candidates?.correction)
        // And off again, in the same field.
        controller.toggleSuggestInApp()
        controller.onKey(space)
        type("chek")
        assertNull(controller.candidates)
    }

    @Test
    fun `allowing an app does not reach its password or e-mail fields, and is not offered there`() {
        controller.restoreSuggestInApp(true)
        for (inputType in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_NULL,
        )) {
            textField(inputType)
            controller.restoreSuggestInApp(true)
            assertFalse(controller.suggestInAppOffered, "input type $inputType")
            type("chek")
            assertNull(controller.candidates, "input type $inputType")
        }
    }

    @Test
    fun `the answer does not carry from one app to the next`() {
        textField(0xa4001)
        controller.autoCapitalize = false
        controller.restoreSuggestInApp(true)
        type("chek")
        assertNotNull(controller.candidates)
        // A new field, before its own answer has been read: the app's own request stands again.
        textField(0xa4001)
        assertFalse(controller.suggestInApp)
        type("chek")
        assertNull(controller.candidates)
    }

    @Test
    fun `a filled password is typed and never read back for the strip until a key`() {
        textField()
        val reads = port.textReads
        controller.typeFilledPassword("chec")
        assertEquals(listOf("chec"), port.committed)
        controller.onSelectionChanged()
        assertNull(controller.candidates)
        assertEquals(reads, port.textReads)
        type("k")
        assertEquals("check", controller.candidates?.typed) // a key of the user's own lifts it

        controller.typeFilledPassword("chek")
        controller.onFinishInput()
        textField()
        port.before = "spel"
        controller.onSelectionChanged()
        assertEquals("spel", controller.candidates?.typed) // and so does another field
    }

    @Test
    fun `a filled username is typed and never read back, and the keyboard moves on with next`() {
        textField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        assertTrue(controller.fieldIsEmpty())
        val reads = port.textReads
        controller.typeFilledUsername("chec")
        assertEquals(listOf("chec"), port.committed)
        controller.onSelectionChanged()
        assertNull(controller.candidates)
        assertEquals(reads, port.textReads)
        controller.goToNextField()
        assertEquals(listOf(EditorInfo.IME_ACTION_NEXT), port.editorActions)
    }

    @Test
    fun `a field with text before, after or selected is not empty`() {
        textField()
        port.before = "a"
        assertFalse(controller.fieldIsEmpty())
        port.before = ""
        port.after = "b"
        assertFalse(controller.fieldIsEmpty())
        port.after = ""
        port.selected = "c"
        assertFalse(controller.fieldIsEmpty())
    }

    @Test
    fun `a combination modifier hides the strip and settings switch the parts off`() {
        textField()
        type("che")
        controller.onKey(DeveloperStrip.keys.first { it.action == KeyAction.Modifier(ModifierKey.CTRL) })
        assertNull(controller.candidates)
        controller.onKey(key("k"))
        assertNull(controller.candidates)
        controller.onKey(space)

        controller.autoCorrect = false
        type("chek")
        assertEquals("check", controller.candidates?.correction)
        controller.onKey(space)
        assertTrue(port.before.endsWith("chek "), port.before)

        controller.suggestionsEnabled = false
        val reads = port.textReads
        type("chek")
        assertNull(controller.candidates)
        assertEquals(reads, port.textReads)
    }

    @Test
    fun `a cursor inside a word gets no candidates, so space does not correct the first half`() {
        textField()
        port.before = "che"
        port.after = "ck"
        controller.onSelectionChanged()
        assertNull(controller.candidates)
        controller.onKey(space)
        assertEquals("che ", port.before)
    }

    @Test
    fun `a field that changed under the strip is not edited`() {
        textField()
        type("chek")
        port.before = "changed"
        controller.onKey(space)
        assertEquals("changed ", port.before)
        type("chek")
        port.before = "x"
        controller.pickCandidate("check")
        assertEquals("x", port.before)
        assertNull(controller.candidates)
    }

    @Test
    fun `enter leaves no stale candidates behind`() {
        textField()
        type("chek")
        controller.onKey(keys.first { it.action == KeyAction.Enter })
        assertNull(controller.candidates)
    }

    @Test
    fun `a moved cursor re-derives the word and a language switch clears the strip`() {
        textField()
        port.before = "hello spel"
        controller.onSelectionChanged()
        assertEquals("spel", controller.candidates?.typed)
        controller.switchLanguage(Languages.ukrainian)
        assertNull(controller.candidates)
        controller.collapseCandidates()
        assertTrue(controller.candidatesCollapsed)
        type("s")
        assertTrue(!controller.candidatesCollapsed)
    }
}
