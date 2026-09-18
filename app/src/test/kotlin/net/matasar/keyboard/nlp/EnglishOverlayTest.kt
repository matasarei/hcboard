package net.matasar.keyboard.nlp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The curated modern-English overlay (`scripts/wordlists/en-modern.tsv`) and the asset it
 * is merged into. The test working directory is the `app` module, as in the glide tests.
 */
class EnglishOverlayTest {

    private val overlay = File("../scripts/wordlists/en-modern.tsv")
    private val asset = File("src/main/assets/dictionaries/en_US.txt")

    private fun entries(): List<Pair<String, Int>> = overlay.readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val parts = line.split('\t')
            assertEquals(2, parts.size, "expected 'word<TAB>frequency', got '$line'")
            parts[0] to parts[1].toInt()
        }

    @Test
    fun `the overlay is lowercase letters in the three tiers with no duplicates`() {
        val entries = entries()
        assertTrue(entries.size >= 250, "only ${entries.size} entries")
        for ((word, frequency) in entries) {
            assertTrue(word.all { it.isLetter() && it.isLowerCase() }, "'$word' is not lowercase letters")
            assertTrue(frequency in setOf(200, 180, 165), "'$word' has tier $frequency")
        }
        assertEquals(entries.size, entries.map { it.first }.toSet().size, "duplicate words")
    }

    @Test
    fun `every overlay word is in the asset at no less than its overlay frequency`() {
        val list = asset.bufferedReader().useLines { WordList.parse(it) }
        val short = entries().filter { (word, frequency) -> list.frequency(word) < frequency }
        assertTrue(short.isEmpty(), "regenerate en_US.txt with the overlay; below their tier: ${short.take(10)}")
    }
}
