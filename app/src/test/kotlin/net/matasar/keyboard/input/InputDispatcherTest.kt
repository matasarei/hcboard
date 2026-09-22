package net.matasar.keyboard.input

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `forward delete deletes one character`() {
        val port = FakeEditorPort(after = "ab")
        InputDispatcher(port).forwardDelete()
        assertEquals(listOf(0 to 1), port.deletions)
    }

    @Test
    fun `forward delete deletes a whole emoji`() {
        val port = FakeEditorPort(after = "😀b")
        InputDispatcher(port).forwardDelete()
        assertEquals(listOf(0 to 2), port.deletions)
    }

    @Test
    fun `forward delete on a selection replaces it with nothing`() {
        val port = FakeEditorPort(after = "abc", selected = "bc")
        InputDispatcher(port).forwardDelete()
        assertEquals(listOf(""), port.committed)
        assertTrue(port.deletions.isEmpty())
    }

    @Test
    fun `forward delete in terminal sends KEYCODE_FORWARD_DEL`() {
        val port = FakeEditorPort(after = "abc")
        InputDispatcher(port).forwardDelete(terminal = true)
        assertEquals(listOf(KeyEvent.KEYCODE_FORWARD_DEL to 0), port.keys)
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
        assertEquals("", InputDispatcher(FakeEditorPort(before = "che", after = "ck")).wordBeforeCursor())
        assertEquals("che", InputDispatcher(FakeEditorPort(before = "che", after = " ck")).wordBeforeCursor())
    }

    @Test
    fun `replacing the word before the cursor deletes its length and commits the new text`() {
        val port = FakeEditorPort(before = "Spell chek")
        InputDispatcher(port).replaceWordBeforeCursor("chek", "check ")
        assertEquals(listOf(4 to 0), port.deletions)
        assertEquals(listOf("check "), port.committed)
        assertEquals("Spell check ", port.before)
    }

    @Test
    fun `a word is replaced in one batch, so the app never sees it half done`() {
        val port = FakeEditorPort(before = "Spell chek")
        InputDispatcher(port).replaceWordBeforeCursor("chek", "check")
        assertEquals(listOf("begin", "delete:4,0", "commit:check", "end"), port.edits)
        assertEquals(0, port.batchDepth)
    }

    @Test
    fun `a batch is closed even when an edit inside it throws`() {
        val port = FakeEditorPort()
        runCatching { InputDispatcher(port).batch { error("the connection went away") } }
        assertEquals(listOf("begin", "end"), port.edits)
        assertEquals(0, port.batchDepth)
    }

    @Test
    fun `a word needs a space in front of it only after something that ends a word`() {
        for (text in listOf("hello", "hello,", "hello.", "hello!", "hello?", "don't", "(hello)", "7", "\u201chello\u201d", "\u00abhello\u00bb")) {
            val port = FakeEditorPort(before = text)
            assertTrue(InputDispatcher(port).needsSpaceBefore(), "after $text")
        }
        for (text in listOf("", "hello ", "hello\n", "(", "[", "well-", "path/")) {
            val port = FakeEditorPort(before = text)
            assertFalse(InputDispatcher(port).needsSpaceBefore(), "after $text")
        }
    }

    @Test
    fun `a field that asks for no capitals is not asked about the cursor`() {
        val port = FakeEditorPort()
        assertFalse(InputDispatcher(port).capitalAtCursor(0))
        assertEquals(0, port.capsQueries)
    }

    @Test
    fun `a sentence field wants a capital at the start and after a full stop`() {
        val sentences = android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        assertTrue(InputDispatcher(FakeEditorPort()).capitalAtCursor(sentences))
        assertTrue(InputDispatcher(FakeEditorPort(before = "hi. ")).capitalAtCursor(sentences))
        assertFalse(InputDispatcher(FakeEditorPort(before = "hi ")).capitalAtCursor(sentences))
    }
}
