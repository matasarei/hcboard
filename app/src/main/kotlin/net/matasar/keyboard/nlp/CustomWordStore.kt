package net.matasar.keyboard.nlp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wordStore: DataStore<Preferences> by preferencesDataStore(name = "words")

/** The user's own words, apart from the settings so a bad value here never touches them. */
class CustomWordStore(private val context: Context) {

    val words: Flow<CustomWords> = context.wordStore.data.map { read(it) }

    /** Adds [word] to [language] at [frequency], or blocks it at [CustomWord.BLOCKED]; replaces what it was. */
    suspend fun set(language: String, word: String, frequency: Int) {
        val clean = CustomWord.normalize(word) ?: return
        context.wordStore.edit { p ->
            val current = read(p)
            val entries = current[language].orEmpty() + (clean to frequency.coerceIn(0, 255))
            p[WORDS_JSON] = CustomWordsJson.encode(current + (language to entries))
        }
    }

    suspend fun remove(language: String, word: String) {
        context.wordStore.edit { p ->
            val current = read(p)
            val entries = current[language].orEmpty() - word
            p[WORDS_JSON] = CustomWordsJson.encode(current + (language to entries))
        }
    }

    suspend fun replaceAll(words: CustomWords) {
        context.wordStore.edit { it[WORDS_JSON] = CustomWordsJson.encode(words) }
    }

    // Unreadable (a newer format, a damaged file): none, until the next change replaces it.
    private fun read(p: Preferences): CustomWords = p[WORDS_JSON]?.let { CustomWordsJson.decode(it) }.orEmpty()

    private companion object {
        val WORDS_JSON = stringPreferencesKey("words_json")
    }
}
