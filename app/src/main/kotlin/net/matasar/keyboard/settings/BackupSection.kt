package net.matasar.keyboard.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.matasar.keyboard.R
import net.matasar.keyboard.backup.BackupCodec
import net.matasar.keyboard.backup.BackupFile
import net.matasar.keyboard.backup.NotABackup
import net.matasar.keyboard.backup.Restored
import net.matasar.keyboard.backup.WrongPassphrase
import net.matasar.keyboard.macro.MacroStore
import net.matasar.keyboard.nlp.CustomWordStore
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** The smallest passphrase an export accepts. */
private const val MIN_PASSPHRASE = 8

/**
 * Export and import of settings, custom words and macros, through the system's file picker.
 *
 * Every piece of state here is `remember`, never `rememberSaveable`: the passphrase and the
 * opened secrets must not be written into a Bundle. An activity recreated mid-way (a rotation
 * while the picker is up) loses them, and the user starts over.
 */
@Composable
fun BackupSection(prefs: Prefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val macroStore = remember { MacroStore(context.applicationContext) }
    val wordStore = remember { CustomWordStore(context.applicationContext) }

    // Export: the passphrase waits here while the picker chooses where the file goes.
    var askExportPassphrase by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    // Import: the file read, then what it restores once opened, waiting for the confirmation.
    var importFile by remember { mutableStateOf<BackupFile?>(null) }
    var importPassphraseError by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf<Restored?>(null) }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val passphrase = exportPassphrase
        exportPassphrase = null
        if (uri == null) {
            passphrase?.fill('\u0000')
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    val text = BackupCodec.encode(prefs.settings.first(), wordStore.words.first(), macroStore.macros.first(), passphrase)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
                }
            } catch (_: Exception) {
                // A passphrase lost to a recreated activity, or a file that could not be written.
                false
            } finally {
                passphrase?.fill('\u0000')
            }
            toast(context, if (ok) R.string.backup_exported else R.string.backup_export_failed)
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) { readBackup(context, uri) }
            } catch (_: TooLarge) {
                toast(context, R.string.backup_too_large)
                return@launch
            } catch (_: NotABackup) {
                toast(context, R.string.backup_not_a_backup)
                return@launch
            } catch (_: Exception) {
                toast(context, R.string.backup_not_a_backup)
                return@launch
            }
            if (BackupCodec.needsPassphrase(file)) {
                importPassphraseError = false
                importFile = file
            } else {
                restore = BackupCodec.open(file, null)
            }
        }
    }

    Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            scope.launch {
                if (BackupCodec.needsPassphrase(macroStore.macros.first())) {
                    askExportPassphrase = true
                } else {
                    createDocument.launch("hcboard-backup-${LocalDate.now()}.json")
                }
            }
        }) { Text(stringResource(R.string.backup_export)) }
        OutlinedButton(onClick = {
            openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }) { Text(stringResource(R.string.backup_import)) }
    }

    if (askExportPassphrase) {
        PassphraseDialog(
            confirm = true,
            error = false,
            onDismiss = { askExportPassphrase = false },
            onDone = { passphrase ->
                askExportPassphrase = false
                exportPassphrase = passphrase
                createDocument.launch("hcboard-backup-${LocalDate.now()}.json")
            },
        )
    }

    importFile?.let { file ->
        PassphraseDialog(
            confirm = false,
            error = importPassphraseError,
            onDismiss = { importFile = null },
            onDone = { passphrase ->
                scope.launch {
                    try {
                        // PBKDF2 takes a moment; off the main thread.
                        val opened = withContext(Dispatchers.Default) { BackupCodec.open(file, passphrase) }
                        importFile = null
                        restore = opened
                    } catch (_: WrongPassphrase) {
                        importPassphraseError = true
                    } finally {
                        passphrase.fill('\u0000')
                    }
                }
            },
        )
    }

    restore?.let { restored ->
        AlertDialog(
            onDismissRequest = { restore = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    restore = null
                    scope.launch {
                        // Macros first: the only write that can refuse (a secret the Keystore will
                        // not seal), and then nothing else has changed.
                        if (!macroStore.save(restored.macros)) {
                            toast(context, R.string.macros_secret_not_saved)
                            return@launch
                        }
                        wordStore.replaceAll(restored.words)
                        prefs.replaceAll(restored.settings)
                        toast(context, R.string.backup_restored)
                    }
                }) { Text(stringResource(R.string.backup_replace)) }
            },
            dismissButton = { TextButton(onClick = { restore = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/**
 * Asks for the backup's passphrase: twice and at least [MIN_PASSPHRASE] long when [confirm]
 * (an export). The typed text lives in `remember` only; [onDone] gets it as an array the caller wipes.
 */
@Composable
private fun PassphraseDialog(confirm: Boolean, error: Boolean, onDismiss: () -> Unit, onDone: (CharArray) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val valid = if (confirm) first.length >= MIN_PASSPHRASE && first == second else first.isNotEmpty()
    val options = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (confirm) R.string.backup_passphrase_new_title else R.string.backup_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (confirm) {
                        pluralStringResource(R.plurals.backup_passphrase_new_hint, MIN_PASSPHRASE, MIN_PASSPHRASE)
                    } else {
                        stringResource(R.string.backup_passphrase_hint)
                    },
                )
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it },
                    label = { Text(stringResource(R.string.backup_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = options,
                    isError = error,
                    supportingText = if (error) {
                        { Text(stringResource(R.string.backup_wrong_passphrase)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirm) {
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        label = { Text(stringResource(R.string.backup_passphrase_again)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = options,
                        isError = second.isNotEmpty() && second != first,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(first.toCharArray()) }, enabled = valid) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private class TooLarge : Exception()

/** Reads the chosen file, refusing one over [BackupCodec.MAX_BYTES] before it is all in memory. */
private fun readBackup(context: Context, uri: Uri): BackupFile {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > BackupCodec.MAX_BYTES) throw TooLarge()
        }
        out.toByteArray()
    } ?: throw NotABackup("the file could not be opened")
    return BackupCodec.read(bytes.toString(Charsets.UTF_8))
}

private fun toast(context: Context, message: Int) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
