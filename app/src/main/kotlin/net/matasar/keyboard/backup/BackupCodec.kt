package net.matasar.keyboard.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.matasar.keyboard.macro.Block
import net.matasar.keyboard.macro.Macro
import net.matasar.keyboard.macro.openSecrets
import net.matasar.keyboard.macro.sealSecrets
import net.matasar.keyboard.nlp.CustomWords
import net.matasar.keyboard.nlp.CustomWordsJson
import net.matasar.keyboard.settings.Settings
import net.matasar.keyboard.settings.sanitized
import java.security.SecureRandom
import java.util.Base64

/** The export is refused: the macros hold secret texts and no passphrase was given. */
class PassphraseRequired : Exception("the backup holds secret texts and needs a passphrase")

/** The passphrase does not open the file's secrets; nothing was read from them. */
class WrongPassphrase : Exception("the passphrase does not open this backup")

/** Not a backup this version can read: another format, a newer version, or not JSON at all. */
class NotABackup(message: String, cause: Throwable? = null) : Exception(message, cause)

/** What a backup file restores, secrets opened and everything within the keyboard's limits. */
data class Restored(val settings: Settings, val words: CustomWords, val macros: List<Macro>)

/**
 * Turns settings, custom words and macros into a backup file and back. Pure: the file picker,
 * the stores and the passphrase dialog are the caller's. Secrets never leave here unsealed.
 */
object BackupCodec {

    /** A larger file is refused before it is read. */
    const val MAX_BYTES = 5 * 1024 * 1024

    private const val CHECK = "hcboard"

    /** A file asking for more rounds than this is refused rather than left to spin. */
    private const val MAX_ITERATIONS = 10_000_000

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        prettyPrint = true
    }

    /** Whether exporting [macros] needs a passphrase: they hold a secret text. */
    fun needsPassphrase(macros: List<Macro>): Boolean = macros.any { it.blocks.anySecretText() }

    /**
     * The backup file's text. Secret texts are sealed with a key made from [passphrase]; one this
     * phone could not open goes out empty, so no Keystore ciphertext ever reaches the file.
     * Throws [PassphraseRequired] when there are secrets and no passphrase.
     */
    fun encode(
        settings: Settings,
        words: CustomWords,
        macros: List<Macro>,
        passphrase: CharArray?,
        random: SecureRandom = SecureRandom(),
        iterations: Int = PassphraseSecretBox.ITERATIONS,
    ): String {
        val clean = macros.map { it.copy(blocks = it.blocks.withoutKeptSealed()) }
        var secrets: SecretsHeader? = null
        var stored = clean
        if (needsPassphrase(clean)) {
            if (passphrase == null) throw PassphraseRequired()
            val salt = ByteArray(PassphraseSecretBox.SALT_BYTES).also(random::nextBytes)
            val box = PassphraseSecretBox(passphrase, salt, iterations, random)
            stored = clean.map { it.sealSecrets(box) }
            secrets = SecretsHeader(PassphraseSecretBox.KDF, iterations, Base64.getEncoder().encodeToString(salt), box.seal(CHECK))
        }
        val file = BackupFile(settings = settings, words = CustomWordsJson.sanitized(words), macros = stored, secrets = secrets)
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /** The file in [text]; throws [NotABackup] for anything this version cannot read. */
    fun read(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw NotABackup("not a backup file", e)
        } catch (e: IllegalArgumentException) {
            throw NotABackup("not a backup file", e)
        }
        if (file.format != BackupFile.FORMAT) throw NotABackup("not a backup file")
        if (file.version > BackupFile.VERSION) throw NotABackup("made by a newer version")
        file.secrets?.let {
            if (it.kdf != PassphraseSecretBox.KDF || it.iterations !in 1..MAX_ITERATIONS) throw NotABackup("unknown secret protection")
        }
        return file
    }

    /** Whether opening [file] needs a passphrase. */
    fun needsPassphrase(file: BackupFile): Boolean = file.secrets != null

    /**
     * What [file] restores. Its secrets are opened with [passphrase]; [PassphraseRequired] when
     * one is needed and missing, [WrongPassphrase] when it does not open the check value. A file
     * with no secrets header is taken as it is: its secret texts, if hand-written, are plain.
     */
    fun open(file: BackupFile, passphrase: CharArray?): Restored {
        val header = file.secrets
        val macros = if (header == null) {
            file.macros
        } else {
            if (passphrase == null) throw PassphraseRequired()
            // A salt that is not Base64, or empty (PBKDF2 refuses one), is a damaged file.
            val box = try {
                PassphraseSecretBox(passphrase, Base64.getDecoder().decode(header.salt), header.iterations)
            } catch (e: IllegalArgumentException) {
                throw NotABackup("damaged secret protection", e)
            } catch (e: java.security.GeneralSecurityException) {
                throw NotABackup("damaged secret protection", e)
            }
            if (box.open(header.check) != CHECK) throw WrongPassphrase()
            file.macros.map { it.openSecrets(box) }.map { it.copy(blocks = it.blocks.withoutKeptSealed()) }
        }
        return Restored(file.settings.sanitized(), CustomWordsJson.sanitized(file.words), macros)
    }

    private fun List<Block>.anySecretText(): Boolean =
        any { (it is Block.TypeText && it.secret) || (it is Block.Repeat && it.blocks.anySecretText()) }

    /** An unopened secret loses its sealed text: it belongs to the Keystore or passphrase it came from. */
    private fun List<Block>.withoutKeptSealed(): List<Block> = map {
        when {
            it is Block.TypeText && it.keptSealed != null -> it.copy(keptSealed = null)
            it is Block.Repeat -> it.copy(blocks = it.blocks.withoutKeptSealed())
            else -> it
        }
    }
}
