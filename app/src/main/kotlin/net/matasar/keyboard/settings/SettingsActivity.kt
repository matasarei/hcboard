package net.matasar.keyboard.settings

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS
import android.widget.Toast
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import net.matasar.keyboard.ime.listVoiceKeyboards
import net.matasar.keyboard.ime.pushEnabledSubtypes
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.matasar.keyboard.R
import net.matasar.keyboard.ime.KeyboardDiagnostics
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.macro.MacrosActivity
import net.matasar.keyboard.ui.theme.KeyboardTheme
import kotlin.math.roundToInt

/** The keyboard's settings: feel, theme, developer-mode behaviour, and a field to try it in. */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        // Android's keyboard list names hcboard's enabled languages; keep it current from here too,
        // since the keyboard itself may not be running while its languages change.
        lifecycleScope.launch {
            prefs.settings.map { it.enabledLanguages }.distinctUntilChanged().collect { pushEnabledSubtypes(applicationContext, it) }
        }
        setContent {
            val settings by prefs.settings.collectAsState(initial = Settings())
            KeyboardThemeFor(settings.theme, settings.accent) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(settings, prefs)
                }
            }
        }
    }
}

/** The keyboard's theme for the user's [ThemeChoice] and [AccentColour]: the one place the choices become colours. */
@Composable
fun KeyboardThemeFor(theme: ThemeChoice, accent: AccentColour, content: @Composable () -> Unit) =
    KeyboardTheme(darkTheme = theme.asDarkTheme(), black = theme == ThemeChoice.BLACK, accent = accent, content = content)

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
    var diagnosticsShown by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val diagnosticsClipLabel = stringResource(R.string.settings_section_diagnostics)
    val diagnosticsCopied = stringResource(R.string.settings_diagnostics_copied)

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
        SwitchRow(stringResource(R.string.settings_number_row), settings.numberRow) { scope.launch { prefs.setNumberRow(it) } }
        Text(stringResource(R.string.settings_number_row_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Section(stringResource(R.string.settings_section_feel))
        SwitchRow(stringResource(R.string.settings_haptics), settings.haptics) { scope.launch { prefs.setHaptics(it) } }
        SwitchRow(stringResource(R.string.settings_previews), settings.previews) { scope.launch { prefs.setPreviews(it) } }
        SwitchRow(stringResource(R.string.settings_double_space_period), settings.doubleSpacePeriod) { scope.launch { prefs.setDoubleSpacePeriod(it) } }
        // The stored setting is whether the phone strip folds; the switch asks the opposite, which is what people look for.
        SwitchRow(stringResource(R.string.settings_always_show_toolbar), !settings.foldToolbar) { scope.launch { prefs.setFoldToolbar(!it) } }
        SwitchRow(stringResource(R.string.settings_voice_input), settings.voiceInput) { scope.launch { prefs.setVoiceInput(it) } }
        if (settings.voiceInput) {
            VoiceKeyboardPicker(settings.voiceKeyboard) { scope.launch { prefs.setVoiceKeyboard(it) } }
        }

        Section(stringResource(R.string.settings_section_wide))
        Text(stringResource(R.string.settings_split), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.splitKeyboard == mode,
                    onClick = { scope.launch { prefs.setSplitKeyboard(mode) } },
                    label = { Text(mode.label()) },
                )
            }
        }
        Text(stringResource(R.string.settings_split_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Section(stringResource(R.string.settings_section_languages))
        // The list is on its own screen: 21 languages and their options would bury everything below.
        Text(
            Languages.all.filter { it == Languages.english || it.tag in settings.enabledLanguages }.joinToString(", ") { it.nativeName },
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(onClick = { context.startActivity(LanguagesActivity.intent(context)) }) {
            Text(stringResource(R.string.settings_languages_open))
        }

        Section(stringResource(R.string.settings_section_suggestions))
        SwitchRow(stringResource(R.string.settings_suggestions), settings.suggestions) { scope.launch { prefs.setSuggestions(it) } }
        SwitchRow(stringResource(R.string.settings_auto_capitalize), settings.autoCapitalize) { scope.launch { prefs.setAutoCapitalize(it) } }
        SwitchRow(stringResource(R.string.settings_auto_correct), settings.autoCorrect) { scope.launch { prefs.setAutoCorrect(it) } }
        OutlinedButton(onClick = { context.startActivity(CustomWordsActivity.intent(context)) }) {
            Text(stringResource(R.string.settings_words_open))
        }

        Section(stringResource(R.string.settings_section_glide))
        SwitchRow(stringResource(R.string.settings_glide), settings.glide) { scope.launch { prefs.setGlide(it) } }
        SwitchRow(stringResource(R.string.settings_glide_trail), settings.glideTrail) { scope.launch { prefs.setGlideTrail(it) } }

        Section(stringResource(R.string.settings_section_developer))
        SwitchRow(stringResource(R.string.settings_editing_shortcuts), settings.editingShortcuts) { scope.launch { prefs.setEditingShortcuts(it) } }
        SwitchRow(stringResource(R.string.settings_double_tap_lock), settings.doubleTapLock) { scope.launch { prefs.setDoubleTapLock(it) } }

        Section(stringResource(R.string.settings_section_macros))
        OutlinedButton(onClick = { context.startActivity(MacrosActivity.intent(context)) }) {
            Text(stringResource(R.string.settings_macros_open))
        }

        Section(stringResource(R.string.settings_section_backup))
        BackupSection(prefs)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        OutlinedTextField(
            value = tryText,
            onValueChange = { tryText = it },
            label = { Text(stringResource(R.string.enable_try_label)) },
            // Sentences, as a chat box asks, so the automatic capital can be tried here.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
        // Hidden until asked for; once shown, the same button copies what is on screen.
        if (!diagnosticsShown) {
            OutlinedButton(onClick = { diagnosticsShown = true }) {
                Text(stringResource(R.string.settings_diagnostics_show))
            }
        } else {
            OutlinedButton(
                onClick = {
                    val report = KeyboardDiagnostics.report()
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(diagnosticsClipLabel, report)))
                    }
                    // Android 13 and later confirm a copy themselves.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, diagnosticsCopied, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(stringResource(R.string.settings_diagnostics_copy))
            }
            SelectionContainer {
                Text(
                    KeyboardDiagnostics.report(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Section(stringResource(R.string.settings_section_about))
        OutlinedButton(onClick = { context.startActivity(LicencesActivity.intent(context)) }) {
            Text(stringResource(R.string.licences_title))
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
private fun SplitMode.label(): String = stringResource(
    when (this) {
        SplitMode.OFF -> R.string.settings_split_off
        SplitMode.AUTO -> R.string.settings_split_auto
        SplitMode.ALWAYS -> R.string.settings_split_always
    },
)

/**
 * Which keyboard the mic hands off to. The list is re-read on every return to this screen, since
 * keyboards are turned on and off in Android's settings; with one keyboard there is nothing to pick,
 * and with none the screen says why the mic is hidden. A pick that is no longer on reads as Automatic.
 */
@Composable
private fun VoiceKeyboardPicker(selected: String?, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    // Read now, not only on resume: an empty first frame would claim no voice keyboard is on.
    var keyboards by remember { mutableStateOf(listVoiceKeyboards(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { keyboards = listVoiceKeyboards(context) }
    // Some builds ship no screen for this action; the button then does nothing rather than crash.
    val openKeyboardSettings = { runCatching { context.startActivity(Intent(ACTION_INPUT_METHOD_SETTINGS)) }; Unit }

    when {
        keyboards.isEmpty() -> {
            Hint(stringResource(R.string.settings_voice_keyboard_none))
            OutlinedButton(onClick = openKeyboardSettings) { Text(stringResource(R.string.settings_voice_keyboard_open)) }
        }
        keyboards.size > 1 -> {
            val current = selected?.takeIf { id -> keyboards.any { it.imeId == id } }
            Text(stringResource(R.string.settings_voice_keyboard), style = MaterialTheme.typography.bodyLarge)
            RadioRow(stringResource(R.string.settings_voice_keyboard_auto), current == null) { onPick(null) }
            keyboards.forEach { keyboard ->
                RadioRow(keyboard.label, current == keyboard.imeId) { onPick(keyboard.imeId) }
            }
            Hint(stringResource(R.string.settings_voice_keyboard_hint))
            OutlinedButton(onClick = openKeyboardSettings) { Text(stringResource(R.string.settings_voice_keyboard_open)) }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    // The whole line is the target, and a screen reader hears one radio choice, not a button and a label.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

