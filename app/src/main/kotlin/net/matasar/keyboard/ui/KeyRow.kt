package net.matasar.keyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.Row as LayoutRow

/**
 * One row of keys. Each key takes its width in units; an indented row (the home row) is padded
 * by whole units so its keys line up with the row above at the same unit width.
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
        for (key in row.keys) {
            Box(modifier = Modifier.weight(key.width)) {
                content(key)
            }
        }
    }
}
