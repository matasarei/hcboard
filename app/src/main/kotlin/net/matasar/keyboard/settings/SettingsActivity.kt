package net.matasar.keyboard.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matasar.keyboard.R
import net.matasar.keyboard.ui.theme.KeyboardTheme
import kotlin.math.roundToInt

/** The keyboard's settings: feel, theme, developer-mode behaviour, and a field to try it in. */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        setContent {
            val settings by prefs.settings.collectAsState(initial = Settings())
            KeyboardTheme(darkTheme = settings.theme.asDarkTheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(settings, prefs)
                }
            }
        }
    }
}

fun ThemeChoice.asDarkTheme(): Boolean? = when (this) {
    ThemeChoice.SYSTEM -> null
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}

@Composable
private fun SettingsScreen(settings: Settings, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    var tryText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        Section(stringResource(R.string.settings_section_look))
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChoice.entries.forEach { choice ->
                FilterChip(
                    selected = settings.theme == choice,
                    onClick = { scope.launch { prefs.setTheme(choice) } },
                    label = { Text(choice.label()) },
                )
            }
        }
        Text(
            stringResource(R.string.settings_height, (settings.heightScale * 100).roundToInt()),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = settings.heightScale,
            onValueChange = { scope.launch { prefs.setHeightScale((it * 20).roundToInt() / 20f) } },
            valueRange = 0.8f..1.2f,
            steps = 7,
        )
        SwitchRow(stringResource(R.string.settings_key_borders), settings.keyBorders) { scope.launch { prefs.setKeyBorders(it) } }

        Section(stringResource(R.string.settings_section_feel))
        SwitchRow(stringResource(R.string.settings_haptics), settings.haptics) { scope.launch { prefs.setHaptics(it) } }
        SwitchRow(stringResource(R.string.settings_previews), settings.previews) { scope.launch { prefs.setPreviews(it) } }

        Section(stringResource(R.string.settings_section_developer))
        SwitchRow(stringResource(R.string.settings_editing_shortcuts), settings.editingShortcuts) { scope.launch { prefs.setEditingShortcuts(it) } }
        SwitchRow(stringResource(R.string.settings_double_tap_lock), settings.doubleTapLock) { scope.launch { prefs.setDoubleTapLock(it) } }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        OutlinedTextField(
            value = tryText,
            onValueChange = { tryText = it },
            label = { Text(stringResource(R.string.enable_try_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ThemeChoice.label(): String = stringResource(
    when (this) {
        ThemeChoice.SYSTEM -> R.string.settings_theme_system
        ThemeChoice.LIGHT -> R.string.settings_theme_light
        ThemeChoice.DARK -> R.string.settings_theme_dark
    },
)

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
