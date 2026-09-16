package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** The chip in the toolbar that names what is armed or locked, or the trackpad hint. */
@Composable
fun ModifierChip(text: String, modifier: Modifier = Modifier) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.chip)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.onChip, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
