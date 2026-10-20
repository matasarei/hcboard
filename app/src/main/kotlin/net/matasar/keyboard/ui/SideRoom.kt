package net.matasar.keyboard.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.settings.BoardSide
import net.matasar.keyboard.settings.Settings

/** The room beside the keyboard: at its start and at its end, outside the keys' own padding. */
data class SideRoom(val start: Dp, val end: Dp)

/**
 * Where the room a narrowed keyboard leaves goes. The width setting frees a share of [maxWidth]:
 * the phone board's own share, or on the [wide] board its own when it has one. The phone board
 * puts that room where [KeyboardFeel.boardSide] says, against one edge or split around it; the
 * wide board always splits it. A side is never narrower than a cutout's [inset].
 */
fun sideRoom(maxWidth: Dp, wide: Boolean, feel: KeyboardFeel, inset: Dp = 0.dp): SideRoom {
    val scale = (if (wide) feel.wideWidthScale else feel.widthScale).coerceIn(Settings.MIN_WIDTH_SCALE, 1f)
    val free = (maxWidth * (1f - scale)).coerceAtLeast(0.dp)
    val start = when (if (wide) BoardSide.CENTRE else feel.boardSide) {
        BoardSide.LEFT -> 0.dp
        BoardSide.CENTRE -> free / 2
        BoardSide.RIGHT -> free
    }
    return SideRoom(start = maxOf(start, inset), end = maxOf(free - start, inset))
}
