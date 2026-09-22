package net.matasar.keyboard.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        assertTrue(letters(Languages.russian).containsAll(listOf("ы", "э", "ъ")))
        // ъ sits where Ukrainian has ї, so the two Cyrillic boards are the same shape; the shift
        // row already made the board 12 units wide, so no key changes size for it.
        assertEquals(12, Languages.russian.rows[0].length)
        assertEquals(12f, Languages.russian.units)
        assertEquals("a", Languages.french.rows[0].first().toString())
        assertTrue(letters(Languages.spanish).contains("ñ"))
        assertTrue(letters(Languages.german).containsAll(listOf("ü", "ö", "ä")))
        assertTrue(letters(Languages.bulgarian).containsAll(listOf("я", "ъ", "ч", "ш", "щ", "ю")))
        assertEquals(12f, Languages.ukrainian.units)
        assertEquals(11f, Languages.german.units)
        assertEquals(11f, Languages.bulgarian.units)
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
        assertEquals("ѝ", accents(Languages.bulgarian, "и").first())
        assertEquals(listOf("і", "ї", "ѝ"), accents(Languages.russian, "и"))
    }

    @Test
    fun `the space bar names the language and the globe cycles enabled languages`() {
        val space = Languages.ukrainian.lettersLayer(true).rows[3].keys.first { it.action == KeyAction.Space }
        assertEquals("Українська", space.label)
        assertEquals("Български", Languages.bulgarian.lettersLayer(true).rows[3].keys.first { it.action == KeyAction.Space }.label)
        val enabled = setOf("en_US", "uk", "ru")
        assertEquals(Languages.ukrainian, Languages.next(Languages.english, enabled))
        assertEquals(Languages.english, Languages.next(Languages.russian, enabled))
        assertEquals(Languages.english, Languages.next(Languages.english, setOf("en_US")))
        assertEquals(Languages.english, Languages.next(Languages.french, setOf("en_US", "uk")))
    }

    @Test
    fun `every letter sits in an ANSI slot, so ctrl combos are positional`() {
        fun slot(language: Language, letter: String) =
            language.lettersLayer(false).rows.flatMap { it.keys }.first { it.label == letter }.slot
        assertEquals('q', slot(Languages.english, "q"))
        assertEquals('q', slot(Languages.ukrainian, "й"))
        assertEquals(']', slot(Languages.ukrainian, "ї"))
        assertEquals('c', slot(Languages.russian, "с"))
        assertEquals('a', slot(Languages.french, "q"))
        assertEquals('q', slot(Languages.bulgarian, "я"))
        assertEquals('w', slot(Languages.bulgarian, "в"))
        assertEquals('[', slot(Languages.bulgarian, "ч"))
        assertEquals(';', slot(Languages.bulgarian, "ш"))
        assertEquals('\'', slot(Languages.bulgarian, "щ"))
        assertEquals(',', slot(Languages.bulgarian, "ю"))
        for (language in Languages.all) {
            val letters = language.lettersLayer(false).rows.flatMap { it.keys }.filter { it.action is KeyAction.Letter }
            assertTrue(letters.all { it.slot != null }, "${language.tag} has a letter without a slot")
        }
    }

    @Test
    fun `the first-run default is english only regardless of system locales`() {
        val uk = java.util.Locale("uk", "UA"); val ru = java.util.Locale("ru", "RU"); val ja = java.util.Locale("ja", "JP")
        val bg = java.util.Locale("bg", "BG")
        assertEquals(setOf("en_US"), Languages.defaultEnabled(listOf(uk, ru)))
        assertEquals(setOf("en_US"), Languages.defaultEnabled(listOf(java.util.Locale("pt", "PT"))))
        assertEquals(setOf("en_US"), Languages.defaultEnabled(listOf(bg)))
        assertEquals(setOf("en_US"), Languages.defaultEnabled(listOf(ja)))
        assertEquals(setOf("en_US"), Languages.defaultEnabled(listOf(java.util.Locale("en", "GB"))))
        assertEquals(setOf("en_US"), Languages.defaultEnabled(emptyList()))
        assertEquals(setOf("en_US"), Languages.defaultEnabled())
    }

    @Test
    fun `a non-letter in a row is a plain text key`() {
        val apostrophe = Languages.french.lettersLayer(false).rows[2].keys.first { it.label == "'" }
        assertEquals(KeyAction.Text("'"), apostrophe.action)
    }

    @Test
    fun `every language has its own subtype id, and English keeps the one phones already enabled`() {
        assertEquals(Languages.all.size, Languages.all.map { it.subtypeId }.toSet().size)
        assertEquals(0x68630001, Languages.english.subtypeId)
        for (language in Languages.all) assertEquals(language, Languages.bySubtypeId(language.subtypeId))
        assertNull(Languages.bySubtypeId(0))
    }
}
