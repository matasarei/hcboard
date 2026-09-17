package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.R
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** What the toolbar buttons do; the service implements it. */
interface ToolbarActions {
    fun toggleManagerSheet()
    fun toggleDeveloperMode()
    fun pasteClipboard()
    fun openSettings()
    fun hideKeyboard()
}

/**
 * The 44 dp strip above the keys: developer-mode toggle, clipboard, settings on the left, the
 * chip (or, later, autofill suggestions) in the middle, hide on the right.
 */
@Composable
fun Toolbar(
    developerMode: Boolean,
    chipText: String?,
    actions: ToolbarActions,
    modifier: Modifier = Modifier,
    sheetOpen: Boolean = false,
    /** The 60% board carries its modifiers itself, so the strip toggle has nothing to do there. */
    showDeveloperToggle: Boolean = true,
    center: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.toolbarHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolbarButton(R.drawable.ic_key, "Password manager", active = sheetOpen) { actions.toggleManagerSheet() }
            if (showDeveloperToggle) ToolbarButton(R.drawable.ic_code, "Developer mode", active = developerMode) { actions.toggleDeveloperMode() }
            ToolbarButton(R.drawable.ic_clipboard, "Paste") { actions.pasteClipboard() }
            ToolbarButton(R.drawable.ic_settings, "Settings") { actions.openSettings() }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when {
                center != null -> center()
                chipText != null -> ModifierChip(chipText)
            }
        }
        ToolbarButton(R.drawable.ic_keyboard_hide, "Hide keyboard") { actions.hideKeyboard() }
    }
}

@Composable
fun ToolbarButton(icon: Int, description: String, active: Boolean = false, onClick: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) colors.chip else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (active) colors.onChip else colors.icon,
            modifier = Modifier.height(22.dp),
        )
    }
}
