package net.matasar.keyboard.nlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomWordsTest {

    @Test
    fun `a word is trimmed and keeps its case`() {
        assertEquals("kubectl", CustomWord.normalize("  kubectl "))
        assertEquals("Straße", CustomWord.normalize("Straße"))
        assertEquals("їжак", CustomWord.normalize("їжак"))
        assertEquals("a".repeat(48), CustomWord.normalize("a".repeat(48)))
    }

    @Test
    fun `anything the lists could not hold is refused`() {
        for (bad in listOf("", "   ", "two words", "k8s", "don't", "e-mail", "a".repeat(49), "hi!")) {
            assertNull(CustomWord.normalize(bad), bad)
        }
    }

    @Test
    fun `words survive a round trip`() {
        val words: CustomWords = mapOf("en_US" to mapOf("kubectl" to 230, "ducking" to 0), "uk" to mapOf("вайбкодинг" to 230))
        assertEquals(words, CustomWordsJson.decode(CustomWordsJson.encode(words)))
    }

    @Test
    fun `stored entries are cleaned on the way in`() {
        val text = """{"version":1,"future":1,"languages":{"en_US":{"kubectl":999,"bad word":230,"low":-5},"de":{"1x":230}}}"""
        assertEquals(mapOf("en_US" to mapOf("kubectl" to 255, "low" to 0)), CustomWordsJson.decode(text))
    }

    @Test
    fun `unreadable text decodes to null`() {
        assertNull(CustomWordsJson.decode("not json"))
        assertNull(CustomWordsJson.decode("""{"languages":{"en_US":["kubectl"]}}"""))
        assertNull(CustomWordsJson.decode(""))
    }
}
