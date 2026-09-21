package net.matasar.keyboard.macro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.matasar.keyboard.R
import net.matasar.keyboard.settings.KeyboardThemeFor
import net.matasar.keyboard.settings.Prefs
import net.matasar.keyboard.settings.Settings
import java.util.UUID

/** Where macros are built from blocks: the list of macros, and one macro's blocks. */
class MacrosActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        val store = MacroStore(applicationContext)
        setContent {
            val settings by prefs.settings.collectAsState(initial = Settings())
            KeyboardThemeFor(settings.theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MacrosScreen(store)
                }
            }
        }
    }

    companion object {
        /** The macros screen, opened from the keyboard (a service, so a new task) or from settings. */
        fun intent(context: Context): Intent = Intent(context, MacrosActivity::class.java)
    }
}

@Composable
private fun MacrosScreen(store: MacroStore) {
    val macros = store.macros.collectAsState(initial = null).value ?: return
    // The screen outlives an edit: a save still in flight when the editor closes must finish.
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val open = macros.firstOrNull { it.id == editing }
    if (open != null) {
        MacroEditor(
            macro = open,
            onSave = { scope.launch { store.upsert(it) } },
            onClose = { last -> scope.launch { store.upsert(last) }; editing = null },
        )
    } else {
        MacroList(
            macros = macros,
            scope = scope,
            store = store,
            onOpen = { editing = it.id },
        )
    }
}

@Composable
private fun MacroList(macros: List<Macro>, scope: CoroutineScope, store: MacroStore, onOpen: (Macro) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val newName = stringResource(R.string.macros_new_name)
    val undo = stringResource(R.string.macros_undo)
    val deletedFormat = stringResource(R.string.macros_deleted)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.macros_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.macros_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (macros.isEmpty()) Text(stringResource(R.string.macros_empty))
            for (macro in macros) {
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(macro) }, shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(macro.name, style = MaterialTheme.typography.titleMedium)
                            Text(macro.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            val before = macros
                            scope.launch {
                                store.delete(macro.id)
                                val result = snackbar.showSnackbar(deletedFormat.format(macro.name), actionLabel = undo, withDismissAction = true)
                                if (result == SnackbarResult.ActionPerformed) store.save(before)
                            }
                        }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.macros_delete), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            Button(onClick = {
                val macro = Macro(id = UUID.randomUUID().toString(), name = newName, blocks = emptyList())
                scope.launch {
                    store.upsert(macro)
                    onOpen(macro)
                }
            }) { Text("+ " + stringResource(R.string.macros_new)) }
        }
    }
}

/**
 * One macro's name and blocks, the palette pinned underneath, and a field to try it in. Edits
 * stay here and are saved a moment after the last one, and at once when the editor closes.
 */
@Composable
private fun MacroEditor(macro: Macro, onSave: (Macro) -> Unit, onClose: (Macro) -> Unit) {
    var draft by remember(macro.id) { mutableStateOf(macro) }
    var tryText by remember { mutableStateOf("") }
    LaunchedEffect(draft) {
        if (draft == macro) return@LaunchedEffect
        delay(SAVE_DELAY_MS)
        onSave(draft)
    }
    BackHandler { onClose(draft) }
    val steps = MacroRunner.stepCount(draft.blocks)
    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                BlockPalette(
                    onAdd = { draft = draft.copy(blocks = MacroEdits.insert(draft.blocks, emptyList(), it)) },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onClose(draft) }) { Text("← " + stringResource(R.string.macros_back)) }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.macros_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.macros_script), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (draft.blocks.isEmpty()) {
                Text(stringResource(R.string.macros_script_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (steps > MacroRunner.MAX_STEPS) {
                Text(pluralStringResource(R.plurals.macros_too_long, MacroRunner.MAX_STEPS, MacroRunner.MAX_STEPS), color = MaterialTheme.colorScheme.error)
            }
            BlockStack(root = draft.blocks, container = emptyList(), onChange = { draft = draft.copy(blocks = it) })
            OutlinedTextField(
                value = tryText,
                onValueChange = { tryText = it },
                label = { Text(stringResource(R.string.macros_try)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

private const val SAVE_DELAY_MS = 300L
