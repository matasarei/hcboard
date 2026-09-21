package net.matasar.keyboard.macro

import net.matasar.keyboard.layout.ModifierKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MacroSummaryTest {

    @Test
    fun `blocks read in a few words`() {
        assertEquals("Press Ctrl+Shift+F1", Block.PressKey("F1", setOf(ModifierKey.SHIFT, ModifierKey.CTRL)).summary())
        assertEquals("Press Esc", Block.PressKey("Esc").summary())
        assertEquals("Type “:wq↵”", Block.TypeText(":wq\n").summary())
        assertEquals("Type “abcdefghijklmnop…”", Block.TypeText("abcdefghijklmnopq").summary())
        assertEquals("Type ••••••", Block.TypeText("hunter2", secret = true).summary())
        assertEquals("Type ••••••", Block.TypeText("a much longer secret than six", secret = true).summary())
        assertEquals("Random keys · 16", Block.RandomKeys().summary())
        assertEquals("Repeat 3×", Block.Repeat(3).summary())
        assertEquals("Wait 250 ms", Block.Wait(250).summary())
        assertEquals("Paste clipboard", Block.PasteClipboard.summary())
    }

    @Test
    fun `a macro shows its first two blocks`() {
        assertEquals("Empty", Macro("a", "A", emptyList()).summary())
        assertEquals("Random keys · 16", MacroStore.passwordGenerator.summary())
        assertEquals(
            "Press Esc · Type “:wq” · …",
            Macro("a", "A", listOf(Block.PressKey("Esc"), Block.TypeText(":wq"), Block.PressKey("Enter"))).summary(),
        )
    }
}
