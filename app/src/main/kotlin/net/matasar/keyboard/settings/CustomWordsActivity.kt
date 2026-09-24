package net.matasar.keyboard.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matasar.keyboard.R
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.nlp.CustomWord
import net.matasar.keyboard.nlp.CustomWordStore
import net.matasar.keyboard.nlp.CustomWords

/** Where the user adds words to a language's list or blocks words out of it. */
class CustomWordsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        val store = CustomWordStore(applicationContext)
        setContent {
            val settings by prefs.settings.collectAsState(initial = Settings())
            val words by store.words.collectAsState(initial = emptyMap())
            KeyboardThemeFor(settings.theme, settings.accent) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CustomWordsScreen(settings, words, store)
                }
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, CustomWordsActivity::class.java)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomWordsScreen(settings: Settings, words: CustomWords, store: CustomWordStore) {
    val scope = rememberCoroutineScope()
    val languages = Languages.all.filter { it.tag in settings.enabledLanguages }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val language = languages.firstOrNull { it.tag == chosen }
        ?: languages.firstOrNull { it.tag == settings.currentLanguage }
        ?: languages.firstOrNull()
        ?: return
    var input by rememberSaveable { mutableStateOf("") }
    val word = CustomWord.normalize(input)
    fun save(frequency: Int) {
        val clean = word ?: return
        scope.launch { store.set(language.tag, clean, frequency) }
        input = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.words_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.words_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (languages.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in languages) {
                    FilterChip(selected = option == language, onClick = { chosen = option.tag }, label = { Text(option.nativeName) })
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.words_field, language.nativeName)) },
            singleLine = true,
            isError = input.isNotBlank() && word == null,
            supportingText = if (input.isNotBlank() && word == null) {
                { Text(stringResource(R.string.words_invalid, CustomWord.MAX_LENGTH)) }
            } else {
                null
            },
            // A word the user is spelling out for the dictionary is not one to correct.
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { save(CustomWord.ADDED) }, enabled = word != null) { Text(stringResource(R.string.words_add)) }
            OutlinedButton(onClick = { save(CustomWord.BLOCKED) }, enabled = word != null) { Text(stringResource(R.string.words_block)) }
        }
        val entries = words[language.tag].orEmpty().entries.sortedBy { it.key.lowercase() }
        if (entries.isEmpty()) {
            Text(stringResource(R.string.words_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for ((entry, frequency) in entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (frequency == CustomWord.BLOCKED) R.string.words_blocked else R.string.words_added),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (frequency == CustomWord.BLOCKED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { scope.launch { store.remove(language.tag, entry) } }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.words_delete, entry), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
