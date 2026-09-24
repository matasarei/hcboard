package net.matasar.keyboard.settings

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import net.matasar.keyboard.layout.BulgarianLayout
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.PortugueseSpelling
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeChoice { SYSTEM, LIGHT, DARK, BLACK }

/** Whether the wide board splits into halves: never, where a hinge or a sideways phone asks for it, or always. */
@Serializable
enum class SplitMode { OFF, AUTO, ALWAYS }

/**
 * Everything the settings screen edits and the keyboard reads. Serializable for the backup file;
 * every field has a default, so a file from an older or newer version still reads.
 */
@Serializable
data class Settings(
    val heightScale: Float = 1f,
    val widthScale: Float = 1f,
    /** Whether the room under the keys follows what the window measures (the system's bar and buttons). */
    val bottomPaddingAuto: Boolean = true,
    /** The room under the keys, in dp, when [bottomPaddingAuto] is off. */
    val bottomPaddingDp: Int = 0,
    val haptics: Boolean = true,
    /** The digits across the top of the phone letters page; password fields show them regardless. */
    val numberRow: Boolean = false,
    val previews: Boolean = true,
    /** On a phone, the strip's settings, passwords and macros fold behind a chevron until tapped. */
    val foldToolbar: Boolean = true,
    /** The strip's mic, which hands dictation to a voice keyboard; off for those who never want it. */
    val voiceInput: Boolean = true,
    /** The keyboard the mic hands off to, by input method id; null picks one automatically. */
    val voiceKeyboard: String? = null,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** The highlight colour: the phone's palette, or one of the swatches. */
    val accent: AccentColour = AccentColour.SYSTEM,
    val splitKeyboard: SplitMode = SplitMode.AUTO,
    val editingShortcuts: Boolean = true,
    val doubleTapLock: Boolean = true,
    /** Packages in which developer mode was left on. */
    val developerModePackages: Set<String> = emptySet(),
    /** Packages allowed to suggest though their fields ask for no suggestions. */
    val suggestInPackages: Set<String> = emptySet(),
    val suggestions: Boolean = true,
    val autoCorrect: Boolean = true,
    val autoCapitalize: Boolean = true,
    /** A quick second Space after a word types ". ", as on the iPhone. */
    val doubleSpacePeriod: Boolean = true,
    val glide: Boolean = true,
    val glideTrail: Boolean = true,
    /** Tags of the enabled languages; never empty. */
    val enabledLanguages: Set<String> = setOf(DEFAULT_LANGUAGE),
    val currentLanguage: String = DEFAULT_LANGUAGE,
    /** Whether the Russian keyboard loads the combined RU+BG dictionary. */
    val ruBulgarianVocabulary: Boolean = false,
    /** Which board Bulgarian is typed on: phonetic, or the standard one (БДС) the iPhone ships. */
    val bulgarianLayout: BulgarianLayout = BulgarianLayout.PHONETIC,
    /** Which Portuguese the word list spells: Portugal's, or Brazil's. */
    val portugueseSpelling: PortugueseSpelling = PortugueseSpelling.PORTUGAL,
    /** What a globe tap does with three or more languages on. */
    val globeTap: GlobeTap = GlobeTap.LAST_USED,
    /** The language typed in before the current one, where [GlobeTap.LAST_USED] goes back to; null before any switch. */
    val previousLanguage: String? = null,
) {
    companion object {
        const val DEFAULT_LANGUAGE = "en_US"
        const val MAX_BOTTOM_PADDING_DP = 48
        const val MAX_SUGGEST_IN_PACKAGES = 200
        const val MIN_HEIGHT_SCALE = 0.8f
        const val MAX_HEIGHT_SCALE = 1.2f
        const val MIN_WIDTH_SCALE = 0.7f
    }
}

/**
 * These settings within what the screen can set: scales and padding clamped, only languages the
 * keyboard has, English always among them, and the current one enabled. Applied to a restored file.
 */
