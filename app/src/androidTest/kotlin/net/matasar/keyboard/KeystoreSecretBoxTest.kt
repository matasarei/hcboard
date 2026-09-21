package net.matasar.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.matasar.keyboard.macro.KeystoreSecretBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The Keystore is only on a device, so the real box is checked here; the logic around it on the JVM. */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretBoxTest {

    private val box = KeystoreSecretBox()

    @Test
    fun aSealedSecretOpensToItself() {
        val secret = "p@ss wörd ✓"
        val sealed = box.seal(secret)
        assertFalse(secret in sealed)
        assertEquals(secret, box.open(sealed))
        assertEquals("", box.open(box.seal("")))
    }

    @Test
    fun theSameSecretSealsDifferentlyEachTime() {
        assertNotEquals(box.seal("same"), box.seal("same"))
    }

    @Test
    fun whatWasNotSealedHereDoesNotOpen() {
        val sealed = box.seal("secret")
        val damaged = sealed.dropLast(4) + if (sealed.endsWith("AAAA")) "BBBB" else "AAAA"
        assertNull(box.open(damaged))
        assertNull(box.open("not base64 !"))
        assertNull(box.open(""))
    }
}
