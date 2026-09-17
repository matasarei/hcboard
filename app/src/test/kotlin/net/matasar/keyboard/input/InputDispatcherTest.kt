package net.matasar.keyboard.input

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputDispatcherTest {

    @Test
    fun `backspace deletes one character`() {
        val port = FakeEditorPort(before = "ab")
        InputDispatcher(port).backspace()
        assertEquals(listOf(1 to 0), port.deletions)
    }

    @Test
    fun `backspace deletes a whole emoji`() {
        val port = FakeEditorPort(before = "a😀")
        InputDispatcher(port).backspace()
        assertEquals(listOf(2 to 0), port.deletions)
    }

    @Test
    fun `backspace on a selection replaces it with nothing`() {
        val port = FakeEditorPort(before = "abc", selected = "bc")
        InputDispatcher(port).backspace()
        assertEquals(listOf(""), port.committed)
        assertTrue(port.deletions.isEmpty())
    }

    @Test
    fun `enter performs the editor action when the field has one`() {
        val port = FakeEditorPort()
        InputDispatcher(port).enter(editorActionId = 3)
        assertEquals(listOf(3), port.editorActions)
        assertTrue(port.keys.isEmpty())
    }

    @Test
    fun `enter falls back to a key event when the action is refused or absent`() {
        val refused = FakeEditorPort(acceptsEditorAction = false)
        InputDispatcher(refused).enter(editorActionId = 3)
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER to 0), refused.keys)

        val none = FakeEditorPort()
        InputDispatcher(none).enter()
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER to 0), none.keys)
    }

    @Test
    fun `the word before the cursor is its trailing letters, capped`() {
        assertEquals("chek", InputDispatcher(FakeEditorPort(before = "Spell chek")).wordBeforeCursor())
        assertEquals("", InputDispatcher(FakeEditorPort(before = "Spell ")).wordBeforeCursor())
        assertEquals("", InputDispatcher(FakeEditorPort(before = "done.")).wordBeforeCursor())
        assertEquals("t", InputDispatcher(FakeEditorPort(before = "don't")).wordBeforeCursor())
        assertEquals("привіт", InputDispatcher(FakeEditorPort(before = "hi привіт")).wordBeforeCursor())
        assertEquals("", InputDispatcher(FakeEditorPort()).wordBeforeCursor())
        assertEquals(MAX_WORD_LENGTH, InputDispatcher(FakeEditorPort(before = "a".repeat(40))).wordBeforeCursor().length)
    }

    @Test
    fun `replacing the word before the cursor deletes its length and commits the new text`() {
        val port = FakeEditorPort(before = "Spell chek")
        InputDispatcher(port).replaceWordBeforeCursor("chek", "check ")
        assertEquals(listOf(4 to 0), port.deletions)
        assertEquals(listOf("check "), port.committed)
        assertEquals("Spell check ", port.before)
    }
}
