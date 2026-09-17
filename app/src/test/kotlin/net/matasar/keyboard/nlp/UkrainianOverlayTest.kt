package net.matasar.keyboard.nlp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The curated everyday-Ukrainian overlay (`scripts/wordlists/uk-everyday.tsv`) and the asset it
 * is merged into. The test working directory is the `app` module, as in the glide tests.
 */
class UkrainianOverlayTest {

    private val overlay = File("../scripts/wordlists/uk-everyday.tsv")
    private val asset = File("src/main/assets/dictionaries/uk.txt")

    private fun entries(): List<Pair<String, Int>> = overlay.readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val (word, frequency) = line.split('\t')
            word to frequency.toInt()
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
        assertTrue(short.isEmpty(), "regenerate uk.txt with the overlay; below their tier: ${short.take(10)}")
    }
}
