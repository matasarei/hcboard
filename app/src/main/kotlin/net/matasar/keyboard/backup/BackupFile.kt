package net.matasar.keyboard.backup

import kotlinx.serialization.Serializable
import net.matasar.keyboard.macro.Macro
import net.matasar.keyboard.settings.Settings

/**
 * Everything the backup carries, as the file holds it. Readable JSON except the macros' secret
 * texts, which are sealed with the passphrase described by [secrets] (null when there are none).
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val settings: Settings = Settings(),
    /** Custom words: language tag → word → frequency. */
    val words: Map<String, Map<String, Int>> = emptyMap(),
    val macros: List<Macro> = emptyList(),
    val secrets: SecretsHeader? = null,
) {
    companion object {
        const val FORMAT = "hcboard-backup"
        const val VERSION = 1
    }
}

/** How the passphrase key is made, and [check]: a known text sealed with it, to tell a wrong passphrase before anything is written. */
@Serializable
data class SecretsHeader(val kdf: String, val iterations: Int, val salt: String, val check: String)
