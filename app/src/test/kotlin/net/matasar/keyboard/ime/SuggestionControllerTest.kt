package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.layout.DeveloperStrip
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.LettersLayer
import net.matasar.keyboard.layout.ModifierKey
import net.matasar.keyboard.layout.SymbolsLayer
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
        assertNull(controller.candidates?.correction) // refused once, left alone for the rest of the field
        controller.onFinishInput()
        textField()
        port.before = ""
        type("chek")
        assertEquals("check", controller.candidates?.correction) // a new field corrects it again
    }

    @Test
    fun `a stale read between the undo and the space does not bring the correction back`() {
        textField()
        type("chek")
        controller.onKey(space)
        controller.onKey(backspace)
        assertEquals("chek", port.before)
        // Some fields answer from a copy that lags our own edits: the cursor report after the
        // undo can still read the corrected text before the field catches up.
        port.before = "check "
        controller.onSelectionChanged()
        port.before = "chek"
        controller.onSelectionChanged()
        controller.onKey(space)
        assertEquals("chek ", port.before)
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
        assertEquals("chek ", port.before)
        assertNull(controller.candidates)
        controller.onKey(space) // the pick's space is already there
        assertEquals("chek ", port.before)
        controller.onKey(keys.first { it.action == KeyAction.Shift })
        type("chek")
        assertEquals("Chek", controller.candidates?.typed)
        assertNull(controller.candidates?.correction) // kept once, in either case, for the rest of the field
    }

    @Test
    fun `tapping a candidate replaces the word and puts a space after it, in one batch`() {
        textField()
        type("spel")
        port.edits.clear()
        controller.pickCandidate("spelling")
        assertEquals("spelling ", port.before)
        assertEquals(listOf("begin", "begin", "delete:4,0", "commit:spelling", "end", "commit: ", "end"), port.edits)
        assertNull(controller.candidates)
    }

    @Test
    fun `punctuation after a picked word takes the place of its space`() {
        val bang = dot.copy(label = "!", action = KeyAction.Text("!"))
        val comma = dot.copy(label = ",", action = KeyAction.Text(","))
        val close = dot.copy(label = ")", action = KeyAction.Text(")"))
        val quote = dot.copy(label = "\"", action = KeyAction.Text("\""))
        for ((mark, expected) in listOf(bang to "spelling! ", comma to "spelling, ", dot to "spelling. ", close to "spelling)", quote to "spelling \"")) {
            textField()
            port.before = ""
            type("spel")
            controller.pickCandidate("spelling")
            port.edits.clear()
            controller.onKey(mark)
            assertEquals(expected, port.before, "after ${mark.label}")
            if (mark != quote) assertEquals(listOf("begin", "delete:1,0", "commit:${expected.removePrefix("spelling")}", "end"), port.edits)
        }
    }

    @Test
    fun `a mark from the symbols page still takes the pick's space`() {
        textField()
        type("spel")
        controller.pickCandidate("spelling")
        // On a phone ! and ) live behind ?123: the page switch types nothing and keeps the space ours.
        controller.onKey(keys.first { it.action == KeyAction.SwitchLayer(LayerId.SYMBOLS) })
        controller.onKey(SymbolsLayer.rows.flatMap { it.keys }.first { it.label == "!" })
        assertEquals("spelling! ", port.before)
    }

    @Test
    fun `space after a picked word is absorbed once, and backspace deletes the space`() {
        textField()
        type("spel")
        controller.pickCandidate("spelling")
        controller.onKey(space)
        assertEquals("spelling ", port.before)
        controller.onKey(space)
        assertEquals("spelling  ", port.before) // only the first press finds it there
        type("spel")
        controller.pickCandidate("spelled")
        controller.onKey(backspace)
        assertEquals("spelling  spelled", port.before)
    }

    @Test
    fun `no space is added where the field already has one or a mark follows`() {
        textField()
        type("spel")
        port.after = " rest"
        controller.pickCandidate("spelling")
        assertEquals("spelling", port.before)
        controller.onKey(space) // nothing of ours to find: an ordinary space
        assertEquals("spelling ", port.before)
        port.before = ""
        port.after = ","
        type("spel")
        controller.pickCandidate("spelled")
        assertEquals("spelled", port.before)
    }

    @Test
    fun `a moved cursor makes the next key ordinary`() {
        val bang = dot.copy(label = "!", action = KeyAction.Text("!"))
        textField()
        type("spel")
        controller.pickCandidate("spelling")
        port.before = "elsewhere "
        controller.onKey(bang)
        assertEquals("elsewhere !", port.before)
    }

    @Test
    fun `a field reached by a restart is read afresh, and the last field's words leave the strip`() {
        textField()
        type("chek")
        assertNotNull(controller.candidates)
        assertFalse(controller.numberRowShown)
        // Focus moved to the password field of the same screen: a restart, not a new start.
        controller.onRestartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        assertTrue(controller.numberRowShown)
        assertNull(controller.candidates) // the text field's words, still on the strip over a password
        controller.onRestartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        assertFalse(controller.numberRowShown)
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
        // And off again, in the same field: the words already on the strip go at once, rather
        // than waiting for the next key in a field the app asked to keep quiet.
        controller.toggleSuggestInApp()
        assertNull(controller.candidates)
        controller.onKey(space)
        type("chek")
        assertNull(controller.candidates)
    }

    @Test
    fun `the row is not offered when suggestions are off altogether`() {
        textField(0xa4001)
        assertTrue(controller.suggestInAppOffered)
        // With the setting off there is nothing to allow: the strip stays empty either way.
        controller.suggestionsEnabled = false
        assertFalse(controller.suggestInAppOffered)
        controller.suggestionsEnabled = true
        assertTrue(controller.suggestInAppOffered)
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
