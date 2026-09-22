package net.matasar.keyboard.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.R

/**
 * The NOTICE and the Apache License text, as the build copied them into the APK from the
 * repository root (app/build.gradle.kts, copyLicenceNotices): the attribution the third-party
 * code and word lists ask for, where someone with only the app can read it.
 */
class LicencesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        setContent {
            val settings by prefs.settings.collectAsState(initial = Settings())
            KeyboardThemeFor(settings.theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LicencesScreen()
                }
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, LicencesActivity::class.java)
    }
}

@Composable
private fun LicencesScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.licences_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.licences_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                RawText(R.raw.notice)
                Heading(R.string.licences_libraries)
                // One library or family per line, as the build wrote it: nothing to reflow.
                RawText(R.raw.dependencies, reflow = false)
                Heading(R.string.licences_apache)
                RawText(R.raw.license)
                Heading(R.string.licences_bsd)
                RawText(R.raw.bsd_3_clause)
            }
        }
    }
}

/** What the text below it is, for a reader scrolling past. */
@Composable
private fun Heading(@StringRes id: Int) {
    Text(stringResource(id), style = MaterialTheme.typography.titleSmall)
}

/**
 * A text file from res/raw, read once. A file wrapped for its own width is reflowed to the
 * screen's (see [reflow]); a file written a line at a time is shown as it is.
 */
@Composable
private fun RawText(@RawRes id: Int, reflow: Boolean = true) {
    val resources = LocalResources.current
    val text = remember(resources, id, reflow) {
        resources.openRawResource(id).bufferedReader().use { it.readText() }.let { if (reflow) reflow(it) else it.trim() }
    }
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/**
 * Undoes a text file's hard wrapping, so a phone does not wrap each 80- or 100-column line a
 * second time. A line joins the one before it when that one was long (it was cut to fit the
 * file's width) and the new one does not start a list item; short lines, blank lines, bullets
 * and numbered or lettered clauses keep their breaks. Indentation goes: the screen's font is
 * not monospace.
 */
internal fun reflow(text: String): String {
    val out = StringBuilder()
    var previous = ""
    for (raw in text.lines()) {
        val line = raw.trim()
        val joins = previous.length >= WRAPPED_LINE && line.isNotEmpty() && !LIST_ITEM.containsMatchIn(line)
        when {
            out.isEmpty() -> out.append(line)
            joins -> out.append(' ').append(line)
            else -> out.append('\n').append(line)
        }
        previous = line
    }
    return out.toString().trimEnd()
}

/** A line at least this long was wrapped by its file, not ended by its author. */
private const val WRAPPED_LINE = 40

/** A bullet, or a clause numbered "2." or lettered "(a)": each starts a line of its own. */
private val LIST_ITEM = Regex("""^(- |\(\w{1,3}\) |\d{1,2}\. )""")
