package net.matasar.keyboard.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.layout.LettersLayer
import net.matasar.keyboard.layout.ModifierKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardControllerTest {

    private val port = FakeEditorPort()
    private var now = 1000L
    private val controller = KeyboardController(InputDispatcher(port), clock = { now })

    private val shiftKey = LettersLayer.rows[2].keys[0]
    private val q = LettersLayer.rows[0].keys[0]
    private val symbolsKey = LettersLayer.rows[3].keys[0]
    private val semicolon = Key(";", KeyAction.Text(";", ":"), shiftedLabel = ":")
    private val one = Key("1", KeyAction.Text("1", "!"), shiftedLabel = "!", fnAction = KeyAction.KeyCode(KeyEvent.KEYCODE_F1))
    private val capsKey = Key("Caps", KeyAction.CapsLock)
    private val tab = Key("Tab", KeyAction.KeyCode(KeyEvent.KEYCODE_TAB))
    private val globeKey = Key("globe", KeyAction.SwitchLanguage)

    @Test
    fun `letters follow shift and one-shot shift releases after a letter`() {
        controller.onKey(q)
        controller.onKey(shiftKey)
        assertEquals("Q", controller.displayLabel(q))
        controller.onKey(q)
        controller.onKey(q)
        assertEquals(listOf("q", "Q", "q"), port.committed)
        assertEquals(LatchState.IDLE, controller.shift.state)
    }

    @Test
    fun `double tap locks shift until tapped again`() {
        controller.onKey(shiftKey)
        now += 100
        controller.onKey(shiftKey)
        assertEquals(LatchState.LOCKED, controller.shift.state)
        controller.onKey(q)
        controller.onKey(q)
        assertEquals(listOf("Q", "Q"), port.committed)
        now += 1000
        controller.onKey(shiftKey)
        assertEquals(LatchState.IDLE, controller.shift.state)
    }

    @Test
    fun `switching layers keeps shift and a new field resets everything`() {
        controller.onKey(shiftKey)
        controller.onKey(symbolsKey)
        assertEquals(LayerId.SYMBOLS, controller.layer)
        assertEquals(LatchState.ARMED, controller.shift.state)
        controller.onStartInput(null)
        assertEquals(LayerId.LETTERS, controller.layer)
        assertEquals(LatchState.IDLE, controller.shift.state)
    }

    @Test
    fun `picking a language on the symbols page comes back to its letters`() {
        controller.enabledLanguages = setOf(Languages.english.tag, Languages.ukrainian.tag)
        controller.onKey(symbolsKey)
        assertEquals(LayerId.SYMBOLS, controller.layer)
        controller.onKey(globeKey)
        assertEquals(Languages.ukrainian, controller.language)
        assertEquals(LayerId.LETTERS, controller.layer)

        // A number field opens on the symbols page: its letters are not what the field wants.
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER })
        assertEquals(LayerId.SYMBOLS, controller.layer)
        controller.onKey(globeKey)
        assertEquals(Languages.english, controller.language)
        assertEquals(LayerId.SYMBOLS, controller.layer)
    }

    @Test
    fun `a long press types the key's shifted symbol, and Caps Lock inverts it`() {
        assertTrue(controller.onKeyLongPressShift(semicolon))
        assertEquals(listOf(":"), port.committed)

        // A separator goes in the way a tapped one does.
        assertTrue(controller.onKeyLongPressShift(one))
        assertEquals("!", port.committed.last())

        // An armed Shift does not change what it types, and is spent as any key press spends it.
        controller.onKey(shiftKey)
        assertTrue(controller.onKeyLongPressShift(semicolon))
        assertEquals(":", port.committed.last())
        assertEquals(LatchState.IDLE, controller.shift.state)

        // Caps Lock on: a tap already gives the shifted symbol, so the hold gives the plain one.
        controller.onKey(capsKey)
        assertEquals(LatchState.LOCKED, controller.shift.state)
        assertTrue(controller.onKeyLongPressShift(semicolon))
        assertEquals(";", port.committed.last())
    }

    @Test
    fun `a long press does nothing on a letter, a key with no shifted symbol, or under Fn`() {
        assertFalse(controller.onKeyLongPressShift(q))
        assertFalse(controller.onKeyLongPressShift(tab))
        controller.onKey(Key("Fn", KeyAction.Modifier(ModifierKey.FN)))
        assertFalse(controller.onKeyLongPressShift(one))
        assertTrue(port.committed.isEmpty())
    }

    @Test
    fun `symbols space and backspace reach the editor`() {
        controller.onKey(Key("#", KeyAction.Text("#")))
        controller.onKey(Key("space", KeyAction.Space))
        port.before = "# "
        controller.onKey(Key("backspace", KeyAction.Backspace))
        assertEquals(listOf("#", " "), port.committed)
        assertEquals(listOf(1 to 0), port.deletions)
    }

    @Test
    fun `paste reaches the editor through the dispatcher and leaves a one-shot shift armed`() {
        var clipboard: String? = "hello"
        controller.clipboardText = { clipboard }
        controller.onKey(shiftKey)
        controller.paste()
        assertEquals(listOf("hello"), port.committed)
        assertEquals(LatchState.ARMED, controller.shift.state)
        clipboard = ""
        controller.paste()
        clipboard = null
        controller.paste()
        assertEquals(listOf("hello"), port.committed)
    }

    @Test
    fun `the cursor actions send arrows, and Ctrl with them by word`() {
        controller.moveCursor(-1, byWord = false)
        controller.moveCursor(1, byWord = false)
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_LEFT to 0, KeyEvent.KEYCODE_DPAD_RIGHT to 0), port.keys)
        port.keys.clear()
        controller.moveCursor(-1, byWord = true)
        controller.moveCursor(1, byWord = true)
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_DPAD_LEFT to KeyEvent.META_CTRL_ON,
                KeyEvent.KEYCODE_DPAD_RIGHT to KeyEvent.META_CTRL_ON,
            ),
            port.keys,
        )
    }

    @Test
    fun `enter uses the field action only when the field offers one`() {
        assertEquals(EditorInfo.IME_ACTION_SEARCH, KeyboardController.editorActionFor(EditorInfo.IME_ACTION_SEARCH, InputType.TYPE_CLASS_TEXT))
        assertNull(KeyboardController.editorActionFor(EditorInfo.IME_ACTION_NONE, InputType.TYPE_CLASS_TEXT))
        assertNull(KeyboardController.editorActionFor(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION, InputType.TYPE_CLASS_TEXT))
        assertNull(KeyboardController.editorActionFor(EditorInfo.IME_ACTION_DONE, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
    }
    @Test
    fun `the mic shows with a voice keyboard in a text field and never in a password field`() {
        controller.voiceAvailable = true
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        assertTrue(controller.showVoiceKey)
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        assertFalse(controller.showVoiceKey)
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; privateImeOptions = "nm" })
        assertFalse(controller.showVoiceKey)
    }

    @Test
    fun `turning voice input off hides the mic at once and turning it on brings it back`() {
        controller.voiceAvailable = true
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.voiceInputEnabled = false
        assertFalse(controller.showVoiceKey)
        controller.voiceInputEnabled = true
        assertTrue(controller.showVoiceKey)
    }

    @Test
    fun `a password field reached by a restart loses the mic`() {
        controller.voiceAvailable = true
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        assertTrue(controller.showVoiceKey)
        controller.updateFieldMic(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        assertFalse(controller.showVoiceKey)
    }

    @Test
    fun `no voice keyboard means no mic`() {
        controller.voiceAvailable = false
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        assertFalse(controller.showVoiceKey)
    }

    @Test
    fun `the strip starts folded, opens on its chevron and folds again when the keyboard hides`() {
        assertFalse(controller.toolbarExpanded)
        controller.expandToolbar()
        assertTrue(controller.toolbarExpanded)
        controller.onKeyboardHidden()
        assertFalse(controller.toolbarExpanded)
    }

    @Test
    fun `an opened strip stays open in the next field`() {
        controller.expandToolbar()
        controller.onFinishInput()
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        assertTrue(controller.toolbarExpanded)
    }

    @Test
    fun `always showing the toolbar buttons is the fold setting turned off`() {
        assertFalse(controller.toolbarAlwaysShown)
        controller.foldToolbar = false
        assertTrue(controller.toolbarAlwaysShown)
        assertFalse(controller.toolbarFolds(wide = false))
    }

    @Test
    fun `the strip folds only on the phone board, and never with the setting off`() {
        assertTrue(controller.toolbarFolds(wide = false))
        assertFalse(controller.toolbarFolds(wide = true))
        controller.foldToolbar = false
        assertFalse(controller.toolbarFolds(wide = false))
        assertFalse(controller.toolbarFolds(wide = true))
    }

    @Test
    fun `folding the candidates away leaves the strip folded`() {
        controller.collapseCandidates()
        assertFalse(controller.toolbarExpanded)
    }
}

