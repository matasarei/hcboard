package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyIcon
import net.matasar.keyboard.layout.KeyStyle
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** Colours for one key in its current state. */
data class KeyVisual(val background: Color, val foreground: Color, val ring: Color? = null)

/**
 * One key. Draws the label or icon, tracks its own pressed state, and reports taps, long
 * presses and (for backspace) repeats. The feel — preview, spring, haptics — lands in step 4.
 */
@Composable
fun KeyButton(
    key: Key,
    label: String,
    visual: KeyVisual,
    height: Dp,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    icon: KeyIcon? = key.icon,
    onLongPress: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
) {
    val colors = LocalKeyboardColors.current
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(Dimens.keyRadius)
    val background = if (pressed) colors.pressedKey else visual.background

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shadow(if (pressed) 0.dp else 1.dp, shape, clip = false, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
            .clip(shape)
            .background(background)
            .pointerInput(key.id, onLongPress != null, onRepeat != null) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        var repeats = 0
                        val job: Job? = onRepeat?.let {
                            scope.launch {
                                delay(REPEAT_DELAY_MS)
                                while (isActive) {
                                    repeats++
                                    it()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                        }
                        tryAwaitRelease()
                        job?.cancel()
                        pressed = false
                        if (repeats > 0) repeatedRelease = true
                    },
                    onTap = {
                        if (repeatedRelease) repeatedRelease = false else onTap()
                    },
                    onLongPress = onLongPress?.let { { it() } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon.drawable()),
                contentDescription = key.label,
                tint = visual.foreground,
                modifier = Modifier.height(22.dp),
            )
        } else {
            val small = key.style != KeyStyle.LETTER || label.length > 1
            Text(
                text = label,
                color = visual.foreground,
                fontSize = if (small) Dimens.labelSize else Dimens.letterSize,
                fontWeight = if (small) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

/** Set while a held backspace repeated, so the release is not also counted as a tap. */
private var repeatedRelease = false

private const val REPEAT_DELAY_MS = 350L
private const val REPEAT_INTERVAL_MS = 50L
