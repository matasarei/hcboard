package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.ime.KeyboardController
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.KeyIcon
import net.matasar.keyboard.layout.KeyStyle
import net.matasar.keyboard.ui.theme.KeyboardColors
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** The feel settings: haptics, press previews, key borders. Step 6 reads them from prefs. */
@Immutable
data class KeyboardFeel(
    val haptics: Boolean = true,
    val previews: Boolean = true,
    val keyBorders: Boolean = true,
    val heightScale: Float = 1f,
)

/**
 * The whole keyboard: a transparent overhang for popups, the toolbar, the current layer's rows,
 * and the popup layer drawn over all of it.
 */
@Composable
fun KeyboardScreen(controller: KeyboardController, actions: ToolbarActions, feel: KeyboardFeel = KeyboardFeel()) {
    val colors = LocalKeyboardColors.current
    val popups = remember { PopupState() }
    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { popups.rootWidthPx = it.width.toFloat() }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(PopupMetrics.overhang))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .navigationBarsPadding(),
            ) {
                Toolbar(
                    developerMode = controller.developerMode,
                    chipText = if (controller.trackpad) "Move cursor" else controller.modifiers.chipText(),
                    actions = actions,
                )
                LayerGrid(controller, feel, popups)
            }
        }
        PopupLayer(popups)
    }
}

@Composable
private fun LayerGrid(controller: KeyboardController, feel: KeyboardFeel, popups: PopupState) {
    val layer = controller.layout.layer(controller.layer)
    val colors = LocalKeyboardColors.current
    val density = LocalDensity.current
    val callbacks = remember(controller, popups, feel, density) {
        KeyScreenCallbacks(controller, popups, feel, trackpadStepPx = with(density) { 16.dp.toPx() })
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val unitWidth = (maxWidth - Dimens.sidePadding * 2 - Dimens.keyGap * (layer.units.toInt() - 1)) / layer.units
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.topPadding, bottom = Dimens.bottomPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.rowGap),
        ) {
            if (controller.developerMode) {
                ModifierStrip(controller, feel, callbacks, unitWidth, keyHeight = Dimens.keyHeight * feel.heightScale)
            }
            for (row in layer.rows) {
                KeyRow(row = row, unitWidth = unitWidth, gap = Dimens.keyGap, modifier = Modifier.padding(horizontal = Dimens.sidePadding)) { key ->
                    KeyButton(
                        key = key,
                        label = controller.displayLabel(key),
                        icon = iconFor(key, controller),
                        visual = visualFor(key, controller, colors),
                        height = Dimens.keyHeight * feel.heightScale,
                        callbacks = callbacks,
                        haptics = feel.haptics,
                        keyBorders = feel.keyBorders,
                        showLabel = !controller.trackpad,
                    )
                }
            }
        }
    }
}

/** Routes key gestures to the controller and the popups. */
private class KeyScreenCallbacks(
    private val controller: KeyboardController,
    private val popups: PopupState,
    private val feel: KeyboardFeel,
    private val trackpadStepPx: Float,
) : KeyCallbacks {
    private var lastX = 0f

    override fun onPressStart(key: Key, bounds: Rect) {
        (key.action as? KeyAction.Modifier)?.let { controller.onModifierPressStart(it.modifier) }
        if (feel.previews && key.showsPreview()) {
            popups.preview = PressPreview(bounds, controller.displayLabel(key))
        }
    }

    override fun onPressEnd(key: Key) {
        (key.action as? KeyAction.Modifier)?.let { controller.onModifierPressEnd(it.modifier) }
        popups.preview = null
    }

    override fun onTap(key: Key) = controller.onKey(key)

    override fun onLongPress(key: Key, bounds: Rect): Boolean {
        val accents = controller.accentsFor(key)
        return when {
            accents.isNotEmpty() -> {
                popups.preview = null
                popups.accents = AccentChoice(bounds, accents, selected = 0)
                true
            }
            key.action == KeyAction.Space -> {
                lastX = bounds.center.x
                controller.startTrackpad(trackpadStepPx)
                true
            }
            else -> {
                controller.onKeyLongPress(key)
                false
            }
        }
    }

    override fun onLongPressMove(key: Key, rootPosition: Offset) {
        popups.accents?.let { accents ->
            popups.accents = accents.copy(selected = accents.indexAt(rootPosition.x, cellPx(), paddingPx(), popups.rootWidthPx))
            return
        }
        if (controller.trackpad) {
            controller.trackpadMove(rootPosition.x - lastX)
            lastX = rootPosition.x
        }
    }

    override fun onLongPressRelease(key: Key, rootPosition: Offset) {
        popups.accents?.let { accents ->
            controller.commitAccent(accents.candidates[accents.selected])
            popups.accents = null
            return
        }
        if (controller.trackpad) controller.endTrackpad()
    }

    override fun onRepeat(key: Key) = controller.onKeyRepeat(key)

    private fun cellPx() = PopupMetrics.accentCell.value * popups.density
    private fun paddingPx() = PopupMetrics.accentPadding.value * popups.density
}

private fun Key.showsPreview(): Boolean =
    style == KeyStyle.LETTER && icon == null && label.length == 1 && action != KeyAction.Space

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