class WideBoardControllerTest {
    private val port = net.matasar.keyboard.input.FakeEditorPort()
    private val controller = KeyboardController(net.matasar.keyboard.input.InputDispatcher(port), layout = net.matasar.keyboard.layout.WideLayout, clock = { 1000L })
    private val board = net.matasar.keyboard.layout.SixtyPercentLayer
    private fun key(label: String) = board.rows.flatMap { it.keys }.first { it.label == label }

    @Test
    fun `shift then a digit sends the shifted symbol`() {
        controller.onKey(key("Shift"))
        controller.onKey(key("1"))
        controller.onKey(key("1"))
        assertEquals(listOf("!", "1"), port.committed)
    }

    @Test
    fun `caps toggles locked shift`() {
        controller.onKey(key("Caps"))
        assertEquals(LatchState.LOCKED, controller.shift.state)
        controller.onKey(key("a"))
        controller.onKey(key("Caps"))
        controller.onKey(key("a"))
        assertEquals(listOf("A", "a"), port.committed)
    }

    @Test
    fun `fn plus a digit is a function key and fn plus i is an arrow`() {
        controller.onKey(key("Fn"))
        controller.onKey(key("2"))
        controller.onKey(key("Fn"))
        controller.onKey(key("i"))
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_F2 to 0, android.view.KeyEvent.KEYCODE_DPAD_UP to 0), port.keys)
    }

    @Test
    fun `arrow keys under fn repeat on hold while letters do not`() {
        val iKey = key("i")
        val backspace = key("backspace")

        assertFalse(controller.repeats(iKey))
        assertTrue(controller.repeats(backspace))

        port.before = "hello"
        controller.onKeyRepeat(backspace)
        assertEquals(listOf(1 to 0), port.deletions)

        controller.onKey(key("Fn"))
        assertTrue(controller.repeats(iKey))

        controller.onKeyRepeat(iKey)
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_UP to 0), port.keys)

        controller.onKeyRepeat(backspace)
        assertEquals(listOf(1 to 0, 0 to 1), port.deletions)
    }

}
