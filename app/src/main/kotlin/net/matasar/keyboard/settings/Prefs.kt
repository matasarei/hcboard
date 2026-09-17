package net.matasar.keyboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Everything the settings screen edits and the keyboard reads. */
data class Settings(
    val heightScale: Float = 1f,
    val haptics: Boolean = true,
    val keyBorders: Boolean = true,
    val previews: Boolean = true,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val editingShortcuts: Boolean = true,
    val doubleTapLock: Boolean = true,
    /** Packages in which developer mode was left on. */
    val developerModePackages: Set<String> = emptySet(),
    val glide: Boolean = true,
    val glideTrail: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The one place preferences are read and written. */
class Prefs(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            heightScale = p[HEIGHT_SCALE] ?: 1f,
            haptics = p[HAPTICS] ?: true,
            keyBorders = p[KEY_BORDERS] ?: true,
            previews = p[PREVIEWS] ?: true,
            theme = p[THEME]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: ThemeChoice.SYSTEM,
            editingShortcuts = p[EDITING_SHORTCUTS] ?: true,
            doubleTapLock = p[DOUBLE_TAP_LOCK] ?: true,
            developerModePackages = p[DEV_MODE_PACKAGES] ?: emptySet(),
            glide = p[GLIDE] ?: true,
            glideTrail = p[GLIDE_TRAIL] ?: true,
        )
    }

    suspend fun setHeightScale(value: Float) = context.dataStore.edit { it[HEIGHT_SCALE] = value }
    suspend fun setHaptics(value: Boolean) = context.dataStore.edit { it[HAPTICS] = value }
    suspend fun setKeyBorders(value: Boolean) = context.dataStore.edit { it[KEY_BORDERS] = value }
    suspend fun setPreviews(value: Boolean) = context.dataStore.edit { it[PREVIEWS] = value }
    suspend fun setTheme(value: ThemeChoice) = context.dataStore.edit { it[THEME] = value.name }
    suspend fun setEditingShortcuts(value: Boolean) = context.dataStore.edit { it[EDITING_SHORTCUTS] = value }
    suspend fun setDoubleTapLock(value: Boolean) = context.dataStore.edit { it[DOUBLE_TAP_LOCK] = value }
    suspend fun setGlide(value: Boolean) = context.dataStore.edit { it[GLIDE] = value }
    suspend fun setGlideTrail(value: Boolean) = context.dataStore.edit { it[GLIDE_TRAIL] = value }

    suspend fun setDeveloperMode(packageName: String, on: Boolean) = context.dataStore.edit { p ->
        val current = p[DEV_MODE_PACKAGES] ?: emptySet()
        p[DEV_MODE_PACKAGES] = if (on) current + packageName else current - packageName
    }

    private companion object {
        val HEIGHT_SCALE = floatPreferencesKey("height_scale")
        val HAPTICS = booleanPreferencesKey("haptics")
        val KEY_BORDERS = booleanPreferencesKey("key_borders")
        val PREVIEWS = booleanPreferencesKey("previews")
        val THEME = stringPreferencesKey("theme")
        val EDITING_SHORTCUTS = booleanPreferencesKey("editing_shortcuts")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")
        val DEV_MODE_PACKAGES = stringSetPreferencesKey("developer_mode_packages")
        val GLIDE = booleanPreferencesKey("glide")
        val GLIDE_TRAIL = booleanPreferencesKey("glide_trail")
    }
}
