package net.matasar.keyboard.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import net.matasar.keyboard.R
import net.matasar.keyboard.settings.SettingsActivity
import net.matasar.keyboard.ui.theme.KeyboardTheme

private const val PRIVACY_POLICY_URL = "https://github.com/matasarei/hcboard/blob/main/docs/PRIVACY.md"

/**
 * Launcher screen: walks the user through enabling the keyboard in system settings and
 * choosing it as the current input method, then offers a field to try it in.
 */
class EnableActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeyboardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EnableScreen()
                }
            }
        }
    }
}

/** Whether this app's input method is in the user's enabled list. */
internal fun isImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

/** Whether this app's input method is the one currently selected. */
internal fun isImeSelected(context: Context): Boolean {
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return current?.startsWith(context.packageName + "/") == true
}

@Composable
private fun EnableScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isImeEnabled(context)) }
    var selected by remember { mutableStateOf(isImeSelected(context)) }
    var tryText by remember { mutableStateOf("") }

    // Both states change in system UI outside this activity; refresh when we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = isImeEnabled(context)
        selected = isImeSelected(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.enable_title), style = MaterialTheme.typography.headlineSmall)

        StepCard(
            title = stringResource(R.string.enable_step1_title),
            body = stringResource(R.string.enable_step1_body),
            done = enabled,
            buttonLabel = stringResource(R.string.enable_step1_button),
            onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
        )
        StepCard(
            title = stringResource(R.string.enable_step2_title),
            body = stringResource(R.string.enable_step2_body),
            done = selected,
            buttonLabel = stringResource(R.string.enable_step2_button),
            onClick = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            },
        )

        if (enabled && selected) {
            Button(
                onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.enable_open_settings))
            }
        }

        OutlinedTextField(
            value = tryText,
            onValueChange = { tryText = it },
            label = { Text(stringResource(R.string.enable_try_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.enable_privacy_policy))
        }
    }
}

@Composable
private fun StepCard(title: String, body: String, done: Boolean, buttonLabel: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(if (done) R.string.enable_done else R.string.enable_not_yet),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick, enabled = !done) { Text(buttonLabel) }
        }
    }
}
