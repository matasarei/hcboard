package net.matasar.keyboard.settings

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.runBlocking
import net.matasar.keyboard.layout.PortugueseSpelling
import net.matasar.keyboard.nlp.CustomWord
import net.matasar.keyboard.nlp.CustomWordsJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Portuguese was `pt_BR` before it had two spellings: what was stored under that tag reaches `pt`. */
class PortugueseTagMigrationTest {

    private val enabled = stringSetPreferencesKey("enabled_languages")
    private val current = stringPreferencesKey("current_language")
    private val spelling = stringPreferencesKey("portuguese_spelling")

    @Test
    fun `someone who typed Brazilian Portuguese keeps it on, current, and Brazilian`() = runBlocking {
        val stored = preferencesOf(enabled to setOf("en_US", "pt_BR", "uk"), current to "pt_BR")
        assertTrue(RenamePortugueseTag.shouldMigrate(stored))
        val migrated = RenamePortugueseTag.migrate(stored)
        assertEquals(setOf("en_US", "pt", "uk"), migrated[enabled])
        assertEquals("pt", migrated[current])
        assertEquals(PortugueseSpelling.BRAZIL.name, migrated[spelling])
        assertFalse(RenamePortugueseTag.shouldMigrate(migrated))
    }

    @Test
    fun `a spelling already chosen is kept, and a file without the old tag is left alone`() = runBlocking {
        val chosen = RenamePortugueseTag.migrate(preferencesOf(enabled to setOf("pt_BR"), spelling to PortugueseSpelling.PORTUGAL.name))
        assertEquals(PortugueseSpelling.PORTUGAL.name, chosen[spelling])
        assertFalse(RenamePortugueseTag.shouldMigrate(preferencesOf(enabled to setOf("en_US", "pt"), current to "en_US")))
        assertFalse(RenamePortugueseTag.shouldMigrate(preferencesOf()))
    }

    @Test
    fun `a restored file with the old tag is Portuguese in Brazilian spelling, a new one as it says`() {
        val old = Settings(enabledLanguages = setOf("en_US", "pt_BR"), currentLanguage = "pt_BR").sanitized()
        assertEquals(setOf("en_US", "pt"), old.enabledLanguages)
        assertEquals("pt", old.currentLanguage)
        assertEquals(PortugueseSpelling.BRAZIL, old.portugueseSpelling)
        val new = Settings(enabledLanguages = setOf("en_US", "pt"), currentLanguage = "pt").sanitized()
        assertEquals(PortugueseSpelling.PORTUGAL, new.portugueseSpelling)
    }

    @Test
    fun `words under the old tag join Portuguese, a block winning and then the higher frequency`() {
        val words = CustomWordsJson.sanitized(
            mapOf(
                "pt_BR" to mapOf("zap" to 230, "vlw" to 200, "bué" to 230),
                "pt" to mapOf("vlw" to 230, "bué" to CustomWord.BLOCKED, "fixe" to 230),
                "en_US" to mapOf("kubectl" to 230),
            ),
        )
        assertEquals(
            mapOf(
                "pt" to mapOf("zap" to 230, "vlw" to 230, "bué" to CustomWord.BLOCKED, "fixe" to 230),
                "en_US" to mapOf("kubectl" to 230),
            ),
            words,
        )
        // The stored JSON reads the same way, so nothing typed under pt_BR is lost on the update.
        val stored = """{"version":1,"languages":{"pt_BR":{"zap":230}}}"""
        assertEquals(mapOf("pt" to mapOf("zap" to 230)), CustomWordsJson.decode(stored))
    }
}
