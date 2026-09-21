package net.matasar.keyboard.macro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class MacroEditsTest {

    private val a = Block.TypeText("a")
    private val b = Block.TypeText("b")
    private val tab = Block.PressKey("Tab")
    private val stack = listOf(a, Block.Repeat(2, listOf(tab, b)), b)

    @Test
    fun `insert appends at the top level or inside a repeat`() {
        assertEquals(listOf(a, Block.Repeat(2, listOf(tab, b)), b, tab), MacroEdits.insert(stack, emptyList(), tab))
        assertEquals(listOf(a, Block.Repeat(2, listOf(tab, b, a)), b), MacroEdits.insert(stack, listOf(1), a))
        assertEquals(listOf(tab, a, Block.Repeat(2, listOf(tab, b)), b), MacroEdits.insert(stack, emptyList(), tab, index = 0))
    }

    @Test
    fun `remove and replace reach inside a repeat`() {
        assertEquals(listOf(a, Block.Repeat(2, listOf(b)), b), MacroEdits.remove(stack, listOf(1, 0)))
        assertEquals(listOf(a, b), MacroEdits.remove(stack, listOf(1)))
        assertEquals(listOf(a, Block.Repeat(2, listOf(a, b)), b), MacroEdits.replace(stack, listOf(1, 0), a))
    }

    @Test
    fun `move shifts within its own list and stops at either end`() {
        assertEquals(listOf(Block.Repeat(2, listOf(tab, b)), a, b), MacroEdits.move(stack, listOf(0), 1))
        assertEquals(listOf(a, Block.Repeat(2, listOf(b, tab)), b), MacroEdits.move(stack, listOf(1, 0), 1))
        assertSame(stack, MacroEdits.move(stack, listOf(0), -1))
        assertSame(stack, MacroEdits.move(stack, listOf(2), 1))
    }

    @Test
    fun `a path that leads nowhere changes nothing`() {
        assertSame(stack, MacroEdits.insert(stack, listOf(0), tab))
        assertSame(stack, MacroEdits.insert(stack, listOf(9), tab))
        assertSame(stack, MacroEdits.remove(stack, listOf(1, 5)))
        assertSame(stack, MacroEdits.remove(stack, emptyList()))
        assertSame(stack, MacroEdits.replace(stack, listOf(7), a))
    }

    @Test
    fun `blockAt follows the path`() {
        assertEquals(b, MacroEdits.blockAt(stack, listOf(1, 1)))
        assertNull(MacroEdits.blockAt(stack, listOf(0, 0)))
        assertNull(MacroEdits.blockAt(stack, emptyList()))
    }
}
