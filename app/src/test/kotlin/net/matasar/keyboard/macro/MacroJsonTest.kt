package net.matasar.keyboard.macro

import net.matasar.keyboard.layout.ModifierKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacroJsonTest {

    private val everyBlock = Macro(
        id = "m1",
        name = "Everything",
        blocks = listOf(
            Block.TypeText("hello\n"),
            Block.PressKey("F1", setOf(ModifierKey.CTRL, ModifierKey.SHIFT)),
            Block.RandomKeys(length = 8, letters = false, digits = true, symbols = false),
            Block.Repeat(3, listOf(Block.PressKey("Tab"), Block.Repeat(2, listOf(Block.Wait(100))))),
            Block.Wait(250),
            Block.PasteClipboard,
        ),
    )

    @Test
    fun `every block kind survives a round trip, nested repeats included`() {
        assertEquals(listOf(everyBlock), MacroJson.decode(MacroJson.encode(listOf(everyBlock))))
    }

    @Test
    fun `blocks are tagged by a readable type and keys by name`() {
        val text = MacroJson.encode(listOf(everyBlock))
        assertTrue("\"version\":1" in text, text)
        assertTrue("\"type\":\"key\"" in text, text)
        assertTrue("\"key\":\"F1\"" in text, text)
        assertTrue("\"type\":\"paste\"" in text, text)
    }

    @Test
    fun `unknown fields are ignored`() {
        val text = """{"version":2,"future":true,"macros":[{"id":"a","name":"A","colour":"red",
            "blocks":[{"type":"text","text":"hi","speed":3}]}]}"""
        assertEquals(listOf(Macro("a", "A", listOf(Block.TypeText("hi")))), MacroJson.decode(text))
    }

    @Test
    fun `unreadable text decodes to null`() {
        assertNull(MacroJson.decode("not json"))
        assertNull(MacroJson.decode("""{"macros":[{"id":"a","name":"A","blocks":[{"type":"teleport"}]}]}"""))
        assertNull(MacroJson.decode(""))
    }
}
