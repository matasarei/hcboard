package net.matasar.keyboard.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
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
    showLabel: Boolean = true,
    /**
     * Sizing hint: on the 60% board every letter in a row is sized as though it carried legends,
     * so plain keys and keys with alts share the exact same font size and vertical center.
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
    /** Whether the key repeats while held down in its current state. */
    repeats: Boolean = key.repeats,
    /** What TalkBack reads after the key's name: a latching key's state (see spokenState). */
    stateDescription: String? = null,
    /** A character key says "Dot" to TalkBack rather than its character: a password, spoken out loud. */
    obscured: Boolean = false,
    /** The field's action (`EditorInfo.IME_ACTION_*`), which Enter says instead of its own name. */
    editorAction: Int? = null,
    /** What a screen reader can do with the key besides typing it (see keyActions); usually none. */
    customActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val colors = LocalKeyboardColors.current
    val view = LocalView.current
    val longPressMs = LocalViewConfiguration.current.longPressTimeoutMillis
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val currentCallbacks by rememberUpdatedState(callbacks)
    val currentHaptics by rememberUpdatedState(haptics)
    val currentRepeats by rememberUpdatedState(repeats)
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
                if (currentRepeats) {
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
                if (currentRepeats) return LongPressResult.HANDLED
                return currentCallbacks.onLongPress(key, bounds)
            }

            override fun onLongPressMove(position: Offset) =
                currentCallbacks.onLongPressMove(key, bounds.topLeft + position)

            override fun onLongPressRelease(position: Offset) =
                currentCallbacks.onLongPressRelease(key, bounds.topLeft + position)

            override fun onLongPressCancel() = currentCallbacks.onLongPressCancel(key)
        }
    }

    // One node per key for TalkBack: what the key types now, or its name, and a click that types
    // it, which is what both double-tap and TalkBack's lift-to-type perform. The glyph and the
    // legends under it are not read on their own.
    val description = when (val spoken = spokenKey(key, label, iconShown = icon != null, obscured = obscured, editorAction = editorAction)) {
        is Spoken.Named -> stringResource(spoken.id)
        is Spoken.Text -> spoken.text
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {
                contentDescription = description
                if (stateDescription != null) this.stateDescription = stateDescription
                role = Role.Button
                onClick { currentCallbacks.onTap(key); true }
                if (customActions.isNotEmpty()) this.customActions = customActions
            }
            .onGloballyPositioned { bounds = it.boundsInRoot(); onBounds?.invoke(key, bounds) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (!pressed) 1.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.keyShadow,
                spotColor = colors.keyShadow,
            )
            .clip(shape)
            .background(if (pressed) colors.pressedKey else visual.background)
            .then(if (visual.ring != null) Modifier.border(2.dp, visual.ring, shape) else Modifier)
            .keyGestures(key.id, longPressMs, listener),
    ) {
        if (!showLabel) return@Box
        // Every key centers its main glyph in the key, so all letters in a row share the exact
        // same vertical center and baseline.
        //
        // On keys with alts, compact legends sit in the top corners (the shifted symbol top-start,
        // the Fn meaning top-end) above the centered glyph. On the 60% board (legendBand = true),
        // plain keys use the same glyph size as keys with alts so the entire row is uniform.
        val drawsLegendLine = topLegend != null || legend != null
        val sizedForLegendLine = legendBand || drawsLegendLine

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val arrow = ArrowDirection.fromString(label)
            if (icon != null) {
                Icon(
                    painter = painterResource(icon.drawable()),
                    contentDescription = key.label,
                    tint = visual.foreground,
                    modifier = Modifier.height(Dimens.iconSize(height, sizedForLegendLine)),
                )
            } else if (arrow != null) {
                ArrowSymbol(
                    direction = arrow,
                    color = visual.foreground,
                    size = Dimens.iconSize(height, sizedForLegendLine),
                )
            } else {
                val word = key.style != KeyStyle.LETTER || label.length > 1
                val size = when {
                    word -> Dimens.wordSize(height, labelSize, sizedForLegendLine)
                    sizedForLegendLine -> Dimens.glyphSize(height)
                    else -> Dimens.plainGlyphSize(height)
                }
                Text(
                    text = label,
                    color = visual.foreground,
                    fontSize = size,
                    lineHeight = size * Dimens.glyphLineHeightRatio,
                    fontWeight = if (word) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    // A word wider than its key (Home, PgUp, Shift on a one-unit key) shrinks to
                    // fit rather than wrapping, which with one line would clip its last letters.
                    // Only words: a single glyph always fits, and sizing it would cost layouts
                    // on every key each time Shift or a layer changes.
                    autoSize = if (word) {
                        TextAutoSize.StepBased(
                            minFontSize = Dimens.minWordSize,
                            maxFontSize = size,
                            stepSize = 0.5.sp,
                        )
                    } else {
                        null
                    },
                    modifier = if (word) Modifier.padding(horizontal = 3.dp) else Modifier,
                )
            }
        }
        if (drawsLegendLine) {
            val legendSize = Dimens.legendTextSize(height)
            if (topLegend != null) {
                val arrow = ArrowDirection.fromString(topLegend)
                if (arrow != null) {
                    ArrowSymbol(
                        direction = arrow,
                        color = topLegendColor ?: colors.subtle,
                        size = legendSize.value.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 5.dp, top = 2.dp),
                    )
                } else {
                    Text(
                        text = topLegend,
                        color = topLegendColor ?: colors.subtle,
                        fontSize = legendSize,
                        lineHeight = legendSize * Dimens.legendLineHeightRatio,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 5.dp, top = 2.dp),
                    )
                }
            }
            if (legend != null) {
                val arrow = ArrowDirection.fromString(legend)
                if (arrow != null) {
                    ArrowSymbol(
                        direction = arrow,
                        color = legendColor ?: colors.legend,
                        size = legendSize.value.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 5.dp, top = 2.dp),
                    )
                } else {
                    Text(
                        text = legend,
                        color = legendColor ?: colors.legend,
                        fontSize = legendSize,
                        lineHeight = legendSize * Dimens.legendLineHeightRatio,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 5.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

private const val REPEAT_DELAY_MS = 350L
private const val REPEAT_INTERVAL_MS = 50L
