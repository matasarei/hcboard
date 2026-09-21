package net.matasar.keyboard.macro

import kotlin.test.Test
import kotlin.test.assertEquals

class MacroStoreDefaultsTest {

    @Test
    fun `the only default is the password generator, sixteen keys from every set`() {
        assertEquals(listOf(MacroStore.passwordGenerator), MacroStore.defaults)
        assertEquals("Password generator", MacroStore.passwordGenerator.name)
        assertEquals(listOf<Block>(Block.RandomKeys(16, letters = true, digits = true, symbols = true)), MacroStore.passwordGenerator.blocks)
    }

    @Test
    fun `the defaults survive the codec`() {
        assertEquals(MacroStore.defaults, MacroJson.decode(MacroJson.encode(MacroStore.defaults)))
    }
}
