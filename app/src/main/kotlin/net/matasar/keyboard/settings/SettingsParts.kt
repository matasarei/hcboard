package net.matasar.keyboard.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.R
import net.matasar.keyboard.layout.BulgarianLayout
import net.matasar.keyboard.layout.PortugueseSpelling

// Pieces the settings screens share: the Settings screen and the Languages screen.

@Composable
internal fun PortugueseSpelling.label(): String = stringResource(
    when (this) {
        PortugueseSpelling.PORTUGAL -> R.string.settings_pt_spelling_portugal
        PortugueseSpelling.BRAZIL -> R.string.settings_pt_spelling_brazil
    },
)

@Composable
internal fun BulgarianLayout.label(): String = stringResource(
    when (this) {
        BulgarianLayout.PHONETIC -> R.string.settings_bg_layout_phonetic
        BulgarianLayout.STANDARD -> R.string.settings_bg_layout_standard
    },
)

@Composable
internal fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
