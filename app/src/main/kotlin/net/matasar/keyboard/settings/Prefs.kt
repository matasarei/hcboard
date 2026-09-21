package net.matasar.keyboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import net.matasar.keyboard.layout.Languages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeChoice { SYSTEM, LIGHT, DARK, BLACK }

/** Whether the wide board splits into halves: never, where a hinge or a sideways phone asks for it, or always. */
enum class SplitMode { OFF, AUTO, ALWAYS }

/** Everything the settings screen edits and the keyboard reads. */
data class Settings(
    val heightScale: Float = 1f,
    val widthScale: Float = 1f,
    /** Whether the room under the keys follows what the window measures (the system's bar and buttons). */
    val bottomPaddingAuto: Boolean = true,
    /** The room under the keys, in dp, when [bottomPaddingAuto] is off. */
    val bottomPaddingDp: Int = 0,
    val haptics: Boolean = true,
    val keyBorders: Boolean = true,
    val previews: Boolean = true,
    /** The strip's mic, which hands dictation to a voice keyboard; off for those who never want it. */
    val voiceInput: Boolean = true,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val splitKeyboard: SplitMode = SplitMode.AUTO,
    val editingShortcuts: Boolean = true,
    val doubleTapLock: Boolean = true,
    /** Packages in which developer mode was left on. */
    val developerModePackages: Set<String> = emptySet(),
    val suggestions: Boolean = true,
    val autoCorrect: Boolean = true,
    val autoCapitalize: Boolean = true,
    val glide: Boolean = true,
    val glideTrail: Boolean = true,
    /** Tags of the enabled languages; never empty. */
    val enabledLanguages: Set<String> = setOf(DEFAULT_LANGUAGE),
    val currentLanguage: String = DEFAULT_LANGUAGE,
    /** Whether the Russian keyboard loads the combined RU+BG dictionary. */
    val ruBulgarianVocabulary: Boolean = false,
) {
    companion object {
        const val DEFAULT_LANGUAGE = "en_US"
        const val MAX_BOTTOM_PADDING_DP = 48
    }
}

