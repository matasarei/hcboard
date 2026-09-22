package net.matasar.keyboard.backup

import net.matasar.keyboard.macro.Block
import net.matasar.keyboard.macro.Macro
import net.matasar.keyboard.macro.MacroStore
import net.matasar.keyboard.settings.Settings
import net.matasar.keyboard.settings.ThemeChoice
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupCodecTest {

    private val settings = Settings(theme = ThemeChoice.DARK, enabledLanguages = setOf("en_US", "uk"), currentLanguage = "uk")
    private val words = mapOf("en_US" to mapOf("kubectl" to 230, "tube" to 0))
    private val login = Macro(
        "login", "Login",
        listOf(Block.TypeText("me"), Block.PressKey("Tab"), Block.Repeat(1, listOf(Block.TypeText("hunter2", secret = true))), Block.PressKey("Enter")),
    )
    private val passphrase = "correct horse".toCharArray()

    private fun encode(macros: List<Macro>, passphrase: CharArray? = this.passphrase) =
        BackupCodec.encode(settings, words, macros, passphrase, SecureRandom(), iterations = 1_000)

    @Test
    fun `everything comes back, secrets opened with the passphrase`() {
        val text = encode(listOf(login, MacroStore.passwordGenerator))
        val restored = BackupCodec.open(BackupCodec.read(text), passphrase)
        assertEquals(settings, restored.settings)
        assertEquals(words, restored.words)
        assertEquals(listOf(login, MacroStore.passwordGenerator), restored.macros)
    }

    @Test
    fun `the file is readable but never holds a secret's plain text`() {
        val text = encode(listOf(login))
        assertTrue("\"format\": \"hcboard-backup\"" in text, text)
        assertTrue("kubectl" in text && "\"text\": \"me\"" in text, text)
        assertFalse("hunter2" in text, text)
        assertNotNull(BackupCodec.read(text).secrets)
    }

    @Test
    fun `secrets need a passphrase to export, random keys do not`() {
        assertFailsWith<PassphraseRequired> { encode(listOf(login), passphrase = null) }
        val text = encode(listOf(MacroStore.passwordGenerator), passphrase = null)
        val file = BackupCodec.read(text)
        assertNull(file.secrets)
        assertFalse(BackupCodec.needsPassphrase(file))
        assertEquals(listOf(MacroStore.passwordGenerator), BackupCodec.open(file, null).macros)
    }

    @Test
    fun `a wrong or missing passphrase opens nothing`() {
        val file = BackupCodec.read(encode(listOf(login)))
        assertTrue(BackupCodec.needsPassphrase(file))
        assertFailsWith<WrongPassphrase> { BackupCodec.open(file, "wrong horse".toCharArray()) }
        assertFailsWith<PassphraseRequired> { BackupCodec.open(file, null) }
    }

    @Test
    fun `a secret this phone could not open goes out empty, with no Keystore ciphertext`() {
        val unopened = Macro("u", "U", listOf(Block.TypeText("", secret = true, keptSealed = "KEYSTORE-CIPHERTEXT")))
        val text = encode(listOf(unopened))
        assertFalse("KEYSTORE-CIPHERTEXT" in text, text)
        assertEquals(listOf<Block>(Block.TypeText("", secret = true)), BackupCodec.open(BackupCodec.read(text), passphrase).macros.single().blocks)
    }

    @Test
    fun `a tampered secret comes back empty, never as ciphertext to keep`() {
        val text = encode(listOf(login))
        val sealed = Regex("\"text\": \"([^\"]{20,})\"").find(text)!!.groupValues[1]
        val restored = BackupCodec.open(BackupCodec.read(text.replace(sealed, "AAAA$sealed")), passphrase)
        val secret = (restored.macros.single().blocks[2] as Block.Repeat).blocks.single() as Block.TypeText
        assertEquals(Block.TypeText("", secret = true), secret)
    }

    @Test
    fun `what this version cannot read is refused`() {
        assertFailsWith<NotABackup> { BackupCodec.read("not json") }
        assertFailsWith<NotABackup> { BackupCodec.read("""{"format":"something-else","version":1}""") }
        assertFailsWith<NotABackup> { BackupCodec.read("""{"format":"hcboard-backup","version":2}""") }
        assertFailsWith<NotABackup> {
            BackupCodec.read("""{"format":"hcboard-backup","version":1,"secrets":{"kdf":"PBKDF2WithHmacSHA256","iterations":2000000000,"salt":"AA==","check":"x"}}""")
        }
    }

    @Test
    fun `unknown keys are ignored and what is out of range is brought within it`() {
        val text = """{"format":"hcboard-backup","version":1,"future":true,
            "settings":{"heightScale":9,"enabledLanguages":["klingon"],"currentLanguage":"klingon"},
            "words":{"en_US":{"kubectl":999,"two words":230}},
            "macros":[{"id":"a","name":"A","blocks":[{"type":"copy"}]}]}"""
        val restored = BackupCodec.open(BackupCodec.read(text), null)
        assertEquals(1.2f, restored.settings.heightScale)
        assertEquals(setOf("en_US"), restored.settings.enabledLanguages)
        assertEquals(mapOf("en_US" to mapOf("kubectl" to 255)), restored.words)
        assertEquals(listOf(Macro("a", "A", listOf(Block.CopyField))), restored.macros)
    }

    @Test
    fun `a damaged salt is refused as not a backup, not thrown as a crash`() {
        val file = BackupCodec.read(encode(listOf(login)))
        for (salt in listOf("", "!!")) {
            val damaged = file.copy(secrets = file.secrets!!.copy(salt = salt))
            assertFailsWith<NotABackup> { BackupCodec.open(damaged, passphrase) }
        }
    }
}
