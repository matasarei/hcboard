package net.matasar.keyboard.backup

import net.matasar.keyboard.macro.SecretBox
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals the secrets in a backup file with a key made from the user's passphrase, so the file can
 * be restored on another phone, where the Keystore key never reaches. The key is derived once
 * here (PBKDF2, [iterations] rounds over [salt]); the passphrase itself is not kept.
 * JDK classes only, so it runs in JVM tests as it does on the phone.
 */
class PassphraseSecretBox(
    passphrase: CharArray,
    salt: ByteArray,
    iterations: Int = ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) : SecretBox {

    private val key: SecretKeySpec = run {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    override fun seal(plain: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /** Null for a wrong passphrase or a damaged value: GCM refuses both. */
    override fun open(sealed: String): String? = try {
        val bytes = Base64.getDecoder().decode(sealed)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    companion object {
        const val KDF = "PBKDF2WithHmacSHA256"
        const val ITERATIONS = 210_000
        const val SALT_BYTES = 16
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
