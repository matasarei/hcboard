package net.matasar.keyboard.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PassphraseSecretBoxTest {

    private val salt = ByteArray(16) { it.toByte() }

    private fun box(passphrase: String) = PassphraseSecretBox(passphrase.toCharArray(), salt, iterations = 1_000)

    @Test
    fun `a sealed secret opens with the same passphrase, in another box too`() {
        val sealed = box("correct horse").seal("p@ss wörd ✓")
        assertFalse("p@ss" in sealed)
        assertEquals("p@ss wörd ✓", box("correct horse").open(sealed))
        assertEquals("", box("correct horse").open(box("correct horse").seal("")))
    }

    @Test
    fun `the same secret seals differently each time`() {
        val box = box("correct horse")
        assertNotEquals(box.seal("same"), box.seal("same"))
    }

    @Test
    fun `a wrong passphrase, another salt or a damaged value does not open`() {
        val sealed = box("correct horse").seal("secret")
        assertNull(box("wrong horse").open(sealed))
        assertNull(PassphraseSecretBox("correct horse".toCharArray(), ByteArray(16), iterations = 1_000).open(sealed))
        assertNull(box("correct horse").open(sealed.dropLast(4) + if (sealed.endsWith("AAAA")) "BBBB" else "AAAA"))
        assertNull(box("correct horse").open("not base64 !"))
        assertNull(box("correct horse").open(""))
    }

    @Test
    fun `the caller's passphrase array is left for the caller to wipe`() {
        val passphrase = "correct horse".toCharArray()
        PassphraseSecretBox(passphrase, salt, iterations = 1_000)
        assertEquals("correct horse", String(passphrase))
    }
}
