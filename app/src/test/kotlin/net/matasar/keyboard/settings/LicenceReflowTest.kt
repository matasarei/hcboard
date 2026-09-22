package net.matasar.keyboard.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LicenceReflowTest {

    private val long = "x".repeat(60)

    @Test
    fun `a hard-wrapped paragraph becomes one line`() {
        assertEquals("$long and the rest of the sentence.", reflow("$long\n  and the rest of the sentence.\n"))
    }

    @Test
    fun `short lines, blank lines, bullets and clauses keep their breaks`() {
        assertEquals("hcboard\nCopyright 2026", reflow("hcboard\nCopyright 2026"))
        assertEquals("$long\n\nNext paragraph.", reflow("$long\n\nNext paragraph."))
        assertEquals("$long\n- A bullet", reflow("$long\n- A bullet"))
        assertEquals("$long\n2. Grant of Copyright License.", reflow("$long\n   2. Grant of Copyright License."))
        assertEquals("$long\n(a) You must give", reflow("$long\n      (a) You must give"))
    }

    @Test
    fun `empty text stays empty`() {
        assertEquals("", reflow(""))
    }

    @Test
    fun `the real NOTICE keeps each attribution as one paragraph`() {
        val notice = File("../NOTICE").takeIf { it.exists() } ?: File("NOTICE")
        val lines = reflow(notice.readText()).lines()
        val glide = lines.single { it.startsWith("- The glide-typing classifier") }
        assertTrue(glide.endsWith("Modifications are marked in the file header."), glide)
        assertTrue(lines.any { it.startsWith("- The Ukrainian word list") && it.contains("CC BY 4.0") })
        assertEquals("hcboard", lines.first())
    }

    @Test
    fun `the real LICENSE keeps each clause whole, and its title lines apart`() {
        val licence = File("../LICENSE").takeIf { it.exists() } ?: File("LICENSE")
        val lines = reflow(licence.readText()).lines()
        assertTrue("(a) You must give any other recipients of the Work or Derivative Works a copy of this License; and" in lines, lines.take(40).joinToString("\n"))
        assertTrue("(b) You must cause any modified files to carry prominent notices stating that You changed the files; and" in lines)
        assertEquals(listOf("Apache License", "Version 2.0, January 2004"), lines.filter { it.isNotEmpty() }.take(2))
    }
}
