package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * The words a glide could have meant, best first, in the toolbar. The first is what was
 * committed and is shown emphasised; tapping another swaps it in.
 */
@Composable
fun TextSuggestionStrip(words: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = modifier.fillMaxWidth().height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        words.take(MAX_SHOWN).forEachIndexed { index, word ->
            val committed = index == 0
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (committed) colors.chip else colors.functionKey)
                    .clickable { onPick(word) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = word,
                    color = if (committed) colors.onChip else colors.onFunctionKey,
                    fontSize = 14.sp,
                    fontWeight = if (committed) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val MAX_SHOWN = 4
