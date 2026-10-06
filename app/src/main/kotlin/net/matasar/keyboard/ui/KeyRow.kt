package net.matasar.keyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.Row as LayoutRow

/**
 * One row of keys. Each key takes its width in units; an indented row (the home row) is padded
 * by whole units so its keys line up with the row above at the same unit width. An inner gap is
 * measured the same way, less the one gap the spacer itself adds to the row.
 */
@Composable
fun KeyRow(
    row: LayoutRow,
    unitWidth: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (Key) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = (unitWidth + gap) * row.leadingUnits,
                end = (unitWidth + gap) * row.trailingUnits,
            ),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        val innerGap = ((unitWidth + gap) * row.innerGapUnits - gap).coerceAtLeast(0.dp)
        for ((index, key) in row.keys.withIndex()) {
            if (row.innerGapUnits > 0f && index == row.keys.lastIndex && index > 0) Spacer(Modifier.width(innerGap))
            Box(modifier = Modifier.weight(key.width)) {
                content(key)
            }
            if (row.innerGapUnits > 0f && index == 0 && row.keys.size > 1) Spacer(Modifier.width(innerGap))
        }
    }
}
