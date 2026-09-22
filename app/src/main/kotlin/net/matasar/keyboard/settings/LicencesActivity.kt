package net.matasar.keyboard.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
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
import androidx.compose.ui.text.font.FontFamily
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
                RawText(R.raw.license)
            }
        }
    }
}

/** A text file from res/raw, read once; monospace, because both files are laid out for it. */
@Composable
private fun RawText(@RawRes id: Int) {
    val resources = LocalResources.current
    val text = remember(resources, id) { resources.openRawResource(id).bufferedReader().use { it.readText() } }
    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
}
