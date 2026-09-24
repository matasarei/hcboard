package net.matasar.keyboard.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.autofill.AutofillActions
import net.matasar.keyboard.R
import net.matasar.keyboard.ime.KeyboardController
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.input.glide.GlideKey
import net.matasar.keyboard.input.glide.GlidePoint
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.KeyIcon
import net.matasar.keyboard.layout.KeyStyle
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.macro.Macro
import net.matasar.keyboard.settings.SplitMode
import net.matasar.keyboard.ui.theme.KeyboardColors
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** The feel settings: haptics, press previews, sizes, glide. Step 6 reads them from prefs. */
@Immutable
data class KeyboardFeel(
    val haptics: Boolean = true,
    val previews: Boolean = true,
    val heightScale: Float = 1f,
    val widthScale: Float = 1f,
    val glide: Boolean = true,
    val glideTrail: Boolean = true,
    /** Whether the wide board splits into halves. */
    val split: SplitMode = SplitMode.AUTO,
)

/**
 * The whole keyboard: a transparent overhang for popups, the toolbar, the current layer's rows,
 * and the popup layer drawn over all of it.
 */
@Composable
fun KeyboardScreen(
    controller: KeyboardController,
    actions: ToolbarActions,
    autofill: AutofillActions,
    feel: KeyboardFeel = KeyboardFeel(),
    /** Room to leave under the keys for the system's bottom bar; the service measures it. */
    bottomInset: Dp = 0.dp,
    /** Room to leave on the sides for display cutouts (camera punch holes in landscape). */
    sideInset: Dp = 0.dp,
    /**
     * A hinge running down the window, its left and right edge in px from the keyboard's left
     * edge, or null. No key is drawn on it: the wide board splits around it.
     */
    hinge: ClosedFloatingPointRange<Float>? = null,
    /** What the macro sheet lists. */
    macros: List<Macro> = emptyList(),
) {
    val colors = LocalKeyboardColors.current
    val popups = remember { PopupState() }
    val trailColor = colors.armedRing
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { popups.rootWidthPx = it.width.toFloat() }
            // The trail is drawn over the keys without taking part in layout: a sized canvas here
            // would grow the input view, the IME window would re-lay itself out mid-gesture, and
            // every later pointer position would arrive offset by the old window top.
            .drawWithContent {
                drawContent()
                drawGlideTrail(popups.trail, trailColor)
            },
    ) {
        val wide = maxWidth >= Dimens.wideBreakpoint
        val extraSidePadding = (maxWidth * ((1f - feel.widthScale.coerceIn(0.7f, 1f)) / 2f)).coerceAtLeast(0.dp)
        val effectiveSidePadding = maxOf(extraSidePadding, sideInset)
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(PopupMetrics.overhang))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(bottom = bottomInset),
            ) {
                val chipText = if (controller.trackpad) "Move cursor" else controller.modifiers.chipText()
                Toolbar(
                    modifier = Modifier
                        .background(colors.toolbar)
                        .padding(horizontal = effectiveSidePadding),
                    chipText = chipText,
                    actions = actions,
                    sheetOpen = controller.managerSheetOpen,
                    macroSheetOpen = controller.macroSheetOpen,
                    settingsSheetOpen = controller.settingsSheetOpen,
                    haptics = feel.haptics,
                    voice = controller.showVoiceKey,
                    collapsible = controller.toolbarFolds(wide),
                    expanded = controller.toolbarExpanded,
                    onExpand = controller::expandToolbar,
                    // The password manager's chips win the toolbar; word candidates take it next.
                    center = if (controller.suggestions.isNotEmpty()) ({ SuggestionStrip(controller.suggestions) }) else null,
                    candidates = controller.candidates.takeIf { controller.suggestions.isEmpty() && chipText == null && !controller.candidatesCollapsed },
                    onPickCandidate = controller::pickCandidate,
                    onCollapseCandidates = controller::collapseCandidates,
                )
                LayerGrid(controller, feel, popups, effectiveSidePadding, hinge)
            }
        }
        PopupLayer(popups)
        if (controller.languageSheetOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = PopupMetrics.overhang + Dimens.toolbarHeight),
            ) {
                LanguageSheet(
                    languages = controller.enabledLanguageList,
                    current = controller.language,
                    onPick = controller::switchLanguage,
                    onDismiss = { controller.languageSheetOpen = false },
                    sidePadding = effectiveSidePadding,
                )
            }
        }
        if (controller.managerSheetOpen) {
            // Covers the keys, not the overhang: the sheet starts under the toolbar.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = PopupMetrics.overhang + Dimens.toolbarHeight),
            ) {
                ManagerSheet(autofill, onDismiss = { controller.managerSheetOpen = false }, sidePadding = effectiveSidePadding)
            }
        }
        if (controller.macroSheetOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = PopupMetrics.overhang + Dimens.toolbarHeight),
            ) {
                MacroSheet(
                    macros = macros,
                    running = controller.runningMacro,
                    onRun = controller::runMacro,
                    onStop = controller::stopMacro,
                    onEdit = actions::openMacros,
                    onDismiss = { controller.macroSheetOpen = false },
                    sidePadding = effectiveSidePadding,
                )
            }
        }
        if (controller.settingsSheetOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = PopupMetrics.overhang + Dimens.toolbarHeight),
            ) {
                SettingsSheet(
                    developerMode = controller.developerMode,
                    onToggleDeveloperMode = actions::toggleDeveloperMode,
                    suggestInAppOffered = controller.suggestInAppOffered,
                    suggestInApp = controller.suggestInApp,
                    onToggleSuggestInApp = actions::toggleSuggestInApp,
                    toolbarAlwaysShown = controller.toolbarAlwaysShown,
                    onToggleToolbarAlwaysShown = actions::toggleToolbarAlwaysShown,
                    numberRow = controller.numberRow,
                    onToggleNumberRow = actions::toggleNumberRow,
                    onOpenSettings = actions::openSettings,
                    onDismiss = { controller.settingsSheetOpen = false },
                    sidePadding = effectiveSidePadding,
                    wide = wide,
                )
            }
        }
    }
}

