package net.matasar.keyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.Row as LayoutRow

/**
 * One row of keys. Each key takes its width in units; an indented row (the home row) is padded
 * by whole units so its keys line up with the row above at the same unit width. An inner gap is
 * measured the same way, less the one gap the spacer itself adds to the row. [onGrid] (the phone
 * board) measures each key as [keySpan] does, so rows of the same units share their edges whatever
 * their key counts; the wide board keeps its keys shared out by units alone.
 */
@Composable
fun KeyRow(
    row: LayoutRow,
    unitWidth: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    onGrid: Boolean = false,
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
            // Keyed by the key, not its place: when the rows change under a finger (a number row
            // comes in), a held key keeps its own gesture and no other key inherits it.
            key(key.id) {
                Box(modifier = Modifier.weight(if (onGrid) keySpan(key.width, unitWidth.value, gap.value) else key.width)) {
                    content(key)
                }
            }
            if (row.innerGapUnits > 0f && index == 0 && row.keys.size > 1) Spacer(Modifier.width(innerGap))
        }
    }
}

/**
 * How much of its row a key of [units] takes: what [units] one-unit keys and the gaps between
 * them cover. Weighted by units alone, a row of fewer keys, with fewer gaps, would hand each unit
 * more of the width, and the bottom row's keys drift right of the Shift row's above them.
 */
internal fun keySpan(units: Float, unitWidth: Float, gap: Float): Float =
    (units * (unitWidth + gap) - gap).coerceAtLeast(MIN_SPAN)

/** A weight must be positive, even before the board has a width. */
private const val MIN_SPAN = 0.01f
