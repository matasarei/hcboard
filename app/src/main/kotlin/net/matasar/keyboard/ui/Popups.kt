package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.ui.theme.LocalKeyboardColors
import kotlin.math.roundToInt

/** The press preview bubble over a key. */
data class PressPreview(val anchor: Rect, val label: String)

/** The accent row over a long-pressed key, with the entry the finger is on. */
data class AccentChoice(val anchor: Rect, val candidates: List<String>, val selected: Int) {
    /** Where the popup's left edge sits, in root pixels, given the cell width and the root width. */
    fun left(cellPx: Float, paddingPx: Float, rootWidthPx: Float): Float {
        val width = candidates.size * cellPx + paddingPx * 2
        return (anchor.left - paddingPx - cellPx / 4).coerceIn(4f, (rootWidthPx - width - 4f).coerceAtLeast(4f))
    }

    fun indexAt(rootX: Float, cellPx: Float, paddingPx: Float, rootWidthPx: Float): Int =
        ((rootX - left(cellPx, paddingPx, rootWidthPx) - paddingPx) / cellPx).toInt().coerceIn(0, candidates.lastIndex)
}

/** Popup state shared by the keys (who set it) and the overlay layer (which draws it). */
@Stable
class PopupState {
    var preview: PressPreview? by mutableStateOf(null)
    var accents: AccentChoice? by mutableStateOf(null)
    var rootWidthPx: Float by mutableStateOf(0f)
    var density: Float = 1f

    /** The glide trail in root coordinates; empty when no finger is gliding. */
    var trail: List<Offset> by mutableStateOf(emptyList())

    fun clear() {
        preview = null
        accents = null
        trail = emptyList()
    }
}

object PopupMetrics {
    // The press bubble is the accent row's sibling: one cell's shape, a little wider so a broad
    // glyph is not cramped, and the same glyph size, so the two popups never drift apart.
    val previewWidth = 48.dp
    val previewHeight = 50.dp
    val gap = 4.dp
    val accentCell = 40.dp
    val accentHeight = 46.dp
    val accentPadding = 4.dp
    /** How far the keyboard extends above the toolbar so popups have room; transparent to the app. */
    val overhang = 68.dp
}

/** Draws whichever popup is open. Lives at the root so it can extend above the top row. */
@Composable
fun PopupLayer(state: PopupState) {
    val colors = LocalKeyboardColors.current
    val density = LocalDensity.current
    state.density = density.density
    val shape = RoundedCornerShape(Dimens.popupRadius)

    state.preview?.let { preview ->
        val width = with(density) { PopupMetrics.previewWidth.toPx() }
        val height = with(density) { PopupMetrics.previewHeight.toPx() }
        val gap = with(density) { PopupMetrics.gap.toPx() }
        val x = preview.anchor.center.x - width / 2
        val y = preview.anchor.top - gap - height
        Box(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .shadow(6.dp, shape)
                .clip(shape)
                .background(colors.popup)
                .width(PopupMetrics.previewWidth)
                .height(PopupMetrics.previewHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                preview.label,
                color = colors.onPopup,
                fontSize = Dimens.plainGlyphSize(PopupMetrics.accentHeight),
                fontWeight = FontWeight.Medium,
            )
        }
    }

    state.accents?.let { accents ->
        val cell = with(density) { PopupMetrics.accentCell.toPx() }
        val padding = with(density) { PopupMetrics.accentPadding.toPx() }
        val height = with(density) { (PopupMetrics.accentHeight + PopupMetrics.accentPadding * 2).toPx() }
        val gap = with(density) { PopupMetrics.gap.toPx() }
        val x = accents.left(cell, padding, state.rootWidthPx)
        val y = accents.anchor.top - gap - height
        Row(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .shadow(6.dp, shape)
                .clip(shape)
                .background(colors.popup)
                .padding(PopupMetrics.accentPadding),
        ) {
            accents.candidates.forEachIndexed { index, glyph ->
                val selected = index == accents.selected
                Box(
                    modifier = Modifier
                        .width(PopupMetrics.accentCell)
                        .height(PopupMetrics.accentHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) colors.action else colors.popup),
                    contentAlignment = Alignment.Center,
                ) {
                    // The same size the letter on the key has: an accent cell is a key's height,
                    // and a row of accents that read smaller than the key under them looks wrong.
                    Text(
                        glyph,
                        color = if (selected) colors.onAction else colors.onPopup,
                        fontSize = Dimens.plainGlyphSize(PopupMetrics.accentHeight),
                    )
                }
            }
        }
    }
}
