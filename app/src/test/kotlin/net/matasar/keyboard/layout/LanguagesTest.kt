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
    fun `shift and backspace take a key and a half at most, and a short row opens a gap beside them`() {
        fun shiftRow(language: Language) = language.lettersLayer(false).rows[2]
        assertEquals(1.5f, shiftRow(Languages.english).keys.first().width)
        assertEquals(0f, shiftRow(Languages.english).innerGapUnits)
        // German's seven letters on an eleven-unit board: not two-unit edge keys, but a gap.
        assertEquals(1.5f, shiftRow(Languages.german).keys.first().width)
        assertEquals(1.5f, shiftRow(Languages.german).keys.last().width)
        assertEquals(0.5f, shiftRow(Languages.german).innerGapUnits)
    }

    @Test
    fun `the layouts carry the letters that make them what they are`() {
        fun letters(language: Language) = language.lettersLayer(false).rows.flatMap { it.keys }.map { it.label }.toSet()
        assertTrue(letters(Languages.ukrainian).containsAll(listOf("ї", "є", "і")))
        assertTrue(letters(Languages.russian).containsAll(listOf("ы", "э")))
        // The phone follows the iPhone: 11/11/9 with ъ on a long press, while the 60% board's
        // rows keep ъ on `]`.
        assertEquals(listOf(11, 11, 9), Languages.russian.phoneRows.map { it.length })
        assertTrue("ъ" !in letters(Languages.russian))
        assertEquals(12, Languages.russian.rows[0].length)
        assertEquals(11f, Languages.russian.units)
        assertEquals(1f, Languages.russian.lettersLayer(false).rows[2].keys.first().width)
        // Ukrainian: twelve keys on every row, the apostrophe and ґ included.
        assertEquals(listOf(12, 12, 10), Languages.ukrainian.phoneRows.map { it.length })
        assertEquals("'", Languages.ukrainian.lettersLayer(false).rows[1].keys.last().label)
        assertEquals("ґ", Languages.ukrainian.lettersLayer(false).rows[2].keys.dropLast(1).last().label)
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
        assertEquals("ъ", accents(Languages.russian, "ь").first())
        assertEquals(listOf("ʼ", "’"), accents(Languages.ukrainian, "'"))
        assertEquals(emptyList(), accents(Languages.ukrainian, "ь"))
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
    fun `bulgarian resolves to the standard board only when asked, and stays the same language`() {
        val standard = Languages.resolve(Languages.bulgarian, BulgarianLayout.STANDARD)
        assertEquals(listOf("уеишщксдзцб", "ьяаожгтнвмч", "юйъэфхпрл"), standard.phoneRows)
        assertEquals(standard.phoneRows, standard.rows)
        assertEquals(Languages.bulgarian.tag, standard.tag)
        assertEquals(Languages.bulgarian.subtypeId, standard.subtypeId)
        assertEquals(Languages.bulgarian.nativeName, standard.nativeName)
        assertEquals(Languages.bulgarian, Languages.resolve(Languages.bulgarian, BulgarianLayout.PHONETIC))
        assertEquals(Languages.russian, Languages.resolve(Languages.russian, BulgarianLayout.STANDARD))
        assertTrue(standard !in Languages.all)
        val layer = standard.lettersLayer(false)
        assertEquals(11f, layer.units)
        assertEquals("shift ю й ъ э ф х п р л backspace", layer.rows[2].keys.joinToString(" ") { it.label })
        assertEquals(listOf(1f, 1f), listOf(layer.rows[2].keys.first().width, layer.rows[2].keys.last().width))
    }

    @Test
    fun `the EU languages carry their letters, and the phone and the 60% board both fit them`() {
        assertEquals(
            listOf("nl", "sv", "da", "fi", "cs", "ro", "el", "hr", "sl", "lt", "lv"),
            Languages.all.dropWhile { it != Languages.dutch }.map { it.tag },
        )
        fun keys(language: Language) = language.lettersLayer(false).rows.flatMap { it.keys }
        assertTrue(keys(Languages.danish).map { it.label }.containsAll(listOf("å", "æ", "ø")))
        assertTrue(keys(Languages.czech).map { it.label }.containsAll(listOf("ú", "ů", "z", "y")))
        // Greek: ς is a letter, ; is typed as it is, and tonos is a long press.
        val greek = keys(Languages.greek)
        assertEquals(KeyAction.Text(";"), greek.first { it.label == ";" }.action)
        assertTrue(greek.first { it.label == "ς" }.action is KeyAction.Letter)
        assertEquals("ά", greek.first { it.label == "α" }.longPress.first())
        // Croatian: ž ends the phone's home row (on the \ slot, as on a Croatian PC) and is a long press on z for the 60% board.
        val croatian = keys(Languages.croatian)
        assertEquals("ž", Languages.croatian.lettersLayer(false).rows[1].keys.last().label)
        assertEquals('\\', croatian.first { it.label == "ž" }.slot)
        assertEquals(listOf("ž"), croatian.first { it.label == "z" }.longPress)
        assertEquals("ț", keys(Languages.romanian).first { it.label == "t" }.longPress.first())
        for (language in Languages.all.dropWhile { it != Languages.dutch }) sixtyPercentLayer(language, withGlobe = true)
    }

    @Test
    fun `each language loads its own word list, Russian and Portuguese as their settings say`() {
        for (spelling in PortugueseSpelling.entries) for (ruBg in listOf(false, true)) {
            assertEquals(if (ruBg) "ru_bg" else "ru", Languages.assetFor("ru", ruBg, spelling))
            assertEquals(if (spelling == PortugueseSpelling.BRAZIL) "pt_BR" else "pt_PT", Languages.assetFor("pt", ruBg, spelling))
            for (language in Languages.all - Languages.russian - Languages.portuguese) {
                assertEquals(language.tag, Languages.assetFor(language.tag, ruBg, spelling))
            }
        }
        assertEquals("pt", Languages.portuguese.tag)
        assertEquals("Portuguese", Languages.portuguese.englishName)
        assertEquals(0x68630009, Languages.portuguese.subtypeId)
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
