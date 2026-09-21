package net.matasar.keyboard.macro

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals secret texts with an AES-GCM key kept in the Android Keystore. The key never leaves the
 * phone, so a secret restored from a backup onto another phone does not open and is typed again.
 * Nothing here is logged: a failure to open is only a null.
 */
class KeystoreSecretBox : SecretBox {

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    override fun open(sealed: String): String? = try {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun existingKey(): SecretKey? =
        (KeyStore.getInstance(KEYSTORE).apply { load(null) }.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    @Synchronized
    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "macro-secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
