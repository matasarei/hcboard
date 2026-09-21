package net.matasar.keyboard.nlp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A curated overlay in `scripts/wordlists/` and the asset it is merged into by
 * `scripts/build-wordlist.py --boost`. The test working directory is the `app` module, as in
 * the glide tests. [tiers] are the overlay's allowed frequencies, which differ per asset
 * because each corpus has its own scale. [capitalisedNouns] lets a word start with a capital, as
 * German nouns do in their list; a lowercase copy would never be offered in its right case.
 */
abstract class DictionaryOverlayTest(
    overlay: String,
    asset: String,
    private val tiers: Set<Int>,
    private val minEntries: Int,
    private val capitalisedNouns: Boolean = false,
) {

    private val overlay = File("../scripts/wordlists/$overlay")
    private val asset = File("src/main/assets/dictionaries/$asset")

    private fun entries(): List<Pair<String, Int>> = overlay.readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val parts = line.split('\t')
            assertEquals(2, parts.size, "expected 'word<TAB>frequency', got '$line'")
            parts[0] to parts[1].toInt()
        }

    @Test
    fun `the overlay is lowercase letters in its tiers with no duplicates`() {
        val entries = entries()
        assertTrue(entries.size >= minEntries, "only ${entries.size} entries")
        for ((word, frequency) in entries) {
            val cased = word.drop(1).all { it.isLowerCase() } && (capitalisedNouns || word.first().isLowerCase())
            assertTrue(word.all { it.isLetter() } && cased, "'$word' is not lowercase letters")
            assertTrue(frequency in tiers, "'$word' has tier $frequency")
        }
        assertEquals(entries.size, entries.map { it.first }.toSet().size, "duplicate words")
    }

    @Test
    fun `every overlay word is in the asset at no less than its overlay frequency`() {
        val list = asset.bufferedReader().useLines { WordList.parse(it) }
        val short = entries().filter { (word, frequency) -> list.frequency(word) < frequency }
        assertTrue(short.isEmpty(), "regenerate ${asset.name} with the overlay; below their tier: ${short.take(10)}")
    }
}
