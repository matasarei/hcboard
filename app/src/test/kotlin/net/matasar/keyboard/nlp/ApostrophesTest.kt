package net.matasar.keyboard.nlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApostrophesTest {

    @Test
    fun `the three apostrophes are one to the lists`() {
        assertEquals("don't", Apostrophes.normalize("don’t"))
        assertEquals("розв'язок", Apostrophes.normalize("розвʼязок"))
        assertEquals("plain", Apostrophes.normalize("plain"))
    }

    @Test
    fun `a word is letters, with apostrophes only between them`() {
        for (word in listOf("don't", "розв'язок", "c'est", "dell'anno", "o'clock", "rock'n'roll", "hello")) assertTrue(Apostrophes.isWord(word), word)
        for (word in listOf("", "'tis", "dogs'", "a''b", "'", "k8s", "e-mail")) assertFalse(Apostrophes.isWord(word), word)
    }

    @Test
    fun `a list word is shown with the apostrophe the user typed`() {
        assertEquals("what’s", Apostrophes.restyle("what's", "what’"))
        assertEquals("розвʼязок", Apostrophes.restyle("розв'язок", "розвʼя"))
        assertEquals("what's", Apostrophes.restyle("what's", "whats"))
        assertEquals("what's", Apostrophes.restyle("what's", "what'"))
    }
}
