package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matasar.keyboard.ime.KeyboardController
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.KeyIcon
import net.matasar.keyboard.layout.KeyStyle
import net.matasar.keyboard.ui.theme.KeyboardColors
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** The whole keyboard: toolbar on top, the current layer's rows below. */
@Composable
fun KeyboardScreen(controller: KeyboardController) {
    val colors = LocalKeyboardColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(Dimens.toolbarHeight)) // toolbar: step 6
        LayerGrid(controller)
    }
}

@Composable
private fun LayerGrid(controller: KeyboardController) {
    val layer = controller.layout.layer(controller.layer)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val unitWidth = (maxWidth - Dimens.sidePadding * 2 - Dimens.keyGap * (layer.units.toInt() - 1)) / layer.units
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Dimens.sidePadding,
                    end = Dimens.sidePadding,
                    top = Dimens.topPadding,
                    bottom = Dimens.bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.rowGap),
        ) {
            for (row in layer.rows) {
                KeyRow(row = row, unitWidth = unitWidth, gap = Dimens.keyGap) { key ->
                    KeyButton(
                        key = key,
                        label = controller.displayLabel(key),
                        icon = iconFor(key, controller),
                        visual = visualFor(key, controller, LocalKeyboardColors.current),
                        height = Dimens.keyHeight,
                        onTap = { controller.onKey(key) },
                        onLongPress = if (key.action == KeyAction.Shift) ({ controller.onKeyLongPress(key) }) else null,
                        onRepeat = if (key.repeats) ({ controller.onKeyRepeat(key) }) else null,
                    )
                }
            }
        }
    }
}

private fun iconFor(key: Key, controller: KeyboardController): KeyIcon? =
    if (key.action == KeyAction.Shift && controller.shift.active) KeyIcon.SHIFT_FILLED else key.icon

/** Background and foreground for a key, including the shift key's armed and locked looks. */
internal fun visualFor(key: Key, controller: KeyboardController, colors: KeyboardColors): KeyVisual {
    if (key.action == KeyAction.Shift) {
        return when (controller.shift.state) {
            LatchState.IDLE -> KeyVisual(colors.functionKey, colors.onFunctionKey)
            LatchState.ARMED -> KeyVisual(colors.armed, colors.onArmed, ring = colors.armedRing)
            LatchState.LOCKED -> KeyVisual(colors.locked, colors.onLocked)
        }
    }
    return when (key.style) {
        KeyStyle.LETTER, KeyStyle.SPACE -> KeyVisual(colors.key, colors.onKey)
        KeyStyle.FUNCTION, KeyStyle.MODIFIER -> KeyVisual(colors.functionKey, colors.onFunctionKey)
        KeyStyle.ACTION -> KeyVisual(colors.action, colors.onAction)
    }
}