fun Settings.sanitized(): Settings {
    // A file from before Portuguese had two spellings names it pt_BR, and meant Brazil's.
    val legacyPortuguese = Languages.LEGACY_PORTUGUESE_TAG in enabledLanguages || currentLanguage == Languages.LEGACY_PORTUGUESE_TAG
    // English is always on: the screen has no switch for it, so a file without it would strand it off.
    val languages = setOf(Settings.DEFAULT_LANGUAGE) + enabledLanguages.map(Languages::migrateTag).filter { Languages.byTag(it) != null }
    val current = Languages.migrateTag(currentLanguage)
    return copy(
        // A restored file is the one place this set arrives from outside, and the keyboard reads
        // it on every field: a list of that size is a mistake or a hostile file, not a choice.
        suggestInPackages = suggestInPackages.take(Settings.MAX_SUGGEST_IN_PACKAGES).toSet(),
        heightScale = heightScale.coerceIn(Settings.MIN_HEIGHT_SCALE, Settings.MAX_HEIGHT_SCALE),
        widthScale = widthScale.coerceIn(Settings.MIN_WIDTH_SCALE, 1f),
        bottomPaddingDp = bottomPaddingDp.coerceIn(0, Settings.MAX_BOTTOM_PADDING_DP),
        enabledLanguages = languages,
        currentLanguage = current.takeIf { it in languages } ?: languages.first(),
        portugueseSpelling = if (legacyPortuguese) PortugueseSpelling.BRAZIL else portugueseSpelling,
        previousLanguage = previousLanguage?.let(Languages::migrateTag)?.takeIf { it in languages && it != current },
    )
}

/** The enabled set after switching [tag] on or off; the last language can never be switched off. */
fun Set<String>.withLanguage(tag: String, enabled: Boolean): Set<String> = when {
    enabled -> this + tag
    size <= 1 -> this
    else -> this - tag
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(DropRemovedSettings, RenamePortugueseTag) },
)

/**
 * Removes stored values for settings that no longer exist, once, on the first read after an
 * update: nothing reads or writes them any more, so they would otherwise stay in the file for
 * good. "key_borders" went with the Key borders switch.
 */
internal object DropRemovedSettings : DataMigration<Preferences> {
    private val removed = listOf(booleanPreferencesKey("key_borders"))

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = removed.any { it in currentData }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply { removed.forEach { remove(it) } }.toPreferences()

    override suspend fun cleanUp() = Unit
}

/**
 * Portuguese was `pt_BR` until it had two spellings; it is `pt` now. Someone who had it on keeps
 * it on, current if it was, and keeps the Brazilian word list they were typing with. Runs once, on
 * the first read after the update.
 */
internal object RenamePortugueseTag : DataMigration<Preferences> {
    private val enabled = stringSetPreferencesKey("enabled_languages")
    private val current = stringPreferencesKey("current_language")
    private val spelling = stringPreferencesKey("portuguese_spelling")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[enabled].orEmpty().contains(Languages.LEGACY_PORTUGUESE_TAG) || currentData[current] == Languages.LEGACY_PORTUGUESE_TAG

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        this[enabled]?.let { this[enabled] = it.map(Languages::migrateTag).toSet() }
        this[current]?.let { this[current] = Languages.migrateTag(it) }
        if (spelling !in this) this[spelling] = PortugueseSpelling.BRAZIL.name
    }.toPreferences()

    override suspend fun cleanUp() = Unit
}

