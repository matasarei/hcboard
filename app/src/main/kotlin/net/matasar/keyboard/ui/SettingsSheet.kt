package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.matasar.keyboard.R
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/**
 * The sheet the gear opens: developer mode, which used to have its own toolbar button, whether the
 * strip always shows its buttons, the number row, and the way to the settings screen. Tapping a
 * switch row flips it and closes the sheet.
 */
@Composable
fun SettingsSheet(
    developerMode: Boolean,
    onToggleDeveloperMode: () -> Unit,
    suggestInAppOffered: Boolean,
    suggestInApp: Boolean,
    onToggleSuggestInApp: () -> Unit,
    toolbarAlwaysShown: Boolean,
    onToggleToolbarAlwaysShown: () -> Unit,
    numberRow: Boolean,
    onToggleNumberRow: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    sidePadding: Dp,
    /** The 60% board: the same rows, with the phone board's own switches dimmed and labelled. */
    wide: Boolean = false,
) {
    val colors = LocalKeyboardColors.current
    KeyboardSheet(onDismiss = onDismiss, sidePadding = sidePadding) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.subtle.copy(alpha = 0.4f)),
        )
        Text(
            text = "Keyboard",
            color = colors.subtle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
        )
        SheetRow(
            icon = R.drawable.ic_code,
            title = "Developer mode",
            subtitle = if (wide) PHONE_ONLY else "Ctrl, Alt, Fn and arrows above the keys",
            onClick = onToggleDeveloperMode,
            enabled = !wide,
        ) {
            // Shows the state only: the row takes the tap, so there is one target, not two. A
            // switch without a callback says nothing to TalkBack, so the state is spelled out
            // and merges into the row.
            val onOff = stringResource(if (developerMode) R.string.a11y_state_on else R.string.a11y_state_off)
            Switch(
                checked = developerMode,
                onCheckedChange = null,
                modifier = Modifier.semantics { stateDescription = onOff },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onArmed,
                    checkedTrackColor = colors.armedRing,
                    uncheckedThumbColor = colors.subtle,
                    uncheckedTrackColor = colors.functionKey,
                    uncheckedBorderColor = colors.subtle,
                ),
            )
        }
        // Only where it would change something: the field asked for no suggestions and
        // nothing else is in the way. Elsewhere the row would be a switch that does nothing.
        if (suggestInAppOffered) {
            SheetRow(
                icon = R.drawable.ic_spellcheck,
                title = "Suggest in this app",
                subtitle = "This app asked the keyboard not to suggest",
                onClick = onToggleSuggestInApp,
            ) {
                StateSwitch(suggestInApp)
            }
        }
        SheetRow(
            icon = R.drawable.ic_tune,
            title = "Always show toolbar buttons",
            subtitle = if (wide) PHONE_ONLY else "Never fold them behind ›",
            onClick = onToggleToolbarAlwaysShown,
            enabled = !wide,
        ) {
            StateSwitch(toolbarAlwaysShown)
        }
        SheetRow(
            icon = R.drawable.ic_numbers,
            title = "Number row",
            subtitle = if (wide) PHONE_ONLY else "Digits above the letters",
            onClick = onToggleNumberRow,
            enabled = !wide,
        ) {
            StateSwitch(numberRow)
        }
        SheetRow(
            icon = R.drawable.ic_settings,
            title = "Settings",
            subtitle = "Look, feel, languages, suggestions, macros",
            onClick = onOpenSettings,
        )
    }
}

/**
 * The subtitle of a row whose switch changes only the phone board: the 60% board has its own
 * modifiers, never folds its toolbar and always has digits. The row stays, so the sheet is the
 * same on both boards, but it is dimmed and takes no tap.
 */
private const val PHONE_ONLY = "Phone board only"

/** A switch that only shows a row's state, as Developer mode's does: the row takes the tap. */
@Composable
private fun StateSwitch(checked: Boolean) {
    val colors = LocalKeyboardColors.current
    val onOff = stringResource(if (checked) R.string.a11y_state_on else R.string.a11y_state_off)
    Switch(
        checked = checked,
        onCheckedChange = null,
        modifier = Modifier.semantics { stateDescription = onOff },
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onArmed,
            checkedTrackColor = colors.armedRing,
            uncheckedThumbColor = colors.subtle,
            uncheckedTrackColor = colors.functionKey,
            uncheckedBorderColor = colors.subtle,
        ),
    )
}
