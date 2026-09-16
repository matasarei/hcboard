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
    keyHeight: Dp = Dimens.keyHeight,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKeyboardColors.current
    val fnActive = controller.modifiers.isActive(ModifierKey.FN)
    KeyRow(row = DeveloperStrip, unitWidth = unitWidth, gap = Dimens.keyGap, modifier = modifier.padding(horizontal = Dimens.sidePadding)) { key ->
        // Phone-width strip keys are 35 dp: no room for an icon plus a legend. While Fn is
        // active the arrow keys show their Fn meaning as the label instead of the icon.
        val fnWord = key.fnLegend?.takeIf { fnActive }
        KeyButton(
            key = key,
            label = fnWord ?: key.label,
            icon = if (fnWord != null) null else key.icon,
            legend = null,
            labelSize = Dimens.stripLabelSize,
            visual = if (fnWord != null) KeyVisual(colors.armed, colors.onArmed) else modifierVisual(key, controller, colors),
            height = keyHeight,
            callbacks = callbacks,
            haptics = feel.haptics,
            keyBorders = feel.keyBorders,
            showLabel = !controller.trackpad,
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
