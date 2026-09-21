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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.matasar.keyboard.R
import net.matasar.keyboard.macro.Macro
import net.matasar.keyboard.macro.summary
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/**
 * The sheet the macro button opens: every macro, one tap plays it into the field. The one playing
 * now offers Stop instead. Building and editing happen on the macros screen, not here.
 */
@Composable
fun MacroSheet(
    macros: List<Macro>,
    running: String?,
    onRun: (Macro) -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
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
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
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
                text = "Macros",
                color = colors.subtle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
            )
            for (macro in macros) {
                if (macro.id == running) {
                    SheetRow(icon = R.drawable.ic_macro, title = "Stop ${macro.name}", subtitle = "Playing…", onClick = onStop)
                } else {
                    SheetRow(icon = R.drawable.ic_macro, title = macro.name, subtitle = macro.summary(), onClick = { onRun(macro) })
                }
            }
            SheetRow(
                icon = R.drawable.ic_open,
                title = "Edit macros",
                subtitle = if (macros.isEmpty()) "No macros yet · build one from blocks" else "Build and change them from blocks",
                onClick = { onEdit(); onDismiss() },
            )
        }
    }
}
