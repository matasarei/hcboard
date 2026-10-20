package net.matasar.keyboard.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.settings.BoardSide
import net.matasar.keyboard.settings.Settings

/**
 * The room beside the keyboard, outside the keys' own padding: on its left and on its right, by
 * the screen and never by the reading direction, because the side setting names a physical edge.
 * Applied with `absolutePadding`, which says so whatever direction the keyboard is composed in.
 */
data class SideRoom(val left: Dp, val right: Dp)

/**
 * Where the room a narrowed keyboard leaves goes. The width setting frees a share of [maxWidth]:
 * the phone board's own share, or on the [wide] board its own when it has one. The phone board
 * puts that room where [KeyboardFeel.boardSide] says, against one edge or split around it; the
 * wide board always splits it. A side is never narrower than a cutout's [inset].
 */
fun sideRoom(maxWidth: Dp, wide: Boolean, feel: KeyboardFeel, inset: Dp = 0.dp): SideRoom {
    val scale = (if (wide) feel.wideWidthScale else feel.widthScale).coerceIn(Settings.MIN_WIDTH_SCALE, 1f)
    val free = (maxWidth * (1f - scale)).coerceAtLeast(0.dp)
    val left = when (if (wide) BoardSide.CENTRE else feel.boardSide) {
        BoardSide.LEFT -> 0.dp
        BoardSide.CENTRE -> free / 2
        BoardSide.RIGHT -> free
    }
    return SideRoom(left = maxOf(left, inset), right = maxOf(free - left, inset))
}
