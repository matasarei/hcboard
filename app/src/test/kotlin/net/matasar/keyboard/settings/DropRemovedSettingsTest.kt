package net.matasar.keyboard.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DropRemovedSettingsTest {

    private val keyBorders = booleanPreferencesKey("key_borders")
    private val height = floatPreferencesKey("height_scale")

    @Test
    fun `a removed setting is dropped once and everything else is kept`() = runBlocking {
        val stored = preferencesOf(keyBorders to false, height to 0.9f)
        assertTrue(DropRemovedSettings.shouldMigrate(stored))
        val migrated = DropRemovedSettings.migrate(stored)
        assertEquals(preferencesOf(height to 0.9f), migrated)
        assertFalse(DropRemovedSettings.shouldMigrate(migrated))
    }

    @Test
    fun `a file without removed settings is left alone`() = runBlocking {
        assertFalse(DropRemovedSettings.shouldMigrate(preferencesOf(height to 0.9f)))
    }
}
