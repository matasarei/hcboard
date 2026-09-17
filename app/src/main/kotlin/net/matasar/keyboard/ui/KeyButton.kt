package net.matasar.keyboard.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.matasar.keyboard.haptics.keyDownTick
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyIcon
import net.matasar.keyboard.layout.KeyStyle
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** Colours for one key in its current state. */
data class KeyVisual(val background: Color, val foreground: Color, val ring: Color? = null)

/** What the screen wants to know about a key's gestures. Positions are in root coordinates. */
interface KeyCallbacks {
    fun onPressStart(key: Key, bounds: Rect)
    fun onPressEnd(key: Key)
    fun onTap(key: Key)
    fun onLongPress(key: Key, bounds: Rect): LongPressResult
    fun onLongPressMove(key: Key, rootPosition: Offset)
    fun onLongPressRelease(key: Key, rootPosition: Offset)
    fun onLongPressCancel(key: Key)
    fun onRepeat(key: Key)
}

/**
 * One key. Draws the label or icon, animates its own press with a spring, ticks the haptics on
 * key down, and reports taps, long presses with steering, and repeats to [callbacks].
 */
@Composable
fun KeyButton(
    key: Key,
    label: String,
    visual: KeyVisual,
    height: Dp,
    callbacks: KeyCallbacks,
    modifier: Modifier = Modifier,
    icon: KeyIcon? = key.icon,
    haptics: Boolean = true,
    keyBorders: Boolean = true,
    showLabel: Boolean = true,
    /**
     * The 60% board: every key keeps its top line for legends (the shifted symbol left, the Fn
     * meaning right) and draws its main glyph below it, so the two never meet and every key in a
     * row shares one baseline whether or not it carries a legend.
     */
    legendBand: Boolean = false,
    legend: String? = key.fnLegend,
    /** Tints the Fn legend while Fn is active: the legend that is live right now. */
    legendColor: Color? = null,
    topLegend: String? = key.shiftedLabel,
    /** Tints the shifted legend while Shift is active, the same way. */
    topLegendColor: Color? = null,
    /** Overrides the size of a small (word) label; the strip's narrow keys use it. */
    labelSize: TextUnit = Dimens.labelSize,
    /** Receives the key's bounds in root coordinates; the glide detector maps fingers to keys with it. */
    onBounds: ((Key, Rect) -> Unit)? = null,
) {
    val colors = LocalKeyboardColors.current
    val view = LocalView.current
    val longPressMs = LocalViewConfiguration.current.longPressTimeoutMillis
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val currentCallbacks by rememberUpdatedState(callbacks)
    val currentHaptics by rememberUpdatedState(haptics)
    val shape = RoundedCornerShape(Dimens.keyRadius)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "press",
    )

    val listener = remember(key.id) {
        object : KeyGestureListener {
            private var repeatJob: Job? = null
            private var repeated = false

            override fun onPressStart() {
                pressed = true
                repeated = false
                if (currentHaptics) view.keyDownTick()
                currentCallbacks.onPressStart(key, bounds)
                if (key.repeats) {
                    repeatJob = scope.launch {
                        delay(REPEAT_DELAY_MS)
                        while (isActive) {
                            repeated = true
                            currentCallbacks.onRepeat(key)
                            delay(REPEAT_INTERVAL_MS)
                        }
                    }
                }
            }

            override fun onPressEnd() {
                pressed = false
                repeatJob?.cancel()
                repeatJob = null
                currentCallbacks.onPressEnd(key)
            }

            override fun onTap() {
                if (!repeated) currentCallbacks.onTap(key)
            }

            override fun onLongPress(): LongPressResult {
                if (key.repeats) return LongPressResult.HANDLED
                return currentCallbacks.onLongPress(key, bounds)
            }

            override fun onLongPressMove(position: Offset) =
                currentCallbacks.onLongPressMove(key, bounds.topLeft + position)

            override fun onLongPressRelease(position: Offset) =
                currentCallbacks.onLongPressRelease(key, bounds.topLeft + position)

            override fun onLongPressCancel() = currentCallbacks.onLongPressCancel(key)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned { bounds = it.boundsInRoot(); onBounds?.invoke(key, bounds) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (keyBorders && !pressed) 1.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.keyShadow,
                spotColor = colors.keyShadow,
            )
            .clip(shape)
            .background(if (pressed) colors.pressedKey else visual.background)
            .then(if (visual.ring != null) Modifier.border(2.dp, visual.ring, shape) else Modifier)
            .keyGestures(key.id, longPressMs, listener),
        contentAlignment = Alignment.Center,
    ) {
        if (!showLabel) return@Box
        // A key is two zones, never a stack of paddings: the legend line on top, the glyph in what
        // is left. A key with nothing to say on that line does without it, and the 60% board keeps
        // it on every key so a row shares one baseline. Nothing is dropped on a short key — both
        // zones are sized from the key's own height, so the 80% setting shrinks them instead.
        val legendLine = legendBand || topLegend != null || legend != null
        Column(modifier = Modifier.fillMaxSize()) {
            if (legendLine) {
                // A minimum, not a cap: the line reserves the same room on every key in the row so
                // they share a baseline, and grows rather than clipping a legend if the font is
                // taller than the reserve (a large system font scale).
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.legendLine(height))) {
                    val legendSize = Dimens.legendTextSize(height)
                    if (topLegend != null) {
                        Text(
                            text = topLegend,
                            color = topLegendColor ?: colors.subtle,
                            fontSize = legendSize,
                            // Pinned: the two zones are measured in advance, so the line box has
                            // to be the one Dimens proved the fit against.
                            lineHeight = legendSize * Dimens.legendLineHeightRatio,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
                        )
                    }
                    if (legend != null) {
                        Text(
                            text = legend,
                            color = legendColor ?: colors.subtle,
                            fontSize = legendSize,
                            lineHeight = legendSize * Dimens.legendLineHeightRatio,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 5.dp),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(
                        painter = painterResource(icon.drawable()),
                        contentDescription = key.label,
                        tint = visual.foreground,
                        modifier = Modifier.height(Dimens.iconSize(height, legendLine)),
                    )
                } else {
                    val word = key.style != KeyStyle.LETTER || label.length > 1
                    val size = when {
                        word -> Dimens.wordSize(height, labelSize)
                        legendLine -> Dimens.glyphSize(height)
                        else -> Dimens.plainGlyphSize(height)
                    }
                    Text(
                        text = label,
                        color = visual.foreground,
                        fontSize = size,
                        lineHeight = size * Dimens.glyphLineHeightRatio,
                        fontWeight = if (word) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private const val REPEAT_DELAY_MS = 350L
private const val REPEAT_INTERVAL_MS = 50L
