package net.matasar.keyboard.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.input.glide.GlideEngine
import net.matasar.keyboard.input.glide.QwertyGeometry
import net.matasar.keyboard.layout.DeveloperStrip
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.LettersLayer
import net.matasar.keyboard.layout.ModifierKey
import net.matasar.keyboard.nlp.Candidates
import net.matasar.keyboard.nlp.WordList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoCapitalizationTest {

    private val port = FakeEditorPort()
    private var now = 1000L
    private val controller = KeyboardController(
        InputDispatcher(port),
        clock = { now },
        background = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
    )

    private val letters = LettersLayer.rows.flatMap { it.keys }
    private fun letter(c: Char) = letters.first { (it.action as? KeyAction.Letter)?.lower == c.toString() }
    private val shiftKey = letters.first { it.action == KeyAction.Shift }
    private val space = Key("space", KeyAction.Space)
    private val period = Key(".", KeyAction.Text("."))
    private val backspace = Key("backspace", KeyAction.Backspace)
    private val enter = Key("enter", KeyAction.Enter)
    private val one = Key("1", KeyAction.Text("1", "!"), shiftedLabel = "!", fnAction = KeyAction.KeyCode(KeyEvent.KEYCODE_F1))
    private val ctrl = DeveloperStrip.keys.first { it.action == KeyAction.Modifier(ModifierKey.CTRL) }
    private val capsKey = Key("Caps", KeyAction.CapsLock)
    private val accented = Key("e", KeyAction.Letter("e", "E"), longPress = listOf("é", "è"))

    private fun startIn(inputType: Int) = controller.onStartInput(EditorInfo().apply { this.inputType = inputType })

    private fun startInChat() = startIn(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

    private fun type(text: String) = text.forEach { c ->
        when (c) {
            ' ' -> controller.onKey(space)
            '.' -> controller.onKey(period)
            else -> controller.onKey(letter(c))
        }
    }

    @Test
    fun `a chat field capitalizes the first letter of every sentence and nothing else`() {
        startInChat()
        assertTrue(controller.autoCapital)
        type("hi. how are you")
        assertEquals("Hi. How are you", port.before)
    }

    @Test
    fun `a new line starts a sentence`() {
        startIn(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        type("ok")
        port.before += "\n"
        controller.onSelectionChanged()
        type("so")
        assertEquals("Ok\nSo", port.before)
    }

    @Test
    fun `fields that ask for no capitals never get one, and are never asked`() {
        for (inputType in listOf(
            InputType.TYPE_NULL,
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        )) {
            port.before = ""
            startIn(inputType)
            type("hi. ok")
            controller.onSelectionChanged()
            assertEquals("hi. ok", port.before, "inputType 0x${Integer.toHexString(inputType)}")
            assertFalse(controller.autoCapital)
        }
        assertEquals(0, port.capsQueries)
    }

    @Test
    fun `no field info means no capitals`() {
        controller.onStartInput(null)
        type("hi")
        assertEquals("hi", port.before)
        assertEquals(0, port.capsQueries)
    }

    @Test
    fun `the letters show capitals while the capital is automatic`() {
        startInChat()
        assertEquals("Q", controller.displayLabel(letter('q')))
        assertEquals(listOf("É", "È"), controller.accentsFor(accented))
        type("q")
        assertEquals("w", controller.displayLabel(letter('w')))
        assertEquals(listOf("é", "è"), controller.accentsFor(accented))
    }

    @Test
    fun `a digit at the start of a sentence stays a digit, and Fn keeps its meaning`() {
        startInChat()
        assertEquals("1", controller.displayLabel(one))
        controller.onKey(one)
        assertEquals("1", port.before)
    }

    @Test
    fun `a combination at the start of a sentence carries no Shift`() {
        startInChat()
        controller.onKey(ctrl)
        assertFalse(controller.autoCapital)
        assertEquals("Q", controller.displayLabel(letter('q'))) // the slot's US letter, as always under Ctrl
        controller.onKey(letter('q'))
        val (code, meta) = port.keys.single()
        assertEquals(KeyEvent.KEYCODE_Q, code)
        assertEquals(0, meta and KeyEvent.META_SHIFT_ON)
        assertTrue(meta and KeyEvent.META_CTRL_ON != 0)
        // The combination went out and Ctrl released: the capital is back.
        assertTrue(controller.autoCapital)
    }

    @Test
    fun `ctrl C at the start of a sentence is still the editor's copy`() {
        startInChat()
        controller.onKey(ctrl)
        controller.onKey(letter('c'))
        assertEquals(listOf(android.R.id.copy), port.contextActions)
        assertTrue(port.keys.isEmpty())
    }

    @Test
    fun `a Shift tap cancels the automatic capital until the next key`() {
        startInChat()
        controller.onKey(shiftKey)
        assertFalse(controller.autoCapital)
        assertEquals(LatchState.IDLE, controller.shift.state)
        // The app reports the cursor after the tap: the cancel holds.
        controller.onSelectionChanged()
        assertFalse(controller.autoCapital)
        type("iphone")
        assertEquals("iphone", port.before)
    }

    @Test
    fun `a double tap on an automatic capital locks caps`() {
        startInChat()
        controller.onKey(shiftKey)
        now += 100
        controller.onKey(shiftKey)
        assertEquals(LatchState.LOCKED, controller.shift.state)
        assertFalse(controller.autoCapital)
        type("ok")
        assertEquals("OK", port.before)
    }

    @Test
    fun `a slow second tap after cancelling arms Shift as usual`() {
        startInChat()
        controller.onKey(shiftKey)
        now += 1000
        controller.onKey(shiftKey)
        assertEquals(LatchState.ARMED, controller.shift.state)
    }

    @Test
    fun `a long press on an automatic capital locks caps`() {
        startInChat()
        controller.onKeyLongPress(shiftKey)
        assertEquals(LatchState.LOCKED, controller.shift.state)
        assertFalse(controller.autoCapital)
    }

    @Test
    fun `backspace back to a sentence start brings the capital back`() {
        startInChat()
        type("hi. o")
        assertFalse(controller.autoCapital)
        controller.onKey(backspace)
        assertTrue(controller.autoCapital)
        port.before = "Hi"
        controller.onKey(backspace)
        controller.onKey(backspace)
        assertTrue(controller.autoCapital)
    }

    @Test
    fun `moving the cursor asks again`() {
        startInChat()
        type("hi")
        assertFalse(controller.autoCapital)
        port.before = ""
        controller.onSelectionChanged()
        assertTrue(controller.autoCapital)
    }

    @Test
    fun `enter is followed by a capital in a field that starts sentences on new lines`() {
        startInChat()
        type("ok")
        controller.onKey(enter)
        port.before += "\n" // the fake does not echo a sent key; the editor would
        controller.onSelectionChanged()
        assertTrue(controller.autoCapital)
    }

    @Test
    fun `with the setting off nothing is capitalized`() {
        controller.autoCapitalize = false
        startInChat()
        assertFalse(controller.autoCapital)
        type("hi. ok")
        assertEquals("hi. ok", port.before)
        assertEquals(0, port.capsQueries)
        controller.autoCapitalize = true
        assertFalse(controller.autoCapital) // "hi. ok" does not start a sentence
        port.before = "ok. "
        controller.autoCapitalize = false
        controller.autoCapitalize = true
        assertTrue(controller.autoCapital)
    }

    @Test
    fun `a glide at the start of a sentence commits a capitalized word`() {
        controller.scope = CoroutineScope(Dispatchers.Unconfined)
        controller.glideEngine = GlideEngine(WordList.of("hello" to 200, "help" to 150))
        startInChat()
        controller.onGlideEnd(QwertyGeometry.path("helo"), QwertyGeometry.keys)
        assertEquals("Hello", port.before)
        assertFalse(controller.autoCapital)
    }

    @Test
    fun `leaving the field drops the capital`() {
        startInChat()
        controller.onFinishInput()
        assertFalse(controller.autoCapital)
    }

    @Test
    fun `a words field capitalizes every word, and undoing a correction asks again`() {
        controller.candidateEngine = Candidates(WordList.of("check" to 200, "chef" to 90))
        startIn(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        type("ann chek ")
        assertEquals("Ann Check ", port.before)
        assertTrue(controller.autoCapital)
        controller.onKey(backspace) // takes the correction back: the cursor is inside a word again
        assertEquals("Ann Chek", port.before)
        assertFalse(controller.autoCapital)
    }

    @Test
    fun `picking a word from the strip asks the field again`() {
        controller.candidateEngine = Candidates(WordList.of("check" to 200, "chef" to 90))
        startIn(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        type("che")
        val asked = port.capsQueries
        controller.pickCandidate("Check")
        assertEquals("Check ", port.before)
        // The pick's space starts a new word, so the field asks for a capital again.
        assertTrue(controller.autoCapital)
        assertTrue(port.capsQueries > asked)
    }

    @Test
    fun `caps lock takes over from the automatic capital and hands it back`() {
        startInChat()
        controller.onKey(capsKey)
        assertEquals(LatchState.LOCKED, controller.shift.state)
        assertFalse(controller.autoCapital)
        controller.onKey(capsKey)
        assertEquals(LatchState.IDLE, controller.shift.state)
        assertTrue(controller.autoCapital)
    }

    @Test
    fun `the trackpad drops the capital while it moves and asks again where it stops`() {
        startInChat()
        type("hi")
        controller.startTrackpad(10f)
        assertFalse(controller.autoCapital)
        port.before = "" // the arrows took the cursor to the start of the field
        controller.onSelectionChanged()
        assertFalse(controller.autoCapital)
        controller.endTrackpad()
        assertTrue(controller.autoCapital)
    }
}
