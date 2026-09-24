package net.matasar.keyboard.settings

import net.matasar.keyboard.layout.Languages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageGroupsTest {

    @Test
    fun `the enabled languages come first, in the globe's order`() {
        val (on, _) = languageGroups(setOf("hr", "en_US", "uk", "pt"))
        assertEquals(listOf("uk", "pt", "hr"), on.map { it.tag })
    }

    @Test
    fun `English is in neither group, being always on`() {
        val (on, more) = languageGroups(setOf("en_US"))
        assertEquals(emptyList(), on)
        assertTrue(Languages.english !in more)
    }

    @Test
    fun `the rest is sorted by English name, and every other language is in exactly one group`() {
        val (on, more) = languageGroups(setOf("en_US", "bg"))
        assertEquals(more.map { it.englishName }.sorted(), more.map { it.englishName })
        assertEquals("Croatian", more.first().englishName)
        assertTrue(on.none { it in more })
        assertEquals((Languages.all - Languages.english).toSet(), (on + more).toSet())
    }
}
