package net.matasar.keyboard.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsSanitizeTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `settings the screen could set pass unchanged`() {
        val settings = Settings(heightScale = 0.9f, enabledLanguages = setOf("en_US", "uk"), currentLanguage = "uk")
        assertEquals(settings, settings.sanitized())
    }

    @Test
    fun `scales and padding are clamped to what the screen allows`() {
        val s = Settings(heightScale = 3f, widthScale = 0.1f, bottomPaddingDp = 500).sanitized()
        assertEquals(1.2f, s.heightScale)
        assertEquals(0.7f, s.widthScale)
        assertEquals(48, s.bottomPaddingDp)
        assertEquals(0.8f, Settings(heightScale = 0f).sanitized().heightScale)
    }

    @Test
    fun `unknown languages are dropped, never all of them, and the current one is enabled`() {
        assertEquals(setOf("uk"), Settings(enabledLanguages = setOf("uk", "klingon"), currentLanguage = "uk").sanitized().enabledLanguages)
        val none = Settings(enabledLanguages = setOf("klingon"), currentLanguage = "klingon").sanitized()
        assertEquals(setOf("en_US"), none.enabledLanguages)
        assertEquals("en_US", none.currentLanguage)
        assertEquals("de", Settings(enabledLanguages = setOf("de", "fr"), currentLanguage = "uk").sanitized().currentLanguage)
    }

    @Test
    fun `settings survive a round trip, and a file missing fields or with new ones still reads`() {
        val settings = Settings(
            theme = ThemeChoice.BLACK, splitKeyboard = SplitMode.ALWAYS, voiceKeyboard = "com.example/.Voice",
            developerModePackages = setOf("com.termux"), glide = false, enabledLanguages = setOf("en_US", "bg"),
        )
        assertEquals(settings, json.decodeFromString(Settings.serializer(), json.encodeToString(Settings.serializer(), settings)))
        assertEquals(Settings(haptics = false), json.decodeFromString(Settings.serializer(), """{"haptics":false,"fromTheFuture":1}"""))
    }
}
