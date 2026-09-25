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
    private var now = 1000L
    private val controller = KeyboardController(InputDispatcher(port), clock = { now }).apply {
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
    fun `a word typed without its apostrophe is restored at the space, and backspace takes it back`() {
        controller.candidateEngine = Candidates(WordList.of("what" to 200, "what's" to 154, "don't" to 185, "done" to 170))
        textField()
        type("whats")
        controller.onKey(space)
        assertEquals("what's ", port.before)
        controller.onKey(backspace)
        assertEquals("whats", port.before)
    }

    @Test
    fun `an apostrophe typed inside a word keeps the word for the strip`() {
        // don'ts completes don't, so the strip shows only if the whole of "don't" was read.
        controller.candidateEngine = Candidates(WordList.of("don't" to 185, "don'ts" to 60, "done" to 170, "dog" to 150))
        val apostrophe = space.copy(label = "'", action = KeyAction.Text("'"))
        textField()
        type("don")
        controller.onKey(apostrophe)
        type("t")
        assertEquals("don't", controller.candidates?.typed)
        controller.onKey(space)
        assertEquals("don't ", port.before) // a known word: nothing to correct
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
        assertEquals("chek", port.before) // a phantom space is owed, not typed
        assertNull(controller.candidates)
        controller.onKey(space) // Space types the one space it owed
        assertEquals("chek ", port.before)
        controller.onKey(keys.first { it.action == KeyAction.Shift })
        type("chek")
        assertEquals("Chek", controller.candidates?.typed)
        assertNull(controller.candidates?.correction) // kept once, in either case, for the rest of the field
    }

    @Test
    fun `tapping a candidate replaces the word in one batch and owes a space after it`() {
        textField()
        type("spel")
        port.edits.clear()
        controller.pickCandidate("spelling")
        // No space is typed, so none can be left behind in the field.
        assertEquals("spelling", port.before)
        assertEquals(listOf("begin", "begin", "delete:4,0", "commit:spelling", "end", "end"), port.edits)
        assertNull(controller.candidates)
    }

    @Test
    fun `punctuation after a picked word lands against it, and the next word gets the owed space`() {
        val bang = dot.copy(label = "!", action = KeyAction.Text("!"))
        val comma = dot.copy(label = ",", action = KeyAction.Text(","))
        val close = dot.copy(label = ")", action = KeyAction.Text(")"))
        val quote = dot.copy(label = "\"", action = KeyAction.Text("\""))
        val ellipsis = dot.copy(label = "…", action = KeyAction.Text("…"))
        // A mark that ends a word lands against it and keeps the space owed; an opening quote is
        // not one, so the owed space goes in front of it.
        for ((mark, expected) in listOf(bang to "spelling!", comma to "spelling,", dot to "spelling.", ellipsis to "spelling…", close to "spelling)", quote to "spelling \"")) {
            textField()
            port.before = ""
            type("spel")
            controller.pickCandidate("spelling")
            controller.onKey(mark)
            assertEquals(expected, port.before, "after ${mark.label}")
            type("w")
            val next = if (mark == quote) "${expected}w" else "$expected w"
            assertEquals(next, port.before, "a letter after ${mark.label}")
        }
    }

    @Test
    fun `a mark from the symbols page still lands against the pick, and the space stays owed`() {
        textField()
        type("spel")
        controller.pickCandidate("spelling")
        // On a phone ! and ) live behind ?123: the page switch types nothing and keeps the space ours.
        controller.onKey(keys.first { it.action == KeyAction.SwitchLayer(LayerId.SYMBOLS) })
        controller.onKey(SymbolsLayer.rows.flatMap { it.keys }.first { it.label == "!" })
        assertEquals("spelling!", port.before)
        controller.onKey(SymbolsLayer.rows.flatMap { it.keys }.first { it.action == KeyAction.SwitchLayer(LayerId.LETTERS) })
        type("w")
        assertEquals("spelling! w", port.before)
    }

    @Test
    fun `space after a picked word types the owed space once, and backspace settles it`() {
        textField()
        type("spel")
        controller.pickCandidate("spelling")
        controller.onKey(space)
        assertEquals("spelling ", port.before)
        now += 1000 // slower than a double space, which would type ". "
        controller.onKey(space)
        assertEquals("spelling  ", port.before) // only the first press was owed
        type("spel")
        controller.pickCandidate("spelled")
        controller.onKey(backspace)
        assertEquals("spelling  spelle", port.before) // a letter, as there is no space to delete
        type("r")
        assertEquals("spelling  speller", port.before) // and nothing is owed after it
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
    fun `a restart in the same field keeps the correction's undo and the pick's space`() {
        // An app may restart input without the user leaving the field; what the last key set up
        // must survive it, and it checks the text still ends as expected before it acts.
        textField()
        type("chek")
        controller.onKey(space)
        assertEquals("check ", port.before)
        controller.onRestartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.onKey(backspace)
        assertEquals("chek", port.before)

        controller.onKey(space)
        type("spel")
        controller.pickCandidate("spelling")
        controller.onRestartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.onKey(dot)
        assertEquals("chek spelling.", port.before)
        type("s")
        assertEquals("chek spelling. s", port.before) // the space owed after the pick came through the restart
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

    @Test
    fun `a quick double space after a word types a full stop and arms the capital`() {
        controller.autoCapitalize = true
        textField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        type("hello")
        controller.onKey(space)
        now += 200
        port.edits.clear()
        controller.onKey(space)
        assertEquals("Hello. ", port.before)
        assertEquals(listOf("begin", "delete:1,0", "commit:. ", "end"), port.edits)
        assertTrue(controller.autoCapital)
        // A third space is just a space: a full stop is not a word.
        now += 200
        controller.onKey(space)
        assertEquals("Hello.  ", port.before)
    }

    @Test
    fun `a slow second space, or one not after a word, stays a space`() {
        textField()
        type("hello")
        controller.onKey(space)
        now += DOUBLE_SPACE_WINDOW + 1
        controller.onKey(space)
        assertEquals("hello  ", port.before)
        port.before = "hello, "
        controller.onKey(space)
        now += 100
        controller.onKey(space)
        assertEquals("hello,   ", port.before)
    }

    @Test
    fun `a key between the two spaces cancels the full stop`() {
        textField()
        type("hello")
        controller.onKey(space)
        controller.onKey(key("a"))
        controller.onKey(backspace)
        controller.onKey(space)
        assertEquals("hello  ", port.before)
    }

    @Test
    fun `passwords, addresses, e-mail and terminals keep their two spaces, and so does the setting off`() {
        val fields = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_NULL,
        )
        for (field in fields) {
            textField(field)
            port.before = "abc"
            controller.onKey(space)
            controller.onKey(space)
            assertEquals("abc  ", port.before, "input type $field")
        }
        controller.doubleSpacePeriod = false
        textField()
        port.before = "abc"
        controller.onKey(space)
        controller.onKey(space)
        assertEquals("abc  ", port.before)
    }

    @Test
    fun `space then space after a pick makes the pick's space a full stop`() {
        textField()
        type("spel")
        controller.pickCandidate("spelling")
        controller.onKey(space) // finds the pick's space there
        now += 100
        controller.onKey(space)
        assertEquals("spelling. ", port.before)
    }

    private companion object {
        const val DOUBLE_SPACE_WINDOW = KeyboardController.DOUBLE_SPACE_WINDOW_MS
    }

    @Test
    fun `the space owed after a pick goes when the cursor moves, the field changes or enter is pressed`() {
        val enter = keys.first { it.action == KeyAction.Enter }
        textField()
        type("spel")
        controller.pickCandidate("spelling")
        type("w")
        assertEquals("spelling w", port.before) // a letter types the owed space first

        textField()
        port.before = ""
        type("spel")
        controller.pickCandidate("spelling")
        port.before = "elsewhere" // the cursor moved: the text no longer ends with the pick
        type("s")
        assertEquals("elsewheres", port.before)

        textField()
        port.before = ""
        type("spel")
        controller.pickCandidate("spelling")
        textField() // a new field
        type("s")
        assertEquals("spellings", port.before)

        textField()
        port.before = ""
        type("spel")
        controller.pickCandidate("spelling")
        controller.onKey(enter)
        assertEquals("spelling", port.before) // Enter leaves no space behind the word
    }

    @Test
    fun `an apostrophe after a pick goes on with the word`() {
        controller.candidateEngine = Candidates(WordList.of("john" to 150, "johnny" to 90))
        val apostrophe = space.copy(label = "'", action = KeyAction.Text("'"))
        textField()
        type("jo")
        controller.pickCandidate("john")
        controller.onKey(apostrophe)
        type("s")
        assertEquals("john's", port.before)
    }

    @Test
    fun `the globe and caps lock type nothing, so the space stays owed across them`() {
        controller.enabledLanguages = setOf("en_US", "uk")
        val globe = Key("globe", KeyAction.SwitchLanguage)
        val capsLock = Key("caps", KeyAction.CapsLock)
        for (key in listOf(globe, capsLock)) {
            textField()
            port.before = ""
            type("spel")
            controller.pickCandidate("spelling")
            controller.onKey(key)
            type("w")
            assertEquals("spelling w", port.before.lowercase(), "after ${key.label}")
        }
    }
}