/** The enabled set after switching [tag] on or off; the last language can never be switched off. */
fun Set<String>.withLanguage(tag: String, enabled: Boolean): Set<String> = when {
    enabled -> this + tag
    size <= 1 -> this
    else -> this - tag
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The one place preferences are read and written. */
class Prefs(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            heightScale = p[HEIGHT_SCALE] ?: 1f,
            widthScale = (p[WIDTH_SCALE] ?: 1f).coerceIn(0.7f, 1f),
            bottomPaddingAuto = p[BOTTOM_PADDING_AUTO] ?: true,
            bottomPaddingDp = (p[BOTTOM_PADDING_DP] ?: 0).coerceIn(0, Settings.MAX_BOTTOM_PADDING_DP),
            haptics = p[HAPTICS] ?: true,
            keyBorders = p[KEY_BORDERS] ?: true,
            previews = p[PREVIEWS] ?: true,
            voiceInput = p[VOICE_INPUT] ?: true,
            theme = p[THEME]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: ThemeChoice.SYSTEM,
            splitKeyboard = p[SPLIT_KEYBOARD]?.let { runCatching { SplitMode.valueOf(it) }.getOrNull() } ?: SplitMode.AUTO,
            editingShortcuts = p[EDITING_SHORTCUTS] ?: true,
            doubleTapLock = p[DOUBLE_TAP_LOCK] ?: true,
            developerModePackages = p[DEV_MODE_PACKAGES] ?: emptySet(),
            suggestions = p[SUGGESTIONS] ?: true,
            autoCorrect = p[AUTO_CORRECT] ?: true,
            autoCapitalize = p[AUTO_CAPITALIZE] ?: true,
            glide = p[GLIDE] ?: true,
            glideTrail = p[GLIDE_TRAIL] ?: true,
            enabledLanguages = p[ENABLED_LANGUAGES]?.takeIf { it.isNotEmpty() } ?: defaultEnabledLanguages(),
            currentLanguage = p[CURRENT_LANGUAGE] ?: Settings.DEFAULT_LANGUAGE,
            ruBulgarianVocabulary = p[RU_BULGARIAN_VOCABULARY] ?: false,
        )
    }

    suspend fun setHeightScale(value: Float) = context.dataStore.edit { it[HEIGHT_SCALE] = value }
    suspend fun setWidthScale(value: Float) = context.dataStore.edit { it[WIDTH_SCALE] = value.coerceIn(0.7f, 1f) }
    suspend fun setBottomPaddingAuto(value: Boolean) = context.dataStore.edit { it[BOTTOM_PADDING_AUTO] = value }
    suspend fun setBottomPaddingDp(value: Int) = context.dataStore.edit { it[BOTTOM_PADDING_DP] = value.coerceIn(0, Settings.MAX_BOTTOM_PADDING_DP) }
    suspend fun setHaptics(value: Boolean) = context.dataStore.edit { it[HAPTICS] = value }
    suspend fun setKeyBorders(value: Boolean) = context.dataStore.edit { it[KEY_BORDERS] = value }
    suspend fun setPreviews(value: Boolean) = context.dataStore.edit { it[PREVIEWS] = value }
    suspend fun setVoiceInput(value: Boolean) = context.dataStore.edit { it[VOICE_INPUT] = value }
    suspend fun setTheme(value: ThemeChoice) = context.dataStore.edit { it[THEME] = value.name }
    suspend fun setSplitKeyboard(value: SplitMode) = context.dataStore.edit { it[SPLIT_KEYBOARD] = value.name }
    suspend fun setEditingShortcuts(value: Boolean) = context.dataStore.edit { it[EDITING_SHORTCUTS] = value }
    suspend fun setDoubleTapLock(value: Boolean) = context.dataStore.edit { it[DOUBLE_TAP_LOCK] = value }
    suspend fun setSuggestions(value: Boolean) = context.dataStore.edit { it[SUGGESTIONS] = value }
    suspend fun setAutoCorrect(value: Boolean) = context.dataStore.edit { it[AUTO_CORRECT] = value }
    suspend fun setAutoCapitalize(value: Boolean) = context.dataStore.edit { it[AUTO_CAPITALIZE] = value }
    suspend fun setGlide(value: Boolean) = context.dataStore.edit { it[GLIDE] = value }
    suspend fun setGlideTrail(value: Boolean) = context.dataStore.edit { it[GLIDE_TRAIL] = value }
    suspend fun setCurrentLanguage(tag: String) = context.dataStore.edit { it[CURRENT_LANGUAGE] = tag }
    suspend fun setRuBulgarianVocabulary(value: Boolean) = context.dataStore.edit { it[RU_BULGARIAN_VOCABULARY] = value }

    suspend fun setLanguageEnabled(tag: String, enabled: Boolean) = context.dataStore.edit { p ->
        val current = p[ENABLED_LANGUAGES]?.takeIf { it.isNotEmpty() } ?: defaultEnabledLanguages()
        val next = current.withLanguage(tag, enabled)
        p[ENABLED_LANGUAGES] = next
        if ((p[CURRENT_LANGUAGE] ?: Settings.DEFAULT_LANGUAGE) !in next) p[CURRENT_LANGUAGE] = next.first()
    }

    suspend fun setDeveloperMode(packageName: String, on: Boolean) = context.dataStore.edit { p ->
        val current = p[DEV_MODE_PACKAGES] ?: emptySet()
        p[DEV_MODE_PACKAGES] = if (on) current + packageName else current - packageName
    }

    /**
     * Until the user touches the list: English plus the phone's own languages that the keyboard
     * ships. Not persisted, so it follows the phone's languages as they change; the first toggle
     * stores the set as it stands then, and the service copes when the current language drops out.
     */
    private fun defaultEnabledLanguages(): Set<String> {
        // The resource configuration carries per-app languages as well as the system's; the
        // process-wide default list does not.
        val locales = context.resources.configuration.locales
        return Languages.defaultEnabled((0 until locales.size()).map { locales.get(it) })
    }

    private companion object {
        val HEIGHT_SCALE = floatPreferencesKey("height_scale")
        val WIDTH_SCALE = floatPreferencesKey("width_scale")
        val BOTTOM_PADDING_AUTO = booleanPreferencesKey("bottom_padding_auto")
        val BOTTOM_PADDING_DP = intPreferencesKey("bottom_padding_dp")
        val HAPTICS = booleanPreferencesKey("haptics")
        val KEY_BORDERS = booleanPreferencesKey("key_borders")
        val PREVIEWS = booleanPreferencesKey("previews")
        val VOICE_INPUT = booleanPreferencesKey("voice_input")
        val THEME = stringPreferencesKey("theme")
        val SPLIT_KEYBOARD = stringPreferencesKey("split_keyboard")
        val EDITING_SHORTCUTS = booleanPreferencesKey("editing_shortcuts")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")
        val DEV_MODE_PACKAGES = stringSetPreferencesKey("developer_mode_packages")
        val SUGGESTIONS = booleanPreferencesKey("suggestions")
        val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        val GLIDE = booleanPreferencesKey("glide")
        val GLIDE_TRAIL = booleanPreferencesKey("glide_trail")
        val ENABLED_LANGUAGES = stringSetPreferencesKey("enabled_languages")
        val CURRENT_LANGUAGE = stringPreferencesKey("current_language")
        val RU_BULGARIAN_VOCABULARY = booleanPreferencesKey("ru_bulgarian_vocabulary")
    }
}
