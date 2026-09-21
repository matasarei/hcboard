package net.matasar.keyboard.macro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MacroSecretsTest {

    private val box = FakeSecretBox()

    private val macro = Macro(
        id = "m",
        name = "Login",
        blocks = listOf(
            Block.TypeText("user"),
            Block.PressKey("Tab"),
            Block.Repeat(2, listOf(Block.TypeText("p@ss", secret = true))),
            Block.TypeText("hunter2", secret = true),
        ),
    )

    @Test
    fun `only secret texts are sealed, inside repeats too`() {
        val sealed = macro.sealSecrets(box).blocks
        assertSame(macro.blocks[0], sealed[0])
        assertSame(macro.blocks[1], sealed[1])
        assertEquals(Block.Repeat(2, listOf(Block.TypeText("sealed:ss@p", secret = true))), sealed[2])
        assertEquals(Block.TypeText("sealed:2retnuh", secret = true), sealed[3])
    }

    @Test
    fun `a sealed macro opens to what it was`() {
        assertEquals(macro, macro.sealSecrets(box).openSecrets(box))
    }

    @Test
    fun `the stored text never holds a secret's plain text`() {
        val stored = MacroJson.encode(listOf(macro.sealSecrets(box)))
        assertFalse("hunter2" in stored, stored)
        assertFalse("p@ss" in stored, stored)
        assertTrue("\"secret\":true" in stored, stored)
        assertTrue("\"text\":\"user\"" in stored, stored)
        assertEquals(listOf(macro), MacroJson.decode(stored)?.map { it.openSecrets(box) })
    }

    @Test
    fun `a secret this phone cannot open is empty and keeps its sealed text`() {
        val stored = listOf<Block>(Block.TypeText("from-another-phone", secret = true))
        val opened = stored.openSecrets(box)
        assertEquals(listOf<Block>(Block.TypeText("", secret = true, keptSealed = "from-another-phone")), opened)
        // Saved again untouched, the sealed text goes back as it was.
        assertEquals(stored, opened.sealSecrets(box))
    }

    @Test
    fun `a new secret typed over an unopened one replaces it`() {
        val opened = Block.TypeText("", secret = true, keptSealed = "from-another-phone")
        assertEquals(listOf<Block>(Block.TypeText("sealed:wen", secret = true)), listOf(opened.copy(text = "new")).sealSecrets(box))
    }

    @Test
    fun `random keys and secret texts are secrets, plain text is not`() {
        assertTrue(macro.typesSecrets())
        assertTrue(MacroStore.passwordGenerator.typesSecrets())
        assertTrue(Macro("a", "A", listOf(Block.Repeat(2, listOf(Block.RandomKeys())))).typesSecrets())
        assertFalse(Macro("a", "A", listOf(Block.TypeText("hi"), Block.PasteClipboard)).typesSecrets())
    }

    @Test
    fun `a box that fails refuses the save instead of storing the plain text`() {
        val broken = object : SecretBox {
            override fun seal(plain: String): String = throw java.security.ProviderException("keystore unavailable")
            override fun open(sealed: String): String? = null
        }
        assertFailsWith<SecretNotSaved> { macro.sealSecrets(broken) }
        // Nothing to seal, nothing to refuse.
        val plain = Macro("p", "Plain", listOf(Block.TypeText("hi")))
        assertEquals(plain, plain.sealSecrets(broken))
    }
}
