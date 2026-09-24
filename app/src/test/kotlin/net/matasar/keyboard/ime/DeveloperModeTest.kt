package net.matasar.keyboard.ime

import android.text.InputType
import android.view.KeyEvent
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.layout.CodeLayer
import net.matasar.keyboard.layout.DeveloperStrip
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.layout.LettersLayer
import net.matasar.keyboard.layout.ModifierKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeveloperModeTest {

    private val port = FakeEditorPort()
    private var now = 1000L
    private val controller = KeyboardController(InputDispatcher(port), clock = { now })

    private val ctrl = DeveloperStrip.keys.first { it.action == KeyAction.Modifier(ModifierKey.CTRL) }
    private val alt = DeveloperStrip.keys.first { it.action == KeyAction.Modifier(ModifierKey.ALT) }
    private val fn = DeveloperStrip.keys.first { it.action == KeyAction.Modifier(ModifierKey.FN) }
    private val left = DeveloperStrip.keys.first { it.label == "left" }
    private val c = LettersLayer.rows[2].keys[3]
    private val symbolsKey = LettersLayer.rows[3].keys[0]
    private val ctrlMeta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

    private fun terminal() {
        controller.onStartInput(android.view.inputmethod.EditorInfo().apply { inputType = InputType.TYPE_NULL })
    }

    @Test
    fun `ctrl c in a text field is the editor copy action and releases ctrl`() {
        controller.onStartInput(android.view.inputmethod.EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.onKey(ctrl)
        controller.onKey(c)
        assertEquals(listOf(android.R.id.copy), port.contextActions)
        assertTrue(port.keys.isEmpty())
        assertEquals(LatchState.IDLE, controller.modifiers.state(ModifierKey.CTRL))
    }

    @Test
    fun `ctrl c in a terminal is a key event with ctrl meta`() {
        terminal()
        controller.onKey(ctrl)
        controller.onKey(c)
        assertEquals(listOf(KeyEvent.KEYCODE_C to ctrlMeta), port.keys)
        assertTrue(port.contextActions.isEmpty())
    }

    @Test
    fun `ctrl on a cyrillic letter sends the key of its slot`() {
        val cyrillicC = Key("с", KeyAction.Letter("с", "С"), slot = 'c')
        val cyrillicF = Key("ф", KeyAction.Letter("ф", "Ф"), slot = 'a')
        controller.onStartInput(android.view.inputmethod.EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.onKey(ctrl)
        controller.onKey(cyrillicC)
        controller.onKey(ctrl)
        controller.onKey(cyrillicF)
        assertEquals(listOf(android.R.id.copy, android.R.id.selectAll), port.contextActions)
        controller.onKey(cyrillicC)
        assertEquals(listOf("с"), port.committed)
        terminal()
        controller.onKey(ctrl)
        controller.onKey(cyrillicC)
        assertEquals(listOf(KeyEvent.KEYCODE_C to ctrlMeta), port.keys)
    }

    @Test
    fun `shift and ctrl on a letter that sits on a punctuation slot carry shift meta`() {
        val yi = Key("ї", KeyAction.Letter("ї", "Ї"), slot = ']')
        terminal()
        controller.onKey(Key("shift", KeyAction.Shift))
        controller.onKey(ctrl)
        controller.onKey(yi)
        val shiftMeta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        assertEquals(listOf(KeyEvent.KEYCODE_RIGHT_BRACKET to (ctrlMeta or shiftMeta)), port.keys)
    }

    @Test
    fun `ctrl survives a layer switch so ctrl bracket works from the code page`() {
        terminal()
        controller.onKey(ctrl)
        controller.onKey(symbolsKey)
        controller.onKey(Key("#+=", KeyAction.SwitchLayer(LayerId.CODE)))
        assertEquals(LayerId.CODE, controller.layer)
        assertEquals("Ctrl · next key", controller.modifiers.chipText())
        controller.onKey(CodeLayer.rows[0].keys.first { it.label == "]" })
        assertEquals(listOf(KeyEvent.KEYCODE_RIGHT_BRACKET to ctrlMeta), port.keys)
        assertEquals(null, controller.modifiers.chipText())
    }

    @Test
    fun `two armed modifiers go out together and both release`() {
        terminal()
        controller.onKey(ctrl)
        controller.onKey(alt)
        assertEquals("Ctrl + Alt · next key", controller.modifiers.chipText())
        controller.onKey(c)
        val meta = port.keys.single().second
        assertTrue(meta and KeyEvent.META_CTRL_ON != 0 && meta and KeyEvent.META_ALT_ON != 0)
        assertEquals(null, controller.modifiers.chipText())
    }

    @Test
    fun `locked ctrl stays through several keys until tapped`() {
        terminal()
        controller.onKeyLongPress(ctrl)
        controller.onKey(c)
        controller.onKey(c)
        assertEquals(2, port.keys.size)
        assertEquals("Ctrl locked", controller.modifiers.chipText())
        now += 1000
        controller.onKey(ctrl)
        assertEquals(null, controller.modifiers.chipText())
    }

    @Test
    fun `holding ctrl and tapping c is a chord and the release is not a tap`() {
        terminal()
        controller.onModifierPressStart(ModifierKey.CTRL)
        controller.onKey(c)
        assertEquals(listOf(KeyEvent.KEYCODE_C to ctrlMeta), port.keys)
        controller.onModifierPressEnd(ModifierKey.CTRL)
        controller.onKey(ctrl) // the gesture's tap after the release
        assertEquals(LatchState.IDLE, controller.modifiers.state(ModifierKey.CTRL))
        controller.onKey(c)
        assertEquals("c", port.committed.single())
    }

    @Test
    fun `a long hold that chorded a key does not lock and does not swallow the next tap`() {
        terminal()
        controller.onModifierPressStart(ModifierKey.CTRL)
        controller.onKeyLongPress(ctrl) // the gesture's long press fires while still held
        controller.onKey(c)
        controller.onModifierPressEnd(ModifierKey.CTRL)
        assertEquals(listOf(KeyEvent.KEYCODE_C to ctrlMeta), port.keys)
        assertEquals(LatchState.IDLE, controller.modifiers.state(ModifierKey.CTRL))
        controller.onKey(ctrl)
        assertEquals(LatchState.ARMED, controller.modifiers.state(ModifierKey.CTRL))
    }

    @Test
    fun `a long hold with no chord locks on release`() {
        terminal()
        controller.onModifierPressStart(ModifierKey.CTRL)
        controller.onKeyLongPress(ctrl)
        assertEquals(LatchState.IDLE, controller.modifiers.state(ModifierKey.CTRL))
        controller.onModifierPressEnd(ModifierKey.CTRL)
        assertEquals(LatchState.LOCKED, controller.modifiers.state(ModifierKey.CTRL))
    }

    @Test
    fun `fn backspace in a terminal sends KEYCODE_FORWARD_DEL`() {
        terminal()
        controller.onKey(fn)
        controller.onKey(left)
        assertEquals(listOf(KeyEvent.KEYCODE_MOVE_HOME to 0), port.keys)
        controller.onKey(fn)
        controller.onKey(Key("backspace", KeyAction.Backspace))
        assertEquals(listOf(KeyEvent.KEYCODE_MOVE_HOME to 0, KeyEvent.KEYCODE_FORWARD_DEL to 0), port.keys)
        assertTrue(port.deletions.isEmpty())
    }

    @Test
    fun `fn backspace in a text field uses deleteSurroundingText`() {
        controller.onStartInput(android.view.inputmethod.EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.onKey(fn)
        controller.onKey(Key("backspace", KeyAction.Backspace))
        assertEquals(listOf(0 to 1), port.deletions)
        assertTrue(port.keys.isEmpty())
    }

    @Test
    fun `fn backspace with a selection clears the selection`() {
        terminal()
        port.selected = "hello"
        controller.onKey(fn)
        controller.onKey(Key("backspace", KeyAction.Backspace))
        assertEquals(listOf(""), port.committed)
        assertTrue(port.keys.isEmpty())
        assertTrue(port.deletions.isEmpty())
    }

    @Test
    fun `a new field releases every modifier`() {
        terminal()
        controller.onKeyLongPress(ctrl)
        controller.onStartInput(null)
        assertEquals(null, controller.modifiers.chipText())
    }

    @Test
    fun `developer strip arrow keys repeat on hold`() {
        val up = DeveloperStrip.keys.first { it.label == "up" }
        val down = DeveloperStrip.keys.first { it.label == "down" }
        val left = DeveloperStrip.keys.first { it.label == "left" }
        val right = DeveloperStrip.keys.first { it.label == "right" }

        assertTrue(controller.repeats(up))
        assertTrue(controller.repeats(down))
        assertTrue(controller.repeats(left))
        assertTrue(controller.repeats(right))

        controller.onKeyRepeat(left)
        controller.onKeyRepeat(right)
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_DPAD_LEFT to 0,
                KeyEvent.KEYCODE_DPAD_RIGHT to 0,
            ),
            port.keys,
        )
    }
}