/** The one place preferences are read and written. */
class Prefs(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            heightScale = p[HEIGHT_SCALE] ?: 1f,
            widthScale = (p[WIDTH_SCALE] ?: 1f).coerceIn(0.7f, 1f),
            bottomPaddingAuto = p[BOTTOM_PADDING_AUTO] ?: true,
            bottomPaddingDp = (p[BOTTOM_PADDING_DP] ?: 0).coerceIn(0, Settings.MAX_BOTTOM_PADDING_DP),
            haptics = p[HAPTICS] ?: true,
            numberRow = p[NUMBER_ROW] ?: false,
            previews = p[PREVIEWS] ?: true,
            foldToolbar = p[FOLD_TOOLBAR] ?: true,
            voiceInput = p[VOICE_INPUT] ?: true,
            voiceKeyboard = p[VOICE_KEYBOARD],
            theme = p[THEME]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: ThemeChoice.SYSTEM,
            accent = p[ACCENT]?.let { runCatching { AccentColour.valueOf(it) }.getOrNull() } ?: AccentColour.SYSTEM,
            splitKeyboard = p[SPLIT_KEYBOARD]?.let { runCatching { SplitMode.valueOf(it) }.getOrNull() } ?: SplitMode.AUTO,
            editingShortcuts = p[EDITING_SHORTCUTS] ?: true,
            doubleTapLock = p[DOUBLE_TAP_LOCK] ?: true,
            developerModePackages = p[DEV_MODE_PACKAGES] ?: emptySet(),
            suggestInPackages = p[SUGGEST_IN_PACKAGES] ?: emptySet(),
            suggestions = p[SUGGESTIONS] ?: true,
            autoCorrect = p[AUTO_CORRECT] ?: true,
            autoCapitalize = p[AUTO_CAPITALIZE] ?: true,
            doubleSpacePeriod = p[DOUBLE_SPACE_PERIOD] ?: true,
            glide = p[GLIDE] ?: true,
            glideTrail = p[GLIDE_TRAIL] ?: true,
            enabledLanguages = p[ENABLED_LANGUAGES]?.takeIf { it.isNotEmpty() } ?: defaultEnabledLanguages(),
            currentLanguage = p[CURRENT_LANGUAGE] ?: Settings.DEFAULT_LANGUAGE,
            ruBulgarianVocabulary = p[RU_BULGARIAN_VOCABULARY] ?: false,
            bulgarianLayout = p[BULGARIAN_LAYOUT]?.let { runCatching { BulgarianLayout.valueOf(it) }.getOrNull() } ?: BulgarianLayout.PHONETIC,
            portugueseSpelling = p[PORTUGUESE_SPELLING]?.let { runCatching { PortugueseSpelling.valueOf(it) }.getOrNull() } ?: PortugueseSpelling.PORTUGAL,
            globeTap = p[GLOBE_TAP]?.let { runCatching { GlobeTap.valueOf(it) }.getOrNull() } ?: GlobeTap.LAST_USED,
            previousLanguage = p[PREVIOUS_LANGUAGE],
        )
    }

    suspend fun setHeightScale(value: Float) = context.dataStore.edit { it[HEIGHT_SCALE] = value }
    suspend fun setWidthScale(value: Float) = context.dataStore.edit { it[WIDTH_SCALE] = value.coerceIn(0.7f, 1f) }
    suspend fun setBottomPaddingAuto(value: Boolean) = context.dataStore.edit { it[BOTTOM_PADDING_AUTO] = value }
    suspend fun setBottomPaddingDp(value: Int) = context.dataStore.edit { it[BOTTOM_PADDING_DP] = value.coerceIn(0, Settings.MAX_BOTTOM_PADDING_DP) }
    suspend fun setHaptics(value: Boolean) = context.dataStore.edit { it[HAPTICS] = value }
    suspend fun setNumberRow(value: Boolean) = context.dataStore.edit { it[NUMBER_ROW] = value }
    suspend fun setPreviews(value: Boolean) = context.dataStore.edit { it[PREVIEWS] = value }
    suspend fun setFoldToolbar(value: Boolean) = context.dataStore.edit { it[FOLD_TOOLBAR] = value }
    suspend fun setVoiceInput(value: Boolean) = context.dataStore.edit { it[VOICE_INPUT] = value }
    suspend fun setVoiceKeyboard(imeId: String?) = context.dataStore.edit { if (imeId == null) it.remove(VOICE_KEYBOARD) else it[VOICE_KEYBOARD] = imeId }
    suspend fun setTheme(value: ThemeChoice) = context.dataStore.edit { it[THEME] = value.name }
    suspend fun setAccent(value: AccentColour) = context.dataStore.edit { it[ACCENT] = value.name }
    suspend fun setSplitKeyboard(value: SplitMode) = context.dataStore.edit { it[SPLIT_KEYBOARD] = value.name }
    suspend fun setEditingShortcuts(value: Boolean) = context.dataStore.edit { it[EDITING_SHORTCUTS] = value }
    suspend fun setDoubleTapLock(value: Boolean) = context.dataStore.edit { it[DOUBLE_TAP_LOCK] = value }
    suspend fun setSuggestions(value: Boolean) = context.dataStore.edit { it[SUGGESTIONS] = value }
    suspend fun setAutoCorrect(value: Boolean) = context.dataStore.edit { it[AUTO_CORRECT] = value }
    suspend fun setAutoCapitalize(value: Boolean) = context.dataStore.edit { it[AUTO_CAPITALIZE] = value }
    suspend fun setDoubleSpacePeriod(value: Boolean) = context.dataStore.edit { it[DOUBLE_SPACE_PERIOD] = value }
    suspend fun setGlide(value: Boolean) = context.dataStore.edit { it[GLIDE] = value }
    suspend fun setGlideTrail(value: Boolean) = context.dataStore.edit { it[GLIDE_TRAIL] = value }
    /** Makes [tag] the current language; the one it replaces becomes the previous one, for the globe to go back to. */
    suspend fun setCurrentLanguage(tag: String) = context.dataStore.edit { p ->
        val was = p[CURRENT_LANGUAGE] ?: Settings.DEFAULT_LANGUAGE
        if (was != tag) p[PREVIOUS_LANGUAGE] = was
        p[CURRENT_LANGUAGE] = tag
    }
    suspend fun setGlobeTap(value: GlobeTap) = context.dataStore.edit { it[GLOBE_TAP] = value.name }
    suspend fun setRuBulgarianVocabulary(value: Boolean) = context.dataStore.edit { it[RU_BULGARIAN_VOCABULARY] = value }
    suspend fun setBulgarianLayout(value: BulgarianLayout) = context.dataStore.edit { it[BULGARIAN_LAYOUT] = value.name }
    suspend fun setPortugueseSpelling(value: PortugueseSpelling) = context.dataStore.edit { it[PORTUGUESE_SPELLING] = value.name }

    /** Writes every setting at once, from a restored backup, within [sanitized]'s limits. */
    suspend fun replaceAll(settings: Settings) {
        val s = settings.sanitized()
        context.dataStore.edit { p ->
            p[HEIGHT_SCALE] = s.heightScale
            p[WIDTH_SCALE] = s.widthScale
            p[BOTTOM_PADDING_AUTO] = s.bottomPaddingAuto
            p[BOTTOM_PADDING_DP] = s.bottomPaddingDp
            p[HAPTICS] = s.haptics
            p[NUMBER_ROW] = s.numberRow
            p[PREVIEWS] = s.previews
            p[FOLD_TOOLBAR] = s.foldToolbar
            p[VOICE_INPUT] = s.voiceInput
            if (s.voiceKeyboard == null) p.remove(VOICE_KEYBOARD) else p[VOICE_KEYBOARD] = s.voiceKeyboard
            p[THEME] = s.theme.name
            p[ACCENT] = s.accent.name
            p[SPLIT_KEYBOARD] = s.splitKeyboard.name
            p[EDITING_SHORTCUTS] = s.editingShortcuts
            p[DOUBLE_TAP_LOCK] = s.doubleTapLock
            p[DEV_MODE_PACKAGES] = s.developerModePackages
            p[SUGGEST_IN_PACKAGES] = s.suggestInPackages
            p[SUGGESTIONS] = s.suggestions
            p[AUTO_CORRECT] = s.autoCorrect
            p[AUTO_CAPITALIZE] = s.autoCapitalize
            p[DOUBLE_SPACE_PERIOD] = s.doubleSpacePeriod
            p[GLIDE] = s.glide
            p[GLIDE_TRAIL] = s.glideTrail
            p[ENABLED_LANGUAGES] = s.enabledLanguages
            p[CURRENT_LANGUAGE] = s.currentLanguage
            p[RU_BULGARIAN_VOCABULARY] = s.ruBulgarianVocabulary
            p[BULGARIAN_LAYOUT] = s.bulgarianLayout.name
            p[PORTUGUESE_SPELLING] = s.portugueseSpelling.name
            p[GLOBE_TAP] = s.globeTap.name
            if (s.previousLanguage == null) p.remove(PREVIOUS_LANGUAGE) else p[PREVIOUS_LANGUAGE] = s.previousLanguage
        }
    }

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

    /** The gear sheet's row: this app may suggest though its fields ask for no suggestions. */
    suspend fun setSuggestInApp(packageName: String, on: Boolean) = context.dataStore.edit { p ->
        val current = p[SUGGEST_IN_PACKAGES] ?: emptySet()
        p[SUGGEST_IN_PACKAGES] = if (on) current + packageName else current - packageName
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
        val NUMBER_ROW = booleanPreferencesKey("number_row")
        val PREVIEWS = booleanPreferencesKey("previews")
        val FOLD_TOOLBAR = booleanPreferencesKey("fold_toolbar")
        val VOICE_INPUT = booleanPreferencesKey("voice_input")
        val VOICE_KEYBOARD = stringPreferencesKey("voice_keyboard")
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
        val SPLIT_KEYBOARD = stringPreferencesKey("split_keyboard")
        val EDITING_SHORTCUTS = booleanPreferencesKey("editing_shortcuts")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")
        val DEV_MODE_PACKAGES = stringSetPreferencesKey("developer_mode_packages")
        val SUGGEST_IN_PACKAGES = stringSetPreferencesKey("suggest_in_packages")
        val SUGGESTIONS = booleanPreferencesKey("suggestions")
        val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        val GLIDE = booleanPreferencesKey("glide")
        val GLIDE_TRAIL = booleanPreferencesKey("glide_trail")
        val ENABLED_LANGUAGES = stringSetPreferencesKey("enabled_languages")
        val CURRENT_LANGUAGE = stringPreferencesKey("current_language")
        val RU_BULGARIAN_VOCABULARY = booleanPreferencesKey("ru_bulgarian_vocabulary")
        val BULGARIAN_LAYOUT = stringPreferencesKey("bulgarian_layout")
        val PORTUGUESE_SPELLING = stringPreferencesKey("portuguese_spelling")
        val GLOBE_TAP = stringPreferencesKey("globe_tap")
        val PREVIOUS_LANGUAGE = stringPreferencesKey("previous_language")
    }
}
