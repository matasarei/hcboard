package net.matasar.keyboard.nlp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordListTest {

    @Test
    fun `parses word and frequency lines and drops the rest`() {
        val list = WordList.parse(sequenceOf("hello\t200", "", "broken", "don't\t150", "x1\t10", "world\t999", "tab\tnotanumber"))
        assertEquals(listOf("hello", "world"), list.words)
        assertEquals(200, list.frequency("hello"))
        assertEquals(255, list.frequency("world"))
        assertEquals(0, list.frequency("missing"))
    }

    @Test
    fun `the bundled english list is large, sorted by frequency and letters only, any case`() {
        val file = File("src/main/assets/dictionaries/en_US.txt")
        assertTrue(file.exists(), "asset missing: ${file.absolutePath}")
        val list = file.bufferedReader().useLines { WordList.parse(it) }
        assertTrue(list.size > 30_000, "only ${list.size} words")
        assertEquals("the", list.words.first())
        assertTrue(list.frequency("hello") > 0)
        assertTrue(list.words.all { word -> word.all { it.isLetter() } })
    }
}
