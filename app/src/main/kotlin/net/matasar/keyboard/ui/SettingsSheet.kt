package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.matasar.keyboard.R
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/**
 * The sheet the gear opens: developer mode, which used to have its own toolbar button, and the
 * way to the settings screen. Tapping developer mode flips it and the controller closes the sheet.
 */
@Composable
fun SettingsSheet(
    developerMode: Boolean,
    onToggleDeveloperMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalKeyboardColors.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.popup)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        ) {
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
                subtitle = "Ctrl, Alt, Fn and arrows above the keys",
                onClick = onToggleDeveloperMode,
            ) {
                // Shows the state only: the row takes the tap, so there is one target, not two. A
                // switch without a callback says nothing to TalkBack, so the state is spelled out
                // and merges into the row.
                Switch(
                    checked = developerMode,
                    onCheckedChange = null,
                    modifier = Modifier.semantics { stateDescription = if (developerMode) "On" else "Off" },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onArmed,
                        checkedTrackColor = colors.armedRing,
                        uncheckedThumbColor = colors.subtle,
                        uncheckedTrackColor = colors.functionKey,
                        uncheckedBorderColor = colors.subtle,
                    ),
                )
            }
            SheetRow(
                icon = R.drawable.ic_settings,
                title = "Settings",
                subtitle = "Look, feel, languages, suggestions, macros",
                onClick = onOpenSettings,
            )
        }
    }
}
