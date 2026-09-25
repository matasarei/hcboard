package net.matasar.keyboard.nlp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordListTest {

    @Test
    fun `parses word and frequency lines and drops the rest`() {
        val list = WordList.parse(
            sequenceOf("hello\t200", "", "broken", "don't\t150", "x1\t10", "'tis\t5", "world\t999", "tab\tnotanumber", "what’s\t140"),
        )
        // An apostrophe between letters is part of the word, stored as '; one at an end is not.
        assertEquals(listOf("hello", "don't", "world", "what's"), list.words)
        assertEquals(140, list.frequency("what's"))
        assertEquals(200, list.frequency("hello"))
        assertEquals(255, list.frequency("world"))
        assertEquals(0, list.frequency("missing"))
    }

    @Test
    fun `the bundled english list is large, sorted by frequency and words only, any case`() {
        val file = File("src/main/assets/dictionaries/en_US.txt")
        assertTrue(file.exists(), "asset missing: ${file.absolutePath}")
        val list = file.bufferedReader().useLines { WordList.parse(it) }
        assertTrue(list.size > 30_000, "only ${list.size} words")
        assertEquals("the", list.words.first())
        assertTrue(list.frequency("hello") > 0)
        assertTrue(list.words.all { Apostrophes.isWord(it) })
        assertTrue(list.frequency("don't") > list.frequency("dont"), "don't is the word, not dont")
    }

    @Test
    fun `completions are the most frequent longer words with the prefix`() {
        val list = WordList.of("spell" to 100, "spelling" to 90, "spelled" to 120, "spelt" to 20, "spam" to 200, "s" to 255)
        assertEquals(listOf("spelled", "spell", "spelling"), list.completions("spel", 3))
        assertEquals(listOf("spelled", "spelling"), list.completions("spell", 3))
        assertEquals(listOf("spelled"), list.completions("spel", 1))
        assertEquals(emptyList(), list.completions("", 3))
        assertEquals(emptyList(), list.completions("xyz", 3))
        assertEquals(emptyList(), list.completions("spelling", 3))
        assertTrue(list.contains("spam"))
        assertTrue(!list.contains("spa"))
    }

    @Test
    fun `completions on the bundled english list rank by frequency`() {
        val list = File("src/main/assets/dictionaries/en_US.txt").bufferedReader().useLines { WordList.parse(it) }
        val completions = list.completions("spell", 3)
        assertTrue("spelling" in completions, "$completions")
        assertTrue(completions.zipWithNext().all { (a, b) -> list.frequency(a) >= list.frequency(b) }, "$completions")
        assertTrue(completions.none { it == "spell" })
    }
}
