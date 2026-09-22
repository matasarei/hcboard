package net.matasar.keyboard.input.glide

import net.matasar.keyboard.nlp.WordList
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlideClassifierTest {

    private val smallList = WordList.of(
        "hello" to 200, "hell" to 120, "help" to 180, "halo" to 60, "hollow" to 90, "hero" to 110,
        "world" to 190, "word" to 170, "would" to 210, "the" to 222, "to" to 215, "of" to 214,
    )

    private fun classifier(list: WordList = smallList) = GlideClassifier(list).apply { setLayout(QwertyGeometry.keys) }

    @Test
    fun `a path through h e l o ranks hello first`() {
        val suggestions = classifier().classify(QwertyGeometry.path("helo"), 3)
        assertEquals("hello", suggestions.first(), "got $suggestions")
    }

    @Test
    fun `a path through w o r l d ranks world first`() {
        val suggestions = classifier().classify(QwertyGeometry.path("world"), 3)
        assertEquals("world", suggestions.first(), "got $suggestions")
    }

    @Test
    fun `no layout or an empty path yields nothing`() {
        assertTrue(GlideClassifier(smallList).classify(QwertyGeometry.path("helo"), 3).isEmpty())
        assertTrue(classifier().classify(emptyList(), 3).isEmpty())
    }

    /**
     * A glide is classified against the whole shipped list while the finger is still moving, so a
     * classification has to stay well inside a keystroke. The median is what the assertion rests
     * on: a single run carries whatever pause the machine had (a CI runner once measured 115 ms
     * where this machine takes 6 to 14), and one pause is not a slowdown. The cap catches the
     * regression the median would miss only if everything got slower together.
     */
    @Test
    fun `the bundled english list recognises common words fast enough`() {
        val list = File("src/main/assets/dictionaries/en_US.txt").bufferedReader().useLines { WordList.parse(it) }
        val classifier = classifier(list)
        val words = listOf("hello" to "helo", "world" to "world", "keyboard" to "keyboard", "thanks" to "thanks", "morning" to "morning")
        // Warm up on every path, so no run below is the first of its shape.
        for ((_, path) in words) classifier.classify(QwertyGeometry.path(path), 3)
        val millis = words.map { (expected, path) ->
            val start = System.nanoTime()
            val suggestions = classifier.classify(QwertyGeometry.path(path), 3)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            assertTrue(expected in suggestions, "$expected not in $suggestions for '$path'")
            elapsed
        }.sorted()
        val median = millis[millis.size / 2]
        println("glide: classifications $millis ms over ${list.size} words")
        assertTrue(median < MEDIAN_BUDGET_MS, "median classification took $median ms, all of $millis")
        assertTrue(millis.last() < SLOWEST_BUDGET_MS, "slowest classification took ${millis.last()} ms, all of $millis")
    }

    private companion object {
        /** What a classification has to stay inside on a phone, and does with room to spare here. */
        const val MEDIAN_BUDGET_MS = 100

        /** Loose enough for a machine that paused, tight enough to catch everything getting slower. */
        const val SLOWEST_BUDGET_MS = 400
    }

}
