package net.matasar.keyboard.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.LettersLayer
import net.matasar.keyboard.macro.Block
import net.matasar.keyboard.macro.Macro
import net.matasar.keyboard.nlp.Candidates
import net.matasar.keyboard.nlp.WordList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MacroControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private val port = FakeEditorPort()
    private val controller = KeyboardController(InputDispatcher(port), clock = { 1000L }, main = dispatcher).apply {
        scope = test
        macroRandom = { Random(1) }
        clipboardText = { "from clipboard" }
        candidateEngine = Candidates(WordList.of("hello" to 200, "help" to 150))
        onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
    }

    private fun macro(vararg blocks: Block) = Macro("m", "Test", blocks.toList())

    @Test
    fun `a macro plays into the field and closes the sheet`() {
        controller.toggleMacroSheet()
        controller.runMacro(macro(Block.TypeText("hi "), Block.PasteClipboard, Block.PressKey("Enter")))
        assertFalse(controller.macroSheetOpen)
        assertEquals("m", controller.runningMacro)
        test.advanceUntilIdle()
        assertEquals(listOf("hi ", "from clipboard"), port.committed)
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER to 0), port.keys)
        assertNull(controller.runningMacro)
    }

    @Test
    fun `what a macro types feeds no candidates while it runs`() {
        controller.runMacro(macro(Block.TypeText("hel"), Block.Wait(100)))
        test.runCurrent()
        controller.onSelectionChanged()
        assertNull(controller.candidates)
        test.advanceUntilIdle()
    }

    @Test
    fun `after random keys nothing is read back for the strip`() {
        controller.runMacro(macro(Block.RandomKeys(length = 8)))
        test.advanceUntilIdle()
        val reads = port.textReads
        controller.onSelectionChanged()
        assertNull(controller.candidates)
        assertEquals(reads, port.textReads)
    }

    @Test
    fun `leaving the field stops a running macro`() {
        controller.runMacro(macro(Block.TypeText("a"), Block.Wait(1_000), Block.TypeText("b")))
        test.runCurrent()
        controller.onFinishInput()
        assertNull(controller.runningMacro)
        test.advanceUntilIdle()
        assertEquals(listOf("a"), port.committed)
    }

    @Test
    fun `stop cancels the rest, a new macro replaces a running one`() {
        controller.runMacro(macro(Block.TypeText("a"), Block.Wait(1_000), Block.TypeText("b")))
        test.runCurrent()
        controller.runMacro(Macro("n", "Other", listOf(Block.TypeText("c"))))
        assertEquals("n", controller.runningMacro)
        test.advanceUntilIdle()
        assertEquals(listOf("a", "c"), port.committed)
        assertNull(controller.runningMacro)

        controller.runMacro(macro(Block.Wait(1_000), Block.TypeText("d")))
        controller.stopMacro()
        test.advanceUntilIdle()
        assertEquals(listOf("a", "c"), port.committed)
    }

    @Test
    fun `a macro that is too long plays nothing and ends`() {
        controller.runMacro(macro(Block.Repeat(100, listOf(Block.Repeat(100, listOf(Block.TypeText("x")))))))
        test.advanceUntilIdle()
        assertEquals(emptyList(), port.committed)
        assertNull(controller.runningMacro)
    }

    @Test
    fun `the two sheets never stand open together`() {
        controller.toggleManagerSheet()
        controller.toggleMacroSheet()
        assertTrue(controller.macroSheetOpen)
        assertFalse(controller.managerSheetOpen)
        controller.toggleManagerSheet()
        assertTrue(controller.managerSheetOpen)
        assertFalse(controller.macroSheetOpen)
    }

    @Test
    fun `a key typed after a macro works as ever`() {
        controller.runMacro(macro(Block.TypeText("x")))
        test.advanceUntilIdle()
        controller.onKey(LettersLayer.rows.flatMap { it.keys }.first { it.action == KeyAction.Space })
        assertEquals(listOf("x", " "), port.committed)
    }
}
