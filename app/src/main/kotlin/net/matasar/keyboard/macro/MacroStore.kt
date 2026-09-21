package net.matasar.keyboard.macro

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.macroStore: DataStore<Preferences> by preferencesDataStore(name = "macros")

/**
 * The saved macros, apart from the settings so a bad value here never touches them. Until the
 * user saves anything the list is [defaults], not written, the way the language list starts.
 * Secret texts are sealed by [box] on every write and opened on every read, so they are never
 * plain on disk or in a backup.
 */
class MacroStore(private val context: Context, private val box: SecretBox = KeystoreSecretBox()) {

    val macros: Flow<List<Macro>> = context.macroStore.data.map { p -> read(p) }

    suspend fun save(macros: List<Macro>) {
        context.macroStore.edit { it[MACROS_JSON] = encode(macros) }
    }

    /** Replaces the macro with [macro]'s id, or adds it at the end. */
    suspend fun upsert(macro: Macro) {
        context.macroStore.edit { p ->
            val current = read(p)
            val next = if (current.any { it.id == macro.id }) current.map { if (it.id == macro.id) macro else it } else current + macro
            p[MACROS_JSON] = encode(next)
        }
    }

    /** Puts a deleted [macro] back at [index], unless a macro with its id is there already. */
    suspend fun restore(macro: Macro, index: Int) {
        context.macroStore.edit { p ->
            val current = read(p)
            if (current.none { it.id == macro.id }) {
                p[MACROS_JSON] = encode(current.toMutableList().apply { add(index.coerceIn(0, size), macro) })
            }
        }
    }

    suspend fun delete(id: String) {
        context.macroStore.edit { p -> p[MACROS_JSON] = encode(read(p).filterNot { it.id == id }) }
    }

    private fun read(p: Preferences): List<Macro> {
        val text = p[MACROS_JSON] ?: return defaults
        // Unreadable (a newer format, a damaged file): the defaults, until the next save replaces it.
        return MacroJson.decode(text)?.map { it.openSecrets(box) } ?: defaults
    }

    /** The only way macros are written: every secret sealed first. */
    private fun encode(macros: List<Macro>): String = MacroJson.encode(macros.map { it.sealSecrets(box) })

    companion object {
        private val MACROS_JSON = stringPreferencesKey("macros_json")

        /** The one built-in macro: sixteen random letters, digits and symbols, typed as keys. */
        val passwordGenerator = Macro(
            id = "password-generator",
            name = "Password generator",
            blocks = listOf(Block.RandomKeys(length = Block.RandomKeys.DEFAULT_LENGTH, letters = true, digits = true, symbols = true)),
        )

        val defaults: List<Macro> = listOf(passwordGenerator)
    }
}
