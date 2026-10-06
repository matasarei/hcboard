package net.matasar.keyboard.nlp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The IT and chat words people type, which the corpora lacked and earlier overlays kept losing
 * (one form of a word listed, every other form corrected back to it). Each probe word in
 * `chat-probe/<language>.txt` must be known and left as typed; each typo in
 * `chat-probe/<language>-guard.tsv` must still correct to its common word, so a new word never
 * takes a typo that used to be fixed. A missing word goes into the probe first, then into the
 * overlay. The probes are test resources, so editing one reruns this test.
 */
class ChatVocabularyTest {

    private fun candidates(asset: String): Candidates =
        Candidates(File("src/main/assets/dictionaries/$asset").bufferedReader().useLines { WordList.parse(it) })

    private fun resource(name: String): List<String> =
        assertNotNullResource(name).bufferedReader().readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }

    private fun assertNotNullResource(name: String) =
        checkNotNull(javaClass.getResourceAsStream("/chat-probe/$name")) { "no test resource chat-probe/$name" }

    private fun assertKnown(asset: String, probe: String) {
        val candidates = candidates(asset)
        val words = resource(probe).flatMap { it.split(' ') }.filter { it.isNotEmpty() }
        assertTrue(words.size >= 150, "only ${words.size} probe words in $probe")
        val corrected = words.mapNotNull { word -> candidates.forWord(word)?.correction?.let { "$word -> $it" } }
        assertTrue(corrected.isEmpty(), "corrected in $asset: $corrected")
    }

    private fun assertGuarded(asset: String, guard: String) {
        val candidates = candidates(asset)
        for (line in resource(guard)) {
            val (typo, word) = line.split('\t')
            assertEquals(word, candidates.forWord(typo)?.correction, "the typo '$typo' in $asset")
        }
    }

    @Test
    fun `russian IT and chat words are left as typed`() = assertKnown("ru.txt", "ru.txt")

    @Test
    fun `ukrainian IT and chat words are left as typed`() = assertKnown("uk.txt", "uk.txt")

    @Test
    fun `english dev and chat words are left as typed`() = assertKnown("en_US.txt", "en.txt")

    @Test
    fun `russian typos of common words still correct to them`() = assertGuarded("ru.txt", "ru-guard.tsv")

    @Test
    fun `ukrainian typos of common words still correct to them`() = assertGuarded("uk.txt", "uk-guard.tsv")

    @Test
    fun `english typos of common words still correct to them`() = assertGuarded("en_US.txt", "en-guard.tsv")
}
