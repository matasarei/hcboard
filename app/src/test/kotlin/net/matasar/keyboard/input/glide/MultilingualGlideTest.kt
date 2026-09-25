package net.matasar.keyboard.input.glide

import net.matasar.keyboard.layout.Language
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.PortugueseSpelling
import net.matasar.keyboard.nlp.Apostrophes
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

    /** The letter keys, as the keyboard hands them to glide: an apostrophe key is left out, so glide skips it. */
    val keys: List<GlideKey> = language.rows.flatMapIndexed { rowIndex, chars ->
        val leadingUnits = if (rowIndex == 2) (units - chars.length) / 2f else (units - chars.length) / 2f
        chars.mapIndexed { i, c ->
            val x = side + (leadingUnits + i) * (keyW + gap) + keyW / 2
            GlideKey(c, x, rowIndex * rowPitch + keyH / 2, keyW, keyH)
        }
    }.filter { it.char.isLetter() }

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

    private fun wordList(asset: String) = File("src/main/assets/dictionaries/$asset.txt").bufferedReader().useLines { WordList.parse(it) }

    /** Every list [language] can load: both Portuguese spellings, Russian with and without Bulgarian words. */
    private fun assets(language: Language): Set<String> =
        listOf(false, true).flatMap { ruBg -> PortugueseSpelling.entries.map { Languages.assetFor(language.tag, ruBg, it) } }.toSet()

    private fun classifier(language: Language): Pair<GlideClassifier, LanguageGeometry> {
        val list = wordList(Languages.assetFor(language.tag, false, PortugueseSpelling.PORTUGAL))
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

    /** ґ has no key and no decomposition: a path over г reaches it (FallbackKeyChars). */
    @Test
    fun `ukrainian glides ґрунт over the г key`() = assertGlides(Languages.ukrainian, "грунт", "ґрунт")

    @Test
    fun `ukrainian commits everyday words first thanks to the overlay`() {
        for (word in listOf("привіт", "дякую", "добре", "зараз", "сьогодні")) assertGlidesFirst(Languages.ukrainian, word)
        val list = File("src/main/assets/dictionaries/uk.txt").bufferedReader().useLines { WordList.parse(it) }
        for (word in listOf("окей", "напиши", "подзвони")) assertTrue(list.frequency(word) >= 180, "'$word' missing from uk.txt")
    }

    @Test
    fun `words with an apostrophe glide over their letters alone`() {
        assertGlides(Languages.ukrainian, "розвязок", "розв'язок")
        assertGlides(Languages.english, "dont", "don't")
        assertGlides(Languages.french, "cest", "c'est")
    }

    @Test
    fun `russian glides привет`() = assertGlides(Languages.russian, "привет", "привет")

    @Test
    fun `russian commits everyday words first thanks to the overlay`() {
        for (word in listOf("привет", "спасибо", "хорошо", "ладно", "окей")) assertGlidesFirst(Languages.russian, word)
    }

    @Test
    fun `french glides bonjour on azerty`() = assertGlides(Languages.french, "bonjour", "bonjour")

    @Test
    fun `spanish glides gracias`() = assertGlides(Languages.spanish, "gracias", "gracias")

    @Test
    fun `german glides danke`() = assertGlides(Languages.german, "danke", "danke")

    @Test
    fun `bulgarian glides здравей`() = assertGlides(Languages.bulgarian, "здравей", "здравей")

    @Test
    fun `combined ru_bg dictionary glides both russian and bulgarian words on russian keyboard`() {
        val list = File("src/main/assets/dictionaries/ru_bg.txt").bufferedReader().useLines { WordList.parse(it) }
        val geometry = LanguageGeometry(Languages.russian)
        val classifier = GlideClassifier(list).apply { setLayout(geometry.keys) }

        // Russian words glide cleanly on Russian layout
        for (word in listOf("привет", "хорошо", "совет")) {
            val suggestions = classifier.classify(geometry.path(word), 4)
            assertTrue(word in suggestions, "ru_bg: expected '$word' in $suggestions")
        }

        // Bulgarian words glide cleanly on Russian layout
        for (word in listOf("здравей", "благодаря", "български")) {
            val suggestions = classifier.classify(geometry.path(word), 4)
            assertTrue(word in suggestions, "ru_bg: expected '$word' in $suggestions")
        }
    }

    @Test
    fun `every language's list is typeable on its layer`() {
        for (language in Languages.all) for (asset in assets(language)) {
            val list = wordList(asset)
            val keys = LanguageGeometry(language).keys.associateBy { it.char }
            // An apostrophe needs no key: glide skips it, so розв'язок is glided as розвязок.
            val untypeable = list.words.take(5_000).filter { word -> word.any { c -> !Apostrophes.isApostrophe(c) && baseKeyChar(c, keys) == null } }
            assertTrue(untypeable.size < 50, "$asset: ${untypeable.size} of the top 5000 words need a missing key, e.g. ${untypeable.take(8)}")
        }
    }
}
