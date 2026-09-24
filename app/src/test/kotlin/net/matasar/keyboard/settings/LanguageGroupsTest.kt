package net.matasar.keyboard.settings

import net.matasar.keyboard.layout.Languages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageGroupsTest {

    @Test
    fun `English and the enabled languages come first, in the globe's order`() {
        val (on, _) = languageGroups(setOf("hr", "en_US", "uk", "pt"))
        assertEquals(listOf("en_US", "uk", "pt", "hr"), on.map { it.tag })
    }

    @Test
    fun `English is on even when the set leaves it out`() {
        assertEquals(listOf(Languages.english), languageGroups(emptySet()).first)
    }

    @Test
    fun `the rest is sorted by English name, and every language is in exactly one group`() {
        val (on, more) = languageGroups(setOf("en_US", "bg"))
        assertEquals(more.map { it.englishName }.sorted(), more.map { it.englishName })
        assertEquals("Croatian", more.first().englishName)
        assertTrue(on.none { it in more })
        assertEquals(Languages.all.toSet(), (on + more).toSet())
        assertEquals(Languages.all.size, on.size + more.size)
    }
}
