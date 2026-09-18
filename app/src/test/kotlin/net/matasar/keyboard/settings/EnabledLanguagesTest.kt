package net.matasar.keyboard.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class EnabledLanguagesTest {

    @Test
    fun `languages can be added and removed but the last one stays`() {
        val one = setOf("en_US")
        assertEquals(setOf("en_US", "uk"), one.withLanguage("uk", true))
        assertEquals(setOf("en_US"), one.withLanguage("en_US", false))
        assertEquals(setOf("uk"), setOf("en_US", "uk").withLanguage("en_US", false))
    }

    @Test
    fun `defaults are english only`() {
        assertEquals(setOf("en_US"), Settings().enabledLanguages)
        assertEquals("en_US", Settings().currentLanguage)
        assertEquals(false, Settings().ruBulgarianVocabulary)
    }
}
