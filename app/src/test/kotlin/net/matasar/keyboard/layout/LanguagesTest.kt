package net.matasar.keyboard.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguagesTest {

    @Test
    fun `every row of every language adds up to the language's units, with and without the globe`() {
        for (language in Languages.all) {
            for (withGlobe in listOf(false, true)) {
                val layer = language.lettersLayer(withGlobe)
                for ((index, row) in layer.rows.withIndex()) {
                    assertEquals(language.units, row.totalUnits, "${language.tag} row $index globe=$withGlobe")
                }
                assertEquals(withGlobe, layer.rows[3].keys.any { it.action == KeyAction.SwitchLanguage })
            }
        }
    }

    @Test
    fun `the layouts carry the letters that make them what they are`() {
        fun letters(language: Language) = language.lettersLayer(false).rows.flatMap { it.keys }.map { it.label }.toSet()
        assertTrue(letters(Languages.ukrainian).containsAll(listOf("ї", "є", "і")))
        assertTrue(letters(Languages.russian).containsAll(listOf("ы", "э")))
        assertEquals("a", Languages.french.rows[0].first().toString())
        assertTrue(letters(Languages.spanish).contains("ñ"))
        assertTrue(letters(Languages.german).containsAll(listOf("ü", "ö", "ä")))
        assertEquals(12f, Languages.ukrainian.units)
        assertEquals(11f, Languages.german.units)
        assertEquals(10f, Languages.french.units)
    }

    @Test
    fun `long-press accents are where the plan puts them`() {
        fun accents(language: Language, letter: String) =
            language.lettersLayer(false).rows.flatMap { it.keys }.first { it.label == letter }.longPress
        assertEquals("ґ", accents(Languages.ukrainian, "г").first())
        assertEquals("ё", accents(Languages.russian, "е").first())
        assertEquals("é", accents(Languages.french, "e").first())
        assertEquals("ß", accents(Languages.german, "s").first())
        assertEquals("ç", accents(Languages.portuguese, "c").first())
    }

    @Test
    fun `the space bar names the language and the globe cycles enabled languages`() {
        val space = Languages.ukrainian.lettersLayer(true).rows[3].keys.first { it.action == KeyAction.Space }
        assertEquals("Українська", space.label)
        val enabled = setOf("en_US", "uk", "ru")
        assertEquals(Languages.ukrainian, Languages.next(Languages.english, enabled))
        assertEquals(Languages.english, Languages.next(Languages.russian, enabled))
        assertEquals(Languages.english, Languages.next(Languages.english, setOf("en_US")))
        assertEquals(Languages.english, Languages.next(Languages.french, setOf("en_US", "uk")))
    }

    @Test
    fun `a non-letter in a row is a plain text key`() {
        val apostrophe = Languages.french.lettersLayer(false).rows[2].keys.first { it.label == "'" }
        assertEquals(KeyAction.Text("'"), apostrophe.action)
    }
}
