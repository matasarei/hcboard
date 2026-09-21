package net.matasar.keyboard.ui

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.matasar.keyboard.R
import net.matasar.keyboard.haptics.keyDownTick
import net.matasar.keyboard.nlp.WordCandidates
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** What the toolbar buttons do; the service implements it. */
interface ToolbarActions {
    fun toggleManagerSheet()
    fun toggleMacroSheet()
    fun toggleSettingsSheet()
    fun openMacros()
    fun toggleDeveloperMode()
    fun pasteClipboard()
    fun startVoiceInput()
    fun openSettings()
    fun hideKeyboard()
}

/**
 * The 44 dp strip above the keys: settings, passwords and macros on the left, the chip or the
 * autofill suggestions in the middle, and the mic (when there is a voice keyboard to hand off to),
 * paste and hide on the right. While a word is being typed its [candidates] take the whole strip
 * but the chevron that brings the buttons back, so the words get the width. Where the strip is
 * [collapsible] (the phone board) the left buttons sit behind a chevron of their own until
 * [expanded], leaving the middle their room.
 */
@Composable
fun Toolbar(
    chipText: String?,
    actions: ToolbarActions,
    modifier: Modifier = Modifier,
    sheetOpen: Boolean = false,
    macroSheetOpen: Boolean = false,
    settingsSheetOpen: Boolean = false,
    /**
     * Whether the gear opens its sheet with the developer-mode switch. The 60% board carries its
     * modifiers itself, so there the sheet would hold Settings alone and the gear opens it directly.
     */
    settingsMenu: Boolean = true,
    center: (@Composable () -> Unit)? = null,
    candidates: WordCandidates? = null,
    onPickCandidate: (String) -> Unit = {},
    onCollapseCandidates: () -> Unit = {},
    haptics: Boolean = true,
    /** Whether the mic shows, first of the right-hand buttons. */
    voice: Boolean = false,
    /** Whether the left buttons may fold behind a chevron: on the phone board, never on the wide one. */
    collapsible: Boolean = false,
    /** Whether they are unfolded; an open sheet unfolds them too, so its button stays in sight. */
    expanded: Boolean = true,
    onExpand: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.toolbarHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (candidates != null) {
            ToolbarButton(R.drawable.ic_arrow_left, "Close suggestions", haptics = haptics) { onCollapseCandidates() }
            CandidateStrip(candidates, onPickCandidate, modifier = Modifier.weight(1f))
        } else {
            val showButtons = !collapsible || expanded || sheetOpen || macroSheetOpen || settingsSheetOpen
            if (showButtons) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToolbarButton(R.drawable.ic_settings, "Settings", active = settingsSheetOpen, haptics = haptics) {
                        if (settingsMenu) actions.toggleSettingsSheet() else actions.openSettings()
                    }
                    ToolbarButton(R.drawable.ic_key, "Password manager", active = sheetOpen, haptics = haptics) { actions.toggleManagerSheet() }
                    ToolbarButton(R.drawable.ic_macro, "Macros", active = macroSheetOpen, haptics = haptics) { actions.toggleMacroSheet() }
                }
            } else {
                ToolbarButton(R.drawable.ic_arrow_right, "Show toolbar buttons", haptics = haptics) { onExpand() }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when {
                    center != null -> center()
                    chipText != null -> ModifierChip(chipText)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (voice) {
                    ToolbarButton(R.drawable.ic_mic, "Voice input", haptics = haptics) { actions.startVoiceInput() }
                }
                ToolbarButton(R.drawable.ic_clipboard, "Paste", haptics = haptics) { actions.pasteClipboard() }
                ToolbarButton(R.drawable.ic_keyboard_hide, "Hide keyboard", haptics = haptics) { actions.hideKeyboard() }
            }
        }
    }
}

@Composable
fun ToolbarButton(
    icon: Int,
    description: String,
    active: Boolean = false,
    haptics: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalKeyboardColors.current
    val view = LocalView.current
    val currentHaptics by rememberUpdatedState(haptics)
    val currentOnClick by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "toolbar_button_press",
    )

    val background = when {
        pressed -> colors.pressedKey
        active -> colors.chip
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .width(40.dp)
            .height(32.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val start = SystemClock.uptimeMillis()
                        pressed = true
                        if (currentHaptics) view.keyDownTick()
                        try {
                            val released = tryAwaitRelease()
                            if (released) {
                                currentOnClick()
                            }
                            val elapsed = SystemClock.uptimeMillis() - start
                            if (elapsed < 80L) {
                                delay(80L - elapsed)
                            }
                        } finally {
                            pressed = false
                        }
                    }
                )
            }
            .semantics {
                this.role = Role.Button
                this.contentDescription = description
                onClick {
                    currentOnClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (active && !pressed) colors.onChip else colors.icon,
            modifier = Modifier.height(22.dp),
        )
    }
}
