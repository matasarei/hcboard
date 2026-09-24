package net.matasar.keyboard.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.matasar.keyboard.R
import net.matasar.keyboard.ime.pushEnabledSubtypes
import net.matasar.keyboard.layout.BulgarianLayout
import net.matasar.keyboard.layout.Language
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.PortugueseSpelling

/**
 * Which languages the keyboard types, and each one's own choice (Russian's Bulgarian words,
 * Bulgarian's board, Portuguese's spelling): too long a list to live on the Settings screen.
 */
class LanguagesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        // Android's keyboard list names the enabled languages; this is where they change now, and
        // the Settings screen behind it may be gone.
        lifecycleScope.launch {
            prefs.settings.map { it.enabledLanguages }.distinctUntilChanged().collect { pushEnabledSubtypes(applicationContext, it) }
        }
        setContent {
            // Null until the stored settings arrive: the groups are fixed from the first real set.
            val settings by prefs.settings.collectAsState(initial = null)
            val current = settings ?: return@setContent
            KeyboardThemeFor(current.theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LanguagesScreen(current, prefs)
                }
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, LanguagesActivity::class.java)
    }
}

/**
 * The languages switched on in [enabled], in the globe's order, and every other language by its
 * English name. English is in neither: it is always on, and the screen says so instead.
 */
internal fun languageGroups(enabled: Set<String>): Pair<List<Language>, List<Language>> {
    val (on, off) = (Languages.all - Languages.english).partition { it.tag in enabled }
    return on to off.sortedBy { it.englishName }
}

@Composable
private fun LanguagesScreen(settings: Settings, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    // Grouped once, as the screen opens: a language switched on or off stays where it is until
    // the screen is opened again.
    val (on, more) = remember { languageGroups(settings.enabledLanguages) }

    @Composable
    fun LanguageRow(language: Language) {
        val enabled = language.tag in settings.enabledLanguages
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${language.nativeName} · ${language.englishName}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { scope.launch { prefs.setLanguageEnabled(language.tag, it) } })
        }
        if (enabled) LanguageOptions(language, settings, prefs)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.languages_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.settings_languages_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // With two languages both ways toggle between them; the choice matters from three.
        if (settings.enabledLanguages.size > 2) {
            Section(stringResource(R.string.languages_globe_tap))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlobeTap.entries.forEach { tap ->
                    FilterChip(
                        selected = settings.globeTap == tap,
                        onClick = { scope.launch { prefs.setGlobeTap(tap) } },
                        label = { Text(tap.label()) },
                    )
                }
            }
            OptionHint(stringResource(R.string.languages_globe_tap_hint))
        }
        if (on.isNotEmpty()) {
            Section(stringResource(R.string.languages_section_on))
            on.forEach { LanguageRow(it) }
        }
        if (more.isNotEmpty()) {
            Section(stringResource(R.string.languages_section_more))
            more.forEach { LanguageRow(it) }
        }
    }
}

/** A language's own choice, under its row while it is on: Russian's Bulgarian words, Bulgarian's board, Portuguese's spelling. */
@Composable
private fun LanguageOptions(language: Language, settings: Settings, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    when (language) {
        Languages.russian -> Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
            SwitchRow(stringResource(R.string.settings_ru_bg_vocabulary), settings.ruBulgarianVocabulary) {
                scope.launch { prefs.setRuBulgarianVocabulary(it) }
            }
            OptionHint(stringResource(R.string.settings_ru_bg_vocabulary_hint))
        }
        Languages.bulgarian -> Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.settings_bg_layout), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BulgarianLayout.entries.forEach { layout ->
                    FilterChip(
                        selected = settings.bulgarianLayout == layout,
                        onClick = { scope.launch { prefs.setBulgarianLayout(layout) } },
                        label = { Text(layout.label()) },
                    )
                }
            }
            OptionHint(stringResource(R.string.settings_bg_layout_hint))
        }
        Languages.portuguese -> Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.settings_pt_spelling), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PortugueseSpelling.entries.forEach { spelling ->
                    FilterChip(
                        selected = settings.portugueseSpelling == spelling,
                        onClick = { scope.launch { prefs.setPortugueseSpelling(spelling) } },
                        label = { Text(spelling.label()) },
                    )
                }
            }
            OptionHint(stringResource(R.string.settings_pt_spelling_hint))
        }
        else -> Unit
    }
}

@Composable
private fun OptionHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