@Composable
private fun LayerGrid(
    controller: KeyboardController,
    feel: KeyboardFeel,
    popups: PopupState,
    extraSidePadding: Dp = 0.dp,
    hinge: ClosedFloatingPointRange<Float>? = null,
) {
    val colors = LocalKeyboardColors.current
    val density = LocalDensity.current
    val callbacks = remember(controller, popups, feel, density) {
        KeyScreenCallbacks(controller, popups, feel, trackpadStepPx = with(density) { 16.dp.toPx() })
    }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().onGloballyPositioned { gridOrigin = it.positionInRoot() }) {
        // 600 dp and wider (a Fold's inner display, a tablet) gets the 60% board.
        val wide = maxWidth >= Dimens.wideBreakpoint
        // Told, not passed: re-creating the callbacks would re-key every key's pointerInput and
        // drop the finger already on one.
        SideEffect { callbacks.wideBoard = wide }
        val layout = if (wide) controller.wideLayout else controller.phoneLayout
        // Letter-key bounds in root coordinates, kept for the glide detector and the classifier.
        // Rebuilt per layout: a language switch, or unfolding onto the 60% board, must not leave
        // the previous board's keys behind.
        val letterBounds = remember(layout) { mutableStateMapOf<Char, Rect>() }
        val longPressMs = LocalViewConfiguration.current.longPressTimeoutMillis
        // Keyed on the bounds map too: a new layout brings a new map, and the detector must restart
        // with it rather than keep classifying against the previous alphabet.
        val glideListener = remember(controller, popups, feel, letterBounds) {
            object : GlideListener {
                override fun onGlideStart() {
                    popups.preview = null
                }

                override fun onGlideMove(path: List<GlidePoint>) {
                    if (feel.glideTrail) popups.trail = path.map { Offset(it.x, it.y) }
                }

                override fun onGlideEnd(path: List<GlidePoint>) {
                    popups.trail = emptyList()
                    val keys = letterBounds.map { (char, rect) -> GlideKey(char, rect.center.x, rect.center.y, rect.width, rect.height) }
                    controller.onGlideEnd(path, keys)
                }

                override fun onGlideCancel() {
                    popups.trail = emptyList()
                }
            }
        }
        val layer = layout.layers[controller.layer] ?: layout.layers.values.first()
        // The screen's size, not the window's: the keyboard's window is only as tall as the
        // keyboard, so sizing the keys from it would size them from themselves.
        val configuration = LocalConfiguration.current
        // The halves replace the whole board where a hinge or two thumbs ask for them.
        @SuppressLint("ConfigurationScreenWidthHeight")
        val split = layout.split.takeIf {
            controller.layer == LayerId.LETTERS &&
                shouldSplit(feel.split, wide, hingeSeparating = hinge != null, phoneLandscape = isPhoneLandscape(configuration.screenWidthDp, configuration.screenHeightDp))
        }
        val sidePadding = (if (wide) Dimens.wideSidePadding else Dimens.sidePadding) + extraSidePadding
        // The gaps follow the height setting too: keys shrunk to 80% under full-size gaps read as
        // small keys floating in space.
        val rowGap = (if (wide) Dimens.wideRowGap else Dimens.rowGap) * feel.heightScale
        // Short windows (a phone in landscape) get shorter keys so the whole board stays on screen.
        val rows = layer.rows.size + (if (controller.developerMode && !wide) 1 else 0)
        @SuppressLint("ConfigurationScreenWidthHeight")
        val budget = configuration.screenHeightDp.dp * Dimens.maxHeightFraction -
            Dimens.toolbarHeight - Dimens.topPadding - Dimens.bottomPadding - rowGap * (rows - 1)
        val keyHeight = minOf((if (wide) Dimens.wideKeyHeight else Dimens.keyHeight) * feel.heightScale, budget / rows)
        val unitWidth = if (split == null) {
            (maxWidth - sidePadding * 2 - Dimens.keyGap * (layer.units.toInt() - 1)) / layer.units
        } else {
            with(density) {
                splitUnit(
                    width = maxWidth.toPx(),
                    sidePadding = sidePadding.toPx(),
                    gap = Dimens.keyGap.toPx(),
                    leftUnits = split.left.units,
                    rightUnits = split.right.units,
                    hinge = hinge,
                    hingeMargin = Dimens.hingeMargin.toPx(),
                    gapUnits = Dimens.splitGapUnits,
                ).toDp()
            }
        }
        // Glide lives on the letters layer of either board; the symbols and code pages tap only.
        // The detector is always attached and asks this at each touch: swapping the modifier in
        // and out would cancel the gestures under it, which is how a trackpad started by a long
        // press on Space (glide off while it runs) used to cancel itself.
        val glide = rememberUpdatedState(feel.glide && controller.layer == LayerId.LETTERS && controller.glideAvailable)
        val unitWidthPx = with(density) { unitWidth.toPx() }
        // A screen reader takes the finger, so what a long press offers goes on the key instead.
        val exploring = rememberTouchExploration()
        val title = if (controller.layer == LayerId.LETTERS) {
            stringResource(layerTitle(controller.layer), controller.language.nativeName)
        } else {
            stringResource(layerTitle(controller.layer))
        }
        // A password is spoken as dots unless it can only be heard in the user's own ears.
        val obscured = controller.passwordField && !rememberPrivateAudio()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // TalkBack announces the page when it changes: a layer switch or a new language.
                .semantics { paneTitle = title }
                .glideDetector(
                    letterBounds = { letterBounds },
                    gridOriginInRoot = { gridOrigin },
                    keyWidthPx = { unitWidthPx },
                    longPressMs = longPressMs,
                    listener = glideListener,
                    enabled = { glide.value },
                )
                .padding(top = Dimens.topPadding, bottom = Dimens.bottomPadding),
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            if (controller.developerMode && !wide) {
                ModifierStrip(controller, feel, callbacks, unitWidth, keyHeight = keyHeight, sidePadding = sidePadding)
            }
            val keyButton: @Composable (Key) -> Unit = { key ->
                KeyButton(
                    key = key,
                    label = controller.displayLabel(key),
                    icon = iconFor(key, controller),
                    visual = visualFor(key, controller, colors),
                    height = keyHeight,
                    callbacks = callbacks,
                    haptics = feel.haptics,
                    showLabel = !controller.trackpad,
                    legendBand = wide,
                    // Only symbols are printed as Fn legends (see printedFnLegend); the live one
                    // tints. The shifted symbol tints while Shift is what makes the glyph what it
                    // is: Shift with Fn does nothing to a digit, so `!` stays subtle then.
                    legend = printedFnLegend(key),
                    legendColor = if (printedFnLegend(key) != null && controller.fnLive(key)) colors.armedRing else null,
                    topLegendColor = if (controller.shiftLive(key)) colors.armedRing else null,
                    onBounds = if (key.action is KeyAction.Letter) ({ k, rect -> letterBounds[(k.action as KeyAction.Letter).lower[0]] = rect }) else null,
                    repeats = controller.repeats(key),
                    stateDescription = keyState(key, controller),
                    customActions = if (exploring) keyCustomActions(key, controller) else emptyList(),
                    obscured = obscured,
                    editorAction = controller.editorActionId,
                    globeTarget = if (key.action == KeyAction.SwitchLanguage && controller.withGlobe) controller.globeTarget().nativeName else null,
                )
            }
            if (split == null) {
                for (row in layer.rows) {
                    KeyRow(row = row, unitWidth = unitWidth, gap = Dimens.keyGap, modifier = Modifier.padding(horizontal = sidePadding), content = keyButton)
                }
            } else {
                val gap = Dimens.keyGap
                val leftWidth = unitWidth * split.left.units + gap * (split.left.units - 1f)
                val rightWidth = unitWidth * split.right.units + gap * (split.right.units - 1f)
                // Each half against its outer edge; the room between them is the thumbs', or the hinge's.
                for ((leftRow, rightRow) in split.left.rows.zip(split.right.rows)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        KeyRow(row = leftRow, unitWidth = unitWidth, gap = gap, modifier = Modifier.width(leftWidth), content = keyButton)
                        KeyRow(row = rightRow, unitWidth = unitWidth, gap = gap, modifier = Modifier.width(rightWidth), content = keyButton)
                    }
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

    /** Whether the 60% board is showing: only there is a key's shifted symbol printed on it. */
    var wideBoard = false

    override fun onPressStart(key: Key, bounds: Rect) {
        (key.action as? KeyAction.Modifier)?.let { controller.onModifierPressStart(it.modifier) }
        if (feel.previews && !controller.passwordField && key.showsPreview() && !controller.repeats(key)) {
            popups.preview = PressPreview(bounds, controller.displayLabel(key))
        }
    }

    override fun onPressEnd(key: Key) {
        (key.action as? KeyAction.Modifier)?.let { controller.onModifierPressEnd(it.modifier) }
        popups.preview = null
    }

    override fun onTap(key: Key) = controller.onKey(key)

    override fun onLongPress(key: Key, bounds: Rect): LongPressResult {
        val accents = controller.accentsFor(key)
        return when {
            accents.isNotEmpty() -> {
                popups.preview = null
                popups.accents = AccentChoice(bounds, accents, selected = 0)
                LongPressResult.STEER
            }
            key.action == KeyAction.Space -> {
                lastX = bounds.center.x
                controller.startTrackpad(trackpadStepPx)
                LongPressResult.STEER
            }
            key.action == KeyAction.Shift || key.action is KeyAction.Modifier || key.action == KeyAction.SwitchLanguage -> {
                controller.onKeyLongPress(key)
                LongPressResult.HANDLED
            }
            else -> {
                // A key with no accents types its shifted symbol instead, which the 60% board
                // prints on the key. The bubble was raised on the tap meaning, so it says what
                // the hold produces for as long as the finger is down, and HANDLED stops the
                // release from typing the plain character as well.
                val shifted = if (wideBoard) controller.longPressText(key) else null
                if (shifted == null) {
                    LongPressResult.NONE
                } else {
                    if (popups.preview != null) popups.preview = PressPreview(bounds, shifted)
                    controller.onKeyLongPressShift(key)
                    LongPressResult.HANDLED
                }
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

    override fun onLongPressCancel(key: Key) {
        popups.accents = null
        if (controller.trackpad) controller.endTrackpad()
    }

    override fun onRepeat(key: Key) = controller.onKeyRepeat(key)

    private fun cellPx() = PopupMetrics.accentCell.value * popups.density
    private fun paddingPx() = PopupMetrics.accentPadding.value * popups.density
}

/**
 * What a screen reader can do with [key] besides typing it: the accents a long press would
 * offer, the cursor moves the trackpad makes, the language picker the globe holds.
 */
@Composable
internal fun keyCustomActions(key: Key, controller: KeyboardController): List<CustomAccessibilityAction> {
    val left = stringResource(R.string.a11y_action_cursor_left)
    val right = stringResource(R.string.a11y_action_cursor_right)
    val leftWord = stringResource(R.string.a11y_action_cursor_left_word)
    val rightWord = stringResource(R.string.a11y_action_cursor_right_word)
    val language = stringResource(R.string.a11y_action_choose_language)
    return keyActions(key, controller.accentsFor(key), controller.withGlobe).map { action ->
        when (action) {
            is KeyAccessibilityAction.Accent ->
                CustomAccessibilityAction(action.text) { controller.commitAccent(action.text); true }
            is KeyAccessibilityAction.MoveCursor -> {
                val label = when {
                    action.byWord && action.steps < 0 -> leftWord
                    action.byWord -> rightWord
                    action.steps < 0 -> left
                    else -> right
                }
                CustomAccessibilityAction(label) { controller.moveCursor(action.steps, action.byWord); true }
            }
            KeyAccessibilityAction.ChooseLanguage ->
                CustomAccessibilityAction(language) { controller.onKeyLongPress(key); true }
        }
    }
}

/** A latching key's state as TalkBack reads it after the key's name; null for the rest. */
@Composable
internal fun keyState(key: Key, controller: KeyboardController): String? =
    spokenState(key, controller.shift.state, controller.modifiers::state, controller.modifiers.held)?.let { stringResource(it) }

private fun Key.showsPreview(): Boolean =
    style == KeyStyle.LETTER && icon == null && label.length == 1 && action != KeyAction.Space

private fun iconFor(key: Key, controller: KeyboardController): KeyIcon? = when {
    // Fn's meaning is drawn as the glyph, so the icon it replaces goes (backspace reads Del).
    !controller.showsIcon(key) -> null
    key.action == KeyAction.Shift && key.icon != null && (controller.shift.active || controller.autoCapital) -> KeyIcon.SHIFT_FILLED
    key.action == KeyAction.Enter -> controller.enterIcon
    else -> key.icon
}

/**
 * The Fn legend a key prints: only a symbol Fn types, both halves of it (Esc `` `~ ``, х `[{`,
 * б `,<`), because a symbol a long alphabet pushed off its key has nowhere else to be seen.
 * A named meaning (F1, an arrow, Home, Del) is not printed: holding Fn shows it as the glyph,
 * and printed it only crowds the key.
 */
internal fun printedFnLegend(key: Key): String? = key.fnLegend.takeIf { key.fnAction is KeyAction.Text }

/**
 * Background and foreground for a key, including the shift key's armed and locked looks. An
 * automatic capital makes Shift (not Caps Lock) look armed: the next letter is a capital, as
 * after a tap on Shift.
 */
internal fun visualFor(key: Key, controller: KeyboardController, colors: KeyboardColors): KeyVisual {
    if (key.action is KeyAction.Modifier) return modifierVisual(key, controller, colors)
    if (key.action == KeyAction.Shift || key.action == KeyAction.CapsLock) {
        return when (controller.shift.state) {
            LatchState.IDLE ->
                if (controller.autoCapital && key.action == KeyAction.Shift) KeyVisual(colors.armed, colors.onArmed, ring = colors.armedRing)
                else KeyVisual(colors.functionKey, colors.onFunctionKey)
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
