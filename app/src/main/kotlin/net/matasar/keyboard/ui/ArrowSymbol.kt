package net.matasar.keyboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The four cardinal arrow directions, with their rotation angle clockwise from UP.
 */
enum class ArrowDirection(val degrees: Float) {
    UP(0f),
    RIGHT(90f),
    DOWN(180f),
    LEFT(270f);

    companion object {
        fun fromChar(c: Char): ArrowDirection? = when (c) {
            '↑' -> UP
            '→' -> RIGHT
            '↓' -> DOWN
            '←' -> LEFT
            else -> null
        }

        fun fromString(s: String?): ArrowDirection? =
            if (s != null && s.length == 1) fromChar(s[0]) else null
    }
}

/**
 * Draws a crisp, geometric arrow pointing in [direction].
 *
 * Rotating the exact same base geometry ensures that arrows pointing in all four directions have
 * identical shaft length, stroke weight, arrowhead angle, and bounding box, eliminating the
 * visual inconsistencies introduced by system font fallbacks.
 */
@Composable
fun ArrowSymbol(
    direction: ArrowDirection,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = (size.value * 0.1f).dp.coerceAtLeast(1.2.dp),
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f

        // Span ~76% of canvas height, with wings at 45 degrees spanning ~22%
        val arrowHalfLength = s * 0.38f
        val wingLength = s * 0.22f
        val strokePx = strokeWidth.toPx()

        rotate(degrees = direction.degrees, pivot = Offset(cx, cy)) {
            val tipY = cy - arrowHalfLength
            val bottomY = cy + arrowHalfLength

            val path = Path().apply {
                // Arrowhead wings
                moveTo(cx - wingLength, tipY + wingLength)
                lineTo(cx, tipY)
                lineTo(cx + wingLength, tipY + wingLength)
                // Shaft
                moveTo(cx, tipY)
                lineTo(cx, bottomY)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}
