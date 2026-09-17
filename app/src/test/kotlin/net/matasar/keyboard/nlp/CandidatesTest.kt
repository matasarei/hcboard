package net.matasar.keyboard.nlp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CandidatesTest {

    private val list = WordList.of(
        "check" to 200, "checking" to 150, "checked" to 140, "chef" to 90, "chalk" to 50,
        "spell" to 100, "spelling" to 90, "spelled" to 120, "hello" to 200, "help" to 150,
        "the" to 255, "to" to 255, "rare" to 70, "rate" to 30,
    )
    private val candidates = Candidates(list)

    @Test
    fun `a misspelt word gets its most frequent one-edit neighbour as the correction`() {
        val result = assertNotNull(candidates.forWord("chek"))
        assertEquals("check", result.correction)
        assertEquals(listOf("chek", "check", "chef"), result.words)
    }

    @Test
    fun `a known word is completed and never corrected`() {
        val result = assertNotNull(candidates.forWord("spell"))
        assertNull(result.correction)
        assertEquals(listOf("spell", "spelled", "spelling"), result.words)
    }

    @Test
    fun `the typed case is applied to the candidates`() {
        assertEquals(listOf("Chek", "Check", "Chef"), candidates.forWord("Chek")!!.words)
        assertEquals("Check", candidates.forWord("Chek")!!.correction)
        assertEquals(listOf("CHEK", "CHECK", "CHEF"), candidates.forWord("CHEK")!!.words)
        assertEquals(listOf("Spell", "Spelled", "Spelling"), candidates.forWord("Spell")!!.words)
    }

    @Test
    fun `short words and rare neighbours are offered but never applied`() {
        assertNull(candidates.forWord("th")!!.correction)
        assertEquals(listOf("th", "the", "to"), candidates.forWord("th")!!.words)
        val rare = assertNotNull(candidates.forWord("rade"))
        assertNull(rare.correction)
        assertEquals(listOf("rade", "rare", "rate"), rare.words)
    }

    @Test
    fun `nothing for an empty word or a word with no neighbours and no completions`() {
        assertNull(candidates.forWord(""))
        assertNull(candidates.forWord("xyzzy"))
        assertNull(candidates.forWord("hello"))
    }

    @Test
    fun `at most three words, the typed one first, no duplicates`() {
        val result = assertNotNull(candidates.forWord("chec"))
        assertEquals(3, result.words.size)
        assertEquals("chec", result.words.first())
        assertEquals(result.words.size, result.words.toSet().size)
        assertEquals("check", result.correction)
    }

    @Test
    fun `transposed letters are one edit away`() {
        assertEquals("the", candidates.forWord("teh")!!.correction)
        assertEquals("hello", candidates.forWord("hlelo")!!.correction)
    }

    @Test
    fun `the bundled english and ukrainian lists behave the same way`() {
        val english = Candidates(File("src/main/assets/dictionaries/en_US.txt").bufferedReader().useLines { WordList.parse(it) })
        assertEquals("check", english.forWord("chek")!!.correction)
        val spell = english.forWord("Spell")!!
        assertNull(spell.correction)
        assertTrue("Spelling" in spell.words, "${spell.words}")
        val ukrainian = Candidates(File("src/main/assets/dictionaries/uk.txt").bufferedReader().useLines { WordList.parse(it) })
        val words = ukrainian.forWord("прив")!!.words
        assertTrue("привіт" in words, "$words")
    }
}
