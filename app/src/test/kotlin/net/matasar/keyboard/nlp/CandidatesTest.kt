package net.matasar.keyboard.nlp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CandidatesTest {

    private val list = WordList.of(
        "check" to 200, "checking" to 150, "checked" to 140, "chef" to 90, "chalk" to 50,
        "spell" to 100, "spelling" to 90, "spelled" to 120, "hello" to 200, "help" to 150,
        "the" to 255, "to" to 255, "rare" to 70, "rate" to 30,
    )
    private val candidates = Candidates(list)

    @Test
    fun `a misspelt word gets its most frequent one-edit neighbour as the correction`() {
        val result = assertNotNull(candidates.forWord("chek"))
        assertEquals("check", result.correction)
        assertEquals(listOf("chek", "check", "chef"), result.words)
    }

    @Test
    fun `a known word is completed and never corrected`() {
        val result = assertNotNull(candidates.forWord("spell"))
        assertNull(result.correction)
        assertEquals(listOf("spell", "spelled", "spelling"), result.words)
    }

    @Test
    fun `the typed case is applied to the candidates`() {
        assertEquals(listOf("Chek", "Check", "Chef"), candidates.forWord("Chek")!!.words)
        assertEquals("Check", candidates.forWord("Chek")!!.correction)
        assertEquals(listOf("CHEK", "CHECK", "CHEF"), candidates.forWord("CHEK")!!.words)
        assertEquals(listOf("Spell", "Spelled", "Spelling"), candidates.forWord("Spell")!!.words)
    }

    @Test
    fun `short words and rare neighbours are offered but never applied`() {
        assertNull(candidates.forWord("th")!!.correction)
        assertEquals(listOf("th", "the", "to"), candidates.forWord("th")!!.words)
        val rare = assertNotNull(candidates.forWord("rade"))
        assertNull(rare.correction)
        assertEquals(listOf("rade", "rare", "rate"), rare.words)
    }

    @Test
    fun `nothing for an empty word or a word with no neighbours and no completions`() {
        assertNull(candidates.forWord(""))
        assertNull(candidates.forWord("xyzzy"))
        assertNull(candidates.forWord("hello"))
    }

    @Test
    fun `at most three words, the typed one first, no duplicates`() {
        val result = assertNotNull(candidates.forWord("chec"))
        assertEquals(3, result.words.size)
        assertEquals("chec", result.words.first())
        assertEquals(result.words.size, result.words.toSet().size)
        assertEquals("check", result.correction)
    }

    @Test
    fun `transposed letters are one edit away`() {
        assertEquals("the", candidates.forWord("teh")!!.correction)
        assertEquals("hello", candidates.forWord("hlelo")!!.correction)
    }

    @Test
    fun `an е spelling of a ё word is corrected to the ё word whatever its length or frequency`() {
        val russian = Candidates(
            WordList.of("идёт" to 90, "идеи" to 140, "ещё" to 160, "еле" to 120, "её" to 170, "не" to 190, "счёт" to 60, "свет" to 130),
        )
        assertEquals("идёт", russian.forWord("идет")!!.correction)
        assertEquals(listOf("идет", "идёт", "идеи"), russian.forWord("идет")!!.words)
        assertEquals("ещё", russian.forWord("еще")!!.correction)
        assertEquals("её", russian.forWord("ее")!!.correction)
        assertEquals("счёт", russian.forWord("счет")!!.correction)
        assertEquals("Ещё", russian.forWord("Еще")!!.correction)
        assertNull(russian.forWord("идёт")?.correction)
    }

    @Test
    fun `the bundled russian list knows chat words and still corrects ordinary typos`() {
        val russian = Candidates(File("src/main/assets/dictionaries/ru.txt").bufferedReader().useLines { WordList.parse(it) })
        for (chat in listOf("окей", "ладно", "напиши", "щас", "лол", "мем", "баг", "логин")) {
            assertNull(russian.forWord(chat)?.correction, "'$chat' should be known, got ${russian.forWord(chat)?.correction}")
        }
        assertEquals("спасибо", russian.forWord("спасиьо")!!.correction)
        assertEquals("идёт", russian.forWord("идет")!!.correction)
    }

    @Test
    fun `the bundled english and ukrainian lists behave the same way`() {
        val english = Candidates(File("src/main/assets/dictionaries/en_US.txt").bufferedReader().useLines { WordList.parse(it) })
        assertEquals("check", english.forWord("chek")!!.correction)
        val spell = english.forWord("Spell")!!
        assertNull(spell.correction)
        assertTrue("Spelling" in spell.words, "${spell.words}")
        val ukrainian = Candidates(File("src/main/assets/dictionaries/uk.txt").bufferedReader().useLines { WordList.parse(it) })
        val words = ukrainian.forWord("прив")!!.words
        assertTrue("привіт" in words, "$words")
        for (modern in listOf("app", "apps", "dev", "diff", "wifi", "git")) {
            val cand = english.forWord(modern)
            assertNull(cand?.correction, "expected $modern to be known with no correction, got ${cand?.correction}")
        }
    }

    private val apostrophes = Candidates(
        WordList.of(
            "what" to 200, "what's" to 154, "whatever" to 120, "don't" to 185, "done" to 170, "I'm" to 116, "in" to 250,
            "its" to 150, "it's" to 162, "cant" to 20, "can't" to 160, "can" to 230,
            "розв'язок" to 70, "розвиток" to 150, "c'est" to 159, "hello" to 200, "I'll" to 164, "Berlin" to 140,
        ),
    )

    @Test
    fun `a word typed without its apostrophe is restored, first and applied`() {
        val whats = assertNotNull(apostrophes.forWord("whats"))
        assertEquals("what's", whats.correction)
        assertEquals("what's", whats.words[1])
        assertEquals("don't", apostrophes.forWord("dont")!!.correction)
    }

    @Test
    fun `a restoration passes the length and frequency gates a guess must pass`() {
        // Two letters, and a word stored at 70, under autocorrect's floor of 80.
        assertEquals("I'm", apostrophes.forWord("im")!!.correction)
        assertEquals("розв'язок", apostrophes.forWord("розвязок")!!.correction)
    }

    @Test
    fun `a lowercase word with its apostrophe typed gets the list's capital, as I'm`() {
        assertEquals("I'm", apostrophes.forWord("i'm")!!.correction)
        assertEquals("I’ll", apostrophes.forWord("i’ll")!!.correction) // the typed apostrophe is kept
        // Only a word with an apostrophe: a name typed in lowercase is not capitalised.
        assertNull(apostrophes.forWord("berlin")?.correction)
    }

    @Test
    fun `a real word is kept and its apostrophe twin offered next to it`() {
        val its = assertNotNull(apostrophes.forWord("its"))
        assertNull(its.correction)
        assertEquals(listOf("its", "it's"), its.words.take(2))
        val cant = assertNotNull(apostrophes.forWord("cant"))
        assertNull(cant.correction)
        assertEquals("can't", cant.words[1])
    }

    @Test
    fun `a word with its apostrophe typed is completed across it`() {
        assertEquals(listOf("розв'я", "розв'язок"), apostrophes.forWord("розв'я")!!.words)
        assertEquals("c'est", apostrophes.forWord("c'es")!!.words[1])
        assertEquals("don't", apostrophes.forWord("don'")!!.words[1])
    }

    @Test
    fun `the apostrophe the user typed is the one the strip gives back`() {
        assertEquals("what’s", apostrophes.forWord("what’")!!.words[1])
        assertEquals("розвʼязок", apostrophes.forWord("розвʼя")!!.words[1])
        assertEquals("What’s", apostrophes.forWord("What’")!!.words[1])
    }

    @Test
    fun `a word ending in an apostrophe is never corrected, as it may be a closing quote`() {
        // "hello'" is one edit from "hello", but its apostrophe may close a quote.
        assertNull(apostrophes.forWord("hello'")?.correction)
    }

    @Test
    fun `on the bundled lists, words with apostrophes are restored, offered and completed`() {
        fun bundled(tag: String) = Candidates(File("src/main/assets/dictionaries/$tag.txt").bufferedReader().useLines { WordList.parse(it) })
        val english = bundled("en_US")
        assertEquals("what's", english.forWord("whats")!!.correction)
        assertEquals("don't", english.forWord("dont")!!.correction)
        assertEquals("I'm", english.forWord("im")!!.correction)
        assertEquals("I'm", english.forWord("i'm")!!.correction)
        assertEquals("I'll", english.forWord("i'll")!!.correction)
        assertNull(english.forWord("its")!!.correction)
        assertEquals("it's", english.forWord("its")!!.words[1])
        val ukrainian = bundled("uk")
        assertEquals("розв'язок", ukrainian.forWord("розвязок")!!.correction)
        assertTrue("розв'язок" in ukrainian.forWord("розв'я")!!.words, "${ukrainian.forWord("розв'я")!!.words}")
        assertEquals("п'ять", ukrainian.forWord("пять")!!.correction)
        assertTrue("c'est" in bundled("fr").forWord("c'es")!!.words)
        assertTrue("dell'anno" in bundled("it").forWord("dell'an")!!.words)
    }
}
