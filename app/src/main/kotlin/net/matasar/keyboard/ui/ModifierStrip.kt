package net.matasar.keyboard.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import net.matasar.keyboard.ime.KeyboardController
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.layout.DeveloperStrip
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.ModifierKey
import net.matasar.keyboard.ui.theme.KeyboardColors
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** The developer-mode strip: one key row whose modifier keys paint their latch state. */
@Composable
fun ModifierStrip(
    controller: KeyboardController,
    feel: KeyboardFeel,
    callbacks: KeyCallbacks,
    unitWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKeyboardColors.current
    val fnActive = controller.modifiers.isActive(ModifierKey.FN)
    KeyRow(row = DeveloperStrip, unitWidth = unitWidth, gap = Dimens.keyGap, modifier = modifier.padding(horizontal = Dimens.sidePadding)) { key ->
        KeyButton(
            key = key,
            label = key.label,
            visual = modifierVisual(key, controller, colors),
            height = Dimens.keyHeight,
            callbacks = callbacks,
            haptics = feel.haptics,
            keyBorders = feel.keyBorders,
            showLabel = !controller.trackpad,
            legendColor = if (fnActive) colors.armedRing else null,
        )
    }
}

/** A modifier key's colours from its latch: idle, armed (ringed), locked; held looks armed. */
internal fun modifierVisual(key: Key, controller: KeyboardController, colors: KeyboardColors): KeyVisual {
    val action = key.action
    if (action is KeyAction.Modifier) {
        val state = controller.modifiers.state(action.modifier)
        val held = action.modifier in controller.modifiers.held
        return when {
            state == LatchState.LOCKED -> KeyVisual(colors.locked, colors.onLocked)
            state == LatchState.ARMED || held -> KeyVisual(colors.armed, colors.onArmed, ring = colors.armedRing)
            else -> KeyVisual(colors.functionKey, colors.onFunctionKey)
        }
    }
    return KeyVisual(colors.functionKey, colors.onFunctionKey)
}
