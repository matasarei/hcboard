package net.matasar.keyboard.macro

import android.view.KeyEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.layout.ModifierKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MacroRunnerTest {

    private val port = FakeEditorPort()
    private var clipboard: String? = "clip"
    private val textField = MacroField(terminal = false, editingShortcuts = true)
    private val terminal = MacroField(terminal = true, editingShortcuts = true)

    private val shift = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
    private val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

    private fun runner() = MacroRunner(InputDispatcher(port), { clipboard }, { Random(7) })

    private fun macro(vararg blocks: Block) = Macro("t", "Test", blocks.toList())

    @Test
    fun `text is committed as it is`() = runTest {
        runner().run(macro(Block.TypeText("Hello\nthere"), Block.TypeText("")), textField)
        assertEquals(listOf("Hello\nthere"), port.committed)
    }

    @Test
    fun `named keys send their codes`() = runTest {
        runner().run(macro(Block.PressKey("Esc"), Block.PressKey("F1"), Block.PressKey("Shift"), Block.PressKey("Nope")), textField)
        assertEquals(
            listOf(KeyEvent.KEYCODE_ESCAPE to 0, KeyEvent.KEYCODE_F1 to 0, KeyEvent.KEYCODE_SHIFT_LEFT to 0),
            port.keys,
        )
    }

    @Test
    fun `combinations carry every modifier's meta but not Fn`() = runTest {
        runner().run(macro(Block.PressKey("F1", setOf(ModifierKey.CTRL, ModifierKey.SHIFT, ModifierKey.FN))), textField)
        assertEquals(listOf(KeyEvent.KEYCODE_F1 to (ctrl or shift)), port.keys)
    }

    @Test
    fun `Ctrl+C is the editor's copy in a text field and a key event in a terminal`() = runTest {
        runner().run(macro(Block.PressKey("c", setOf(ModifierKey.CTRL))), textField)
        assertEquals(listOf(android.R.id.copy), port.contextActions)
        assertEquals(emptyList(), port.keys)

        runner().run(macro(Block.PressKey("c", setOf(ModifierKey.CTRL))), terminal)
        assertEquals(listOf(KeyEvent.KEYCODE_C to ctrl), port.keys)
    }

    @Test
    fun `Ctrl+C stays a key event with editing shortcuts off`() = runTest {
        runner().run(macro(Block.PressKey("c", setOf(ModifierKey.CTRL))), MacroField(terminal = false, editingShortcuts = false))
        assertEquals(emptyList(), port.contextActions)
        assertEquals(listOf(KeyEvent.KEYCODE_C to ctrl), port.keys)
    }

    @Test
    fun `random keys arrive as key events, never as committed text`() = runTest {
        runner().run(macro(Block.RandomKeys(length = 12, letters = false, symbols = false)), textField)
        assertEquals(emptyList(), port.committed)
        assertEquals(12, port.keys.size)
        assertTrue(port.keys.all { (code, meta) -> code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 && meta == 0 }, "${port.keys}")
    }

    @Test
    fun `capital letters and shifted symbols carry Shift`() = runTest {
        runner().run(macro(Block.RandomKeys(length = 64, digits = false)), textField)
        assertTrue(port.keys.any { it.second == shift }, "no shifted keys in ${port.keys}")
        assertTrue(port.keys.any { it.second == 0 }, "no plain keys in ${port.keys}")
    }

    @Test
    fun `a repeat plays its blocks that many times, nested too`() = runTest {
        runner().run(macro(Block.Repeat(3, listOf(Block.PressKey("Tab"), Block.Repeat(2, listOf(Block.TypeText("x")))))), textField)
        assertEquals(List(3) { KeyEvent.KEYCODE_TAB to 0 }, port.keys)
        assertEquals(List(6) { "x" }, port.committed)
    }

    @Test
    fun `repeats and waits are clamped`() {
        assertEquals(Block.Repeat.MAX_TIMES, MacroRunner.stepCount(listOf(Block.Repeat(1_000_000, listOf(Block.TypeText("x"))))))
        assertEquals(1, MacroRunner.stepCount(listOf(Block.Repeat(-5, listOf(Block.TypeText("x"))))))
        assertEquals(Block.Wait.MAX_MILLIS, Block.Wait(Long.MAX_VALUE).duration)
        assertEquals(0L, Block.Wait(-1).duration)
    }

    @Test
    fun `a macro past the step limit sends nothing`() = runTest {
        val tooLong = macro(Block.TypeText("a"), Block.Repeat(100, listOf(Block.Repeat(100, listOf(Block.PressKey("Tab"))))))
        assertFailsWith<MacroTooLong> { runner().run(tooLong, textField) }
        assertEquals(emptyList(), port.committed)
        assertEquals(emptyList(), port.keys)
    }

    @Test
    fun `exactly the step limit still plays`() {
        assertEquals(MacroRunner.MAX_STEPS, MacroRunner.expand(listOf(Block.Repeat(100, listOf(Block.Repeat(20, listOf(Block.PasteClipboard)))))).size)
        assertFailsWith<MacroTooLong> { MacroRunner.expand(listOf(Block.Repeat(100, listOf(Block.Repeat(20, listOf(Block.PasteClipboard)))), Block.PasteClipboard)) }
    }

    @Test
    fun `waits pause between steps`() = runTest {
        val job = launch { runner().run(macro(Block.TypeText("a"), Block.Wait(500), Block.TypeText("b")), textField) }
        runCurrent()
        assertEquals(listOf("a"), port.committed)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(listOf("a"), port.committed)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("a", "b"), port.committed)
        job.join()
    }

    @Test
    fun `cancelling stops the rest of the macro`() = runTest {
        val job = launch { runner().run(macro(Block.TypeText("a"), Block.Wait(1_000), Block.TypeText("b")), textField) }
        runCurrent()
        job.cancel()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf("a"), port.committed)
    }

    @Test
    fun `paste commits the clipboard and skips an empty one`() = runTest {
        runner().run(macro(Block.PasteClipboard), textField)
        clipboard = null
        runner().run(macro(Block.PasteClipboard), textField)
        clipboard = ""
        runner().run(macro(Block.PasteClipboard), textField)
        assertEquals(listOf("clip"), port.committed)
    }

    @Test
    fun `Tab and Enter wait for focus to move, other keys do not`() = runTest {
        val waits = mutableListOf<Int>()
        val runner = MacroRunner(InputDispatcher(port), { clipboard }, { Random(7) }, awaitFocusMove = { waits += port.keys.size })
        runner.run(macro(Block.PressKey("Esc"), Block.PressKey("Tab", setOf(ModifierKey.SHIFT)), Block.PressKey("F1"), Block.PressKey("Enter")), textField)
        assertEquals(listOf(2, 4), waits)
    }

    @Test
    fun `Ctrl with a capital letter is Ctrl+Shift, never the editor's action`() = runTest {
        runner().run(macro(Block.PressKey("C", setOf(ModifierKey.CTRL))), textField)
        assertEquals(emptyList(), port.contextActions)
        assertEquals(listOf(KeyEvent.KEYCODE_C to (ctrl or shift)), port.keys)
    }
}
