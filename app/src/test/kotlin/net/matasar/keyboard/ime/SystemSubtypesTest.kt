package net.matasar.keyboard.ime

import net.matasar.keyboard.layout.Languages
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SystemSubtypesTest {

    @Test
    fun `the enabled languages go to Android in globe order`() {
        val ids = subtypeIdsFor(setOf("ru", "en_US", "uk"))
        assertContentEquals(intArrayOf(Languages.english.subtypeId, Languages.ukrainian.subtypeId, Languages.russian.subtypeId), ids)
    }

    @Test
    fun `english is always among them, even from a set that lost it`() {
        assertContentEquals(intArrayOf(Languages.english.subtypeId, Languages.german.subtypeId), subtypeIdsFor(setOf("de")))
        assertContentEquals(intArrayOf(Languages.english.subtypeId), subtypeIdsFor(emptySet()))
    }

    @Test
    fun `an unknown tag is not pushed`() {
        assertContentEquals(intArrayOf(Languages.english.subtypeId), subtypeIdsFor(setOf("en_US", "xx")))
    }

    @Test
    fun `a pick of an enabled language is followed`() {
        assertEquals(Languages.ukrainian, languageForPick(Languages.ukrainian.subtypeId, setOf("en_US", "uk")))
    }

    @Test
    fun `a pick of a language not enabled here, or of no language at all, keeps ours`() {
        assertNull(languageForPick(Languages.polish.subtypeId, setOf("en_US", "uk")))
        assertNull(languageForPick(0x12345678, setOf("en_US", "uk")))
    }
}
