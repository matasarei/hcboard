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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import net.matasar.keyboard.R
import net.matasar.keyboard.ime.KeyboardDiagnostics
import net.matasar.keyboard.layout.Languages
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
            KeyboardThemeFor(settings.theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(settings, prefs)
                }
            }
        }
    }
}

/** The keyboard's theme for the user's [ThemeChoice]: the one place the choice becomes colours. */
@Composable
fun KeyboardThemeFor(theme: ThemeChoice, content: @Composable () -> Unit) =
    KeyboardTheme(darkTheme = theme.asDarkTheme(), black = theme == ThemeChoice.BLACK, content = content)

fun ThemeChoice.asDarkTheme(): Boolean? = when (this) {
    ThemeChoice.SYSTEM -> null
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK, ThemeChoice.BLACK -> true
}

@Composable
private fun SettingsScreen(settings: Settings, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    var tryText by remember { mutableStateOf("") }
    // Never rememberSaveable: a test password must not outlive the screen.
    var tryPassword by remember { mutableStateOf("") }

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
        Text(
            stringResource(R.string.settings_width, (settings.widthScale * 100).roundToInt()),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = settings.widthScale,
            onValueChange = { scope.launch { prefs.setWidthScale((it * 20).roundToInt() / 20f) } },
            valueRange = 0.7f..1f,
            steps = 5,
        )
        SwitchRow(stringResource(R.string.settings_bottom_padding_auto), settings.bottomPaddingAuto) { scope.launch { prefs.setBottomPaddingAuto(it) } }
        Text(stringResource(R.string.settings_bottom_padding_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!settings.bottomPaddingAuto) {
            Text(
                stringResource(R.string.settings_bottom_padding, settings.bottomPaddingDp),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = settings.bottomPaddingDp.toFloat(),
                onValueChange = { scope.launch { prefs.setBottomPaddingDp((it / 4).roundToInt() * 4) } },
                valueRange = 0f..Settings.MAX_BOTTOM_PADDING_DP.toFloat(),
                steps = Settings.MAX_BOTTOM_PADDING_DP / 4 - 1,
            )
        }
        SwitchRow(stringResource(R.string.settings_key_borders), settings.keyBorders) { scope.launch { prefs.setKeyBorders(it) } }

        Section(stringResource(R.string.settings_section_feel))
        SwitchRow(stringResource(R.string.settings_haptics), settings.haptics) { scope.launch { prefs.setHaptics(it) } }
        SwitchRow(stringResource(R.string.settings_previews), settings.previews) { scope.launch { prefs.setPreviews(it) } }

        Section(stringResource(R.string.settings_section_languages))
        Text(stringResource(R.string.settings_languages_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (language in Languages.all) {
            val enabled = language.tag in settings.enabledLanguages
            SwitchRow("${language.nativeName} · ${language.englishName}", enabled) { scope.launch { prefs.setLanguageEnabled(language.tag, it) } }
            if (language.tag == "ru" && enabled) {
                Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
                    SwitchRow(stringResource(R.string.settings_ru_bg_vocabulary), settings.ruBulgarianVocabulary) {
                        scope.launch { prefs.setRuBulgarianVocabulary(it) }
                    }
                    Text(
                        stringResource(R.string.settings_ru_bg_vocabulary_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Section(stringResource(R.string.settings_section_suggestions))
        SwitchRow(stringResource(R.string.settings_suggestions), settings.suggestions) { scope.launch { prefs.setSuggestions(it) } }
        SwitchRow(stringResource(R.string.settings_auto_correct), settings.autoCorrect) { scope.launch { prefs.setAutoCorrect(it) } }

        Section(stringResource(R.string.settings_section_glide))
        SwitchRow(stringResource(R.string.settings_glide), settings.glide) { scope.launch { prefs.setGlide(it) } }
        SwitchRow(stringResource(R.string.settings_glide_trail), settings.glideTrail) { scope.launch { prefs.setGlideTrail(it) } }

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
        // A password field to try the keyboard's password behaviour on (no suggestions, no glide,
        // no key previews) and to see what the password manager offers. Masked, and tagged as a
        // password so autofill treats it as one.
        OutlinedTextField(
            value = tryPassword,
            onValueChange = { tryPassword = it },
            label = { Text(stringResource(R.string.settings_try_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
        )

        Section(stringResource(R.string.settings_section_diagnostics))
        Text(stringResource(R.string.settings_diagnostics_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                KeyboardDiagnostics.field + "\n\n" + KeyboardDiagnostics.insets,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ThemeChoice.label(): String = stringResource(
    when (this) {
        ThemeChoice.SYSTEM -> R.string.settings_theme_system
        ThemeChoice.LIGHT -> R.string.settings_theme_light
        ThemeChoice.DARK -> R.string.settings_theme_dark
        ThemeChoice.BLACK -> R.string.settings_theme_black
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
