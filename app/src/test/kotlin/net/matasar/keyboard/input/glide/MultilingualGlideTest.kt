package net.matasar.keyboard.input.glide

import net.matasar.keyboard.layout.Language
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.nlp.WordList
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Key centres for any language's letters layer at a 412 dp phone width and density 1. */
class LanguageGeometry(language: Language) {
    private val units = language.units
    private val gap = 6f
    private val side = 4f
    private val keyW = (412f - 2 * side - (units - 1) * gap) / units
    private val keyH = 42f
    private val rowPitch = 54f

    val keys: List<GlideKey> = language.rows.flatMapIndexed { rowIndex, chars ->
        val leadingUnits = if (rowIndex == 2) (units - chars.length) / 2f else (units - chars.length) / 2f
        chars.mapIndexed { i, c ->
            val x = side + (leadingUnits + i) * (keyW + gap) + keyW / 2
            GlideKey(c, x, rowIndex * rowPitch + keyH / 2, keyW, keyH)
        }
    }

    fun path(letters: String, steps: Int = 8): List<GlidePoint> {
        val centres = letters.map { ch -> keys.first { it.char == ch }.let { GlidePoint(it.centerX, it.centerY) } }
        val points = mutableListOf(centres.first())
        for ((a, b) in centres.zipWithNext()) {
            for (s in 1..steps) {
                val t = s / steps.toFloat()
                points += GlidePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        return points
    }
}

class MultilingualGlideTest {

    private fun classifier(language: Language): Pair<GlideClassifier, LanguageGeometry> {
        val list = File("src/main/assets/dictionaries/${language.tag}.txt").bufferedReader().useLines { WordList.parse(it) }
        assertTrue(list.size > 30_000, "${language.tag}: only ${list.size} words")
        val geometry = LanguageGeometry(language)
        return GlideClassifier(list).apply { setLayout(geometry.keys) } to geometry
    }

    private fun assertGlides(language: Language, path: String, expected: String) {
        val (classifier, geometry) = classifier(language)
        val suggestions = classifier.classify(geometry.path(path), 4)
        assertTrue(expected in suggestions, "${language.tag}: '$expected' not in $suggestions for path '$path'")
    }

    /** Stricter than [assertGlides]: the word the user meant must be what gets committed. */
    private fun assertGlidesFirst(language: Language, word: String) {
        val (classifier, geometry) = classifier(language)
        val suggestions = classifier.classify(geometry.path(word), 4)
        assertEquals(word, suggestions.firstOrNull(), "${language.tag}: got $suggestions for '$word'")
    }

    @Test
    fun `ukrainian glides привіт`() = assertGlides(Languages.ukrainian, "привіт", "привіт")

    @Test
    fun `ukrainian commits everyday words first thanks to the overlay`() {
        for (word in listOf("привіт", "дякую", "добре", "зараз", "сьогодні")) assertGlidesFirst(Languages.ukrainian, word)
        val list = File("src/main/assets/dictionaries/uk.txt").bufferedReader().useLines { WordList.parse(it) }
        for (word in listOf("окей", "напиши", "подзвони")) assertTrue(list.frequency(word) >= 180, "'$word' missing from uk.txt")
    }

    @Test
    fun `russian glides привет`() = assertGlides(Languages.russian, "привет", "привет")

    @Test
    fun `french glides bonjour on azerty`() = assertGlides(Languages.french, "bonjour", "bonjour")

    @Test
    fun `spanish glides gracias`() = assertGlides(Languages.spanish, "gracias", "gracias")

    @Test
    fun `german glides danke`() = assertGlides(Languages.german, "danke", "danke")

    @Test
    fun `bulgarian glides здравей`() = assertGlides(Languages.bulgarian, "здравей", "здравей")

    @Test
    fun `every language's list is typeable on its layer`() {
        for (language in Languages.all) {
            val list = File("src/main/assets/dictionaries/${language.tag}.txt").bufferedReader().useLines { WordList.parse(it) }
            val keys = LanguageGeometry(language).keys.associateBy { it.char }
            val untypeable = list.words.take(5_000).filter { word -> word.any { c -> baseKeyChar(c, keys) == null } }
            assertTrue(untypeable.size < 50, "${language.tag}: ${untypeable.size} of the top 5000 words need a missing key, e.g. ${untypeable.take(8)}")
        }
    }
}
