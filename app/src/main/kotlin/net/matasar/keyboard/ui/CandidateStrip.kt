package net.matasar.keyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.matasar.keyboard.nlp.Candidates
import net.matasar.keyboard.nlp.WordCandidates
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/**
 * The words for the word under the cursor, in three equal slots: as typed, then the correction
 * (tinted, because a separator will apply it) or a completion, then one more. After a glide
 * the slots hold the glided word and its alternatives. Tapping a word swaps it in.
 */
@Composable
fun CandidateStrip(candidates: WordCandidates, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalKeyboardColors.current
    Row(modifier = modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(Candidates.MAX_WORDS) { index ->
            val word = candidates.words.getOrNull(index)
            val correction = word != null && word == candidates.correction
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (word != null) Modifier.clickable { onPick(word) } else Modifier)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (word != null) {
                    Text(
                        text = word,
                        color = if (correction) colors.armedRing else colors.onKey,
                        fontSize = 16.sp,
                        fontWeight = if (correction) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
