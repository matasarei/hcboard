package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import net.matasar.keyboard.R
import net.matasar.keyboard.autofill.AutofillActions
import net.matasar.keyboard.autofill.SuggestionEntry
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** The manager's chips in the toolbar. Each is a view the manager draws; we only place them. */
@Composable
fun SuggestionStrip(entries: List<SuggestionEntry>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (entry in entries) {
            AndroidView(
                factory = { entry.view },
                modifier = Modifier
                    .height(32.dp)
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
    }
}

/**
 * The sheet the key button opens: which manager is set, open it, or change it in settings.
 * Drawn over the keys inside the keyboard window, like the mock.
 */
@Composable
fun ManagerSheet(actions: AutofillActions, onDismiss: () -> Unit) {
    val colors = LocalKeyboardColors.current
    val manager = actions.currentManagerLabel()
    val opensItself = manager != null && actions.canOpenManager()
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
                text = "Fill password from",
                color = colors.subtle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
            )
            SheetRow(
                icon = R.drawable.ic_vault,
                title = manager ?: "No password manager set",
                subtitle = if (manager != null) "Preferred autofill service" else "Choose one in Android settings",
                trailing = if (manager != null) R.drawable.ic_check else null,
                onClick = onDismiss,
            )
            if (manager != null) {
                SheetRow(
                    icon = R.drawable.ic_open,
                    title = "Open $manager",
                    // Google's manager lives in Play services, which has no screen a keyboard may
                    // open: say where the tap really goes rather than promise the vault.
                    subtitle = if (opensItself) "Search the vault, then paste" else "No app of its own · opens Android's password settings",
                    onClick = { actions.openManager(); onDismiss() },
                )
            }
            SheetRow(
                icon = R.drawable.ic_tune,
                title = "Change password manager",
                subtitle = "Opens Android settings · Passwords, passkeys & autofill",
                onClick = { actions.changeManager(); onDismiss() },
            )
        }
    }
}

@Composable
private fun SheetRow(icon: Int, title: String, subtitle: String, trailing: Int? = null, onClick: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(colors.functionKey),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.onFunctionKey, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.onPopup, fontSize = 15.sp)
            Text(subtitle, color = colors.subtle, fontSize = 12.sp)
        }
        if (trailing != null) {
            Icon(painterResource(trailing), contentDescription = null, tint = colors.armedRing, modifier = Modifier.size(20.dp))
        }
    }
}
