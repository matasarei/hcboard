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

    @Test
    fun `the bundled english list recognises common words fast enough`() {
        val list = File("src/main/assets/dictionaries/en_US.txt").bufferedReader().useLines { WordList.parse(it) }
        val classifier = classifier(list)
        classifier.classify(QwertyGeometry.path("the"), 3) // warm up
        val words = listOf("hello" to "helo", "world" to "world", "keyboard" to "keyboard", "thanks" to "thanks", "morning" to "morning")
        var slowest = 0L
        for ((expected, path) in words) {
            val start = System.nanoTime()
            val suggestions = classifier.classify(QwertyGeometry.path(path), 3)
            val millis = (System.nanoTime() - start) / 1_000_000
            slowest = maxOf(slowest, millis)
            assertTrue(expected in suggestions, "$expected not in $suggestions for '$path'")
        }
        println("glide: slowest classification $slowest ms over ${list.size} words")
        assertTrue(slowest < 100, "slowest classification took $slowest ms")
    }
}
