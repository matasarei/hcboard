package net.matasar.keyboard.macro

import net.matasar.keyboard.input.keyStrokeFor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RandomKeysTest {

    @Test
    fun `the length is the block's, clamped`() {
        assertEquals(16, randomKeys(Block.RandomKeys(), Random(1)).length)
        assertEquals(Block.RandomKeys.MIN_LENGTH, randomKeys(Block.RandomKeys(length = 0), Random(1)).length)
        assertEquals(Block.RandomKeys.MAX_LENGTH, randomKeys(Block.RandomKeys(length = 10_000), Random(1)).length)
    }

    @Test
    fun `only the chosen sets are used`() {
        repeat(50) { seed ->
            val digits = randomKeys(Block.RandomKeys(length = 8, letters = false, symbols = false), Random(seed))
            assertTrue(digits.all { it in RandomKeySets.DIGITS }, digits)
            val letters = randomKeys(Block.RandomKeys(length = 8, digits = false, symbols = false), Random(seed))
            assertTrue(letters.all { it in RandomKeySets.LETTERS }, letters)
        }
    }

    @Test
    fun `every chosen set appears at least once`() {
        repeat(200) { seed ->
            val text = randomKeys(Block.RandomKeys(length = 4), Random(seed))
            assertTrue(text.any { it in RandomKeySets.LETTERS }, text)
            assertTrue(text.any { it in RandomKeySets.DIGITS }, text)
            assertTrue(text.any { it in RandomKeySets.SYMBOLS }, text)
        }
    }

    @Test
    fun `no set chosen counts as all three`() {
        val text = randomKeys(Block.RandomKeys(length = 6, letters = false, digits = false, symbols = false), Random(3))
        assertTrue(text.any { it in RandomKeySets.LETTERS } && text.any { it in RandomKeySets.DIGITS } && text.any { it in RandomKeySets.SYMBOLS }, text)
    }

    @Test
    fun `the same seed gives the same keys, another seed others`() {
        assertEquals(randomKeys(Block.RandomKeys(), Random(42)), randomKeys(Block.RandomKeys(), Random(42)))
        assertNotEquals(randomKeys(Block.RandomKeys(), Random(42)), randomKeys(Block.RandomKeys(), Random(43)))
    }

    @Test
    fun `every character of every set has a key stroke`() {
        for (c in RandomKeySets.LETTERS + RandomKeySets.DIGITS + RandomKeySets.SYMBOLS) assertNotNull(keyStrokeFor(c), "'$c'")
    }
}
