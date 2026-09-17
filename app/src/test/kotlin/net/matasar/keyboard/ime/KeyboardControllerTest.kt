package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.layout.LettersLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyboardControllerTest {

    private val port = FakeEditorPort()
    private var now = 1000L
    private val controller = KeyboardController(InputDispatcher(port), clock = { now })

    private val shiftKey = LettersLayer.rows[2].keys[0]
    private val q = LettersLayer.rows[0].keys[0]
    private val symbolsKey = LettersLayer.rows[3].keys[0]
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
    fun `symbols space and backspace reach the editor`() {
        controller.onKey(Key("#", KeyAction.Text("#")))
        controller.onKey(Key("space", KeyAction.Space))
        port.before = "# "
        controller.onKey(Key("backspace", KeyAction.Backspace))
        assertEquals(listOf("#", " "), port.committed)
        assertEquals(listOf(1 to 0), port.deletions)
    }

    @Test
    fun `enter uses the field action only when the field offers one`() {
        assertEquals(EditorInfo.IME_ACTION_SEARCH, KeyboardController.editorActionFor(EditorInfo.IME_ACTION_SEARCH, InputType.TYPE_CLASS_TEXT))
        assertNull(KeyboardController.editorActionFor(EditorInfo.IME_ACTION_NONE, InputType.TYPE_CLASS_TEXT))
        assertNull(KeyboardController.editorActionFor(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION, InputType.TYPE_CLASS_TEXT))
        assertNull(KeyboardController.editorActionFor(EditorInfo.IME_ACTION_DONE, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
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
}
