package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
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
