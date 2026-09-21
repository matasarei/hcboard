package net.matasar.keyboard.nlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WordListOverridesTest {

    private val list = WordList.of("cube" to 120, "cubes" to 90, "duck" to 200, "ducks" to 150, "the" to 255)

    @Test
    fun `no overrides leave the very same list`() {
        assertSame(list, list.withOverrides(emptyMap()))
    }

    @Test
    fun `an added word joins the list and a raised one takes its new frequency`() {
        val over = list.withOverrides(mapOf("kubectl" to 230, "cubes" to 250))
        assertEquals(230, over.frequency("kubectl"))
        assertEquals(250, over.frequency("cubes"))
        assertEquals(listOf("cubes"), over.completions("cube", 3))
        // The original is untouched.
        assertFalse(list.contains("kubectl"))
    }

    @Test
    fun `a blocked word is gone and a frequency past the scale is capped`() {
        val over = list.withOverrides(mapOf("ducks" to 0, "the" to 999))
        assertFalse(over.contains("ducks"))
        assertEquals(emptyList(), over.completions("duck", 3))
        assertEquals(255, over.frequency("the"))
        assertEquals(4, over.size)
    }

    @Test
    fun `an added word is completed and not corrected away`() {
        val candidates = Candidates(list.withOverrides(mapOf("kubectl" to 230)))
        assertTrue("kubectl" in assertNotNull(candidates.forWord("kube")).words)
        assertNull(candidates.forWord("kubectl")?.correction)
    }

    @Test
    fun `a blocked word is never offered as a correction`() {
        // "duxk" is one edit from "duck"; blocked, it is not offered.
        assertEquals("duck", Candidates(list).forWord("duxk")?.correction)
        val result = Candidates(list.withOverrides(mapOf("duck" to 0))).forWord("duxk")
        assertTrue(result == null || "duck" !in result.words)
    }

    @Test
    fun `a block takes the word out whatever its case`() {
        val over = list.withOverrides(mapOf("Duck" to 0, "THE" to 0))
        assertFalse(over.contains("duck"))
        assertFalse(over.contains("the"))
        assertTrue(over.contains("ducks"))
    }

    @Test
    fun `adding a word the list ranks higher never lowers it`() {
        val over = list.withOverrides(mapOf("the" to CustomWord.ADDED, "duck" to 210))
        assertEquals(255, over.frequency("the"))
        assertEquals(210, over.frequency("duck"))
    }
}
