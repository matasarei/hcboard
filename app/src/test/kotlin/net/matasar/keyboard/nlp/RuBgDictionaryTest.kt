package net.matasar.keyboard.nlp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuBgDictionaryTest {

    private val ruList by lazy {
        File("src/main/assets/dictionaries/ru.txt").bufferedReader().useLines { WordList.parse(it) }
    }

    private val ruBgList by lazy {
        File("src/main/assets/dictionaries/ru_bg.txt").bufferedReader().useLines { WordList.parse(it) }
    }

    private val candidates by lazy {
        Candidates(ruBgList)
    }

    @Test
    fun `all 80k russian words are present in ru_bg with identical frequencies`() {
        assertEquals(80_000, ruList.size)
        assertTrue(ruBgList.size >= 150_000, "ru_bg size: ${ruBgList.size}")
        for (word in ruList.words) {
            assertTrue(ruBgList.contains(word), "Missing Russian word: $word")
            assertEquals(ruList.frequency(word), ruBgList.frequency(word), "Frequency mismatch for Russian word: $word")
        }
    }

    @Test
    fun `bulgarian vocabulary is present in ru_bg`() {
        for (bgWord in listOf("съвет", "български", "здравей", "благодаря", "кметство", "стотинки")) {
            assertTrue(ruBgList.contains(bgWord), "Missing Bulgarian word: $bgWord")
        }
    }

    @Test
    fun `russian words dominate colliding bulgarian words in frequency`() {
        // Colliding Bulgarian words must have frequency < 80 and strictly less than Russian neighbors
        assertTrue(ruBgList.frequency("совет") > ruBgList.frequency("съвет"))
        assertEquals(75, ruBgList.frequency("съвет"))

        assertTrue(ruBgList.frequency("аппарат") > ruBgList.frequency("апарат"))
        assertEquals(75, ruBgList.frequency("апарат"))

        assertTrue(ruBgList.frequency("минуту") > ruBgList.frequency("минути"))
        assertEquals(75, ruBgList.frequency("минути"))

        assertTrue(ruBgList.frequency("был") > ruBgList.frequency("бял"))
        assertEquals(75, ruBgList.frequency("бял"))
    }

    @Test
    fun `intentionally typed russian and bulgarian words are never autocorrected`() {
        for (known in listOf("совет", "съвет", "минуту", "минути", "был", "бял", "български", "кметство", "здравей", "благодаря")) {
            val result = candidates.forWord(known)
            assertNull(result?.correction, "Word '$known' should never be autocorrected")
            if (result != null) {
                assertEquals(known, result.words.first(), "First candidate must be the typed word")
            }
        }
    }

    @Test
    fun `russian typos autocorrect to russian words and never to colliding bulgarian words`() {
        // савет is 1 edit from свет (RU, 138), совет (RU, 135), and съвет (BG, 75)
        val savet = assertNotNull(candidates.forWord("савет"))
        assertTrue(savet.correction in listOf("свет", "совет"), "Expected Russian correction, got ${savet.correction}")
        assertTrue(savet.correction != "съвет", "Must never autocorrect to Bulgarian съвет")

        // совеь is a typo specifically for совет
        val sovet = assertNotNull(candidates.forWord("совеь"))
        assertEquals("совет", sovet.correction)

        // минутк is 1 edit from минутку (RU, 155 from the everyday overlay), минут (RU, 139), минуты (RU, 124),
        // минуту (RU, 120), and минути (BG, 75)
        val minutk = assertNotNull(candidates.forWord("минутк"))
        assertEquals("минутку", minutk.correction)
        assertTrue(minutk.correction != "минути", "Must never autocorrect to Bulgarian минути")

        // быыл is 1 edit from был (RU, 187)
        val byyl = assertNotNull(candidates.forWord("быыл"))
        assertEquals("был", byyl.correction)
    }

    @Test
    fun `safe bulgarian words autocorrect without interference`() {
        // стотинки is a uniquely Bulgarian word scaled to f=83 (>= 80)
        val stotinkl = assertNotNull(candidates.forWord("стотинкл"))
        assertEquals("стотинки", stotinkl.correction)
    }
}
