package net.matasar.keyboard.input

import android.view.KeyEvent
import net.matasar.keyboard.layout.ModifierKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModifierStateTest {

    @Test
    fun `tap arms, a key consumes it, locked stays`() {
        var m = Modifiers().tap(ModifierKey.CTRL, 1000)
        assertTrue(m.isActive(ModifierKey.CTRL))
        m = m.consume()
        assertFalse(m.isActive(ModifierKey.CTRL))

        m = Modifiers().tap(ModifierKey.CTRL, 1000).tap(ModifierKey.CTRL, 1100)
        assertEquals(LatchState.LOCKED, m.state(ModifierKey.CTRL))
        assertEquals(LatchState.LOCKED, m.consume().state(ModifierKey.CTRL))
        assertEquals(LatchState.IDLE, m.tap(ModifierKey.CTRL, 5000).state(ModifierKey.CTRL))
    }

    @Test
    fun `held counts as active and survives consume`() {
        val m = Modifiers().hold(ModifierKey.ALT)
        assertTrue(m.isActive(ModifierKey.ALT))
        assertTrue(m.consume().isActive(ModifierKey.ALT))
        assertFalse(m.releaseHold(ModifierKey.ALT).isActive(ModifierKey.ALT))
    }

    @Test
    fun `meta state carries every active modifier except Fn`() {
        val m = Modifiers().tap(ModifierKey.CTRL, 0).tap(ModifierKey.SHIFT, 0).tap(ModifierKey.FN, 0)
        val meta = m.metaState()
        assertTrue(meta and KeyEvent.META_CTRL_ON != 0)
        assertTrue(meta and KeyEvent.META_CTRL_LEFT_ON != 0)
        assertTrue(meta and KeyEvent.META_SHIFT_ON != 0)
        assertTrue(meta and KeyEvent.META_ALT_ON == 0)
        assertEquals(0, Modifiers().tap(ModifierKey.FN, 0).metaState())
    }

    @Test
    fun `chip text reads like the mocks`() {
        assertNull(Modifiers().chipText())
        assertEquals("Ctrl · next key", Modifiers().tap(ModifierKey.CTRL, 0).chipText())
        assertEquals("Ctrl + Alt · next key", Modifiers().tap(ModifierKey.CTRL, 0).tap(ModifierKey.ALT, 0).chipText())
        assertEquals("Ctrl locked", Modifiers().longPress(ModifierKey.CTRL).chipText())
        assertEquals("Ctrl locked + Shift · next key", Modifiers().longPress(ModifierKey.CTRL).tap(ModifierKey.SHIFT, 0).chipText())
        assertEquals("Alt · next key", Modifiers().hold(ModifierKey.ALT).chipText())
    }

    @Test
    fun `active order is Ctrl Alt Shift Meta Fn`() {
        val m = Modifiers().tap(ModifierKey.FN, 0).tap(ModifierKey.SHIFT, 0).tap(ModifierKey.CTRL, 0)
        assertEquals(listOf(ModifierKey.CTRL, ModifierKey.SHIFT, ModifierKey.FN), m.active)
        assertTrue(m.anyMetaActive)
        assertFalse(Modifiers().tap(ModifierKey.FN, 0).anyMetaActive)
    }

    @Test
    fun `metaStateOf gives each modifier its left-hand flags and Fn none`() {
        assertEquals(0, metaStateOf(emptySet()))
        assertEquals(0, metaStateOf(setOf(ModifierKey.FN)))
        assertEquals(
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
            metaStateOf(setOf(ModifierKey.CTRL, ModifierKey.SHIFT, ModifierKey.FN)),
        )
    }
}
