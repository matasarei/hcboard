package net.matasar.keyboard.haptics

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The light tick on key down. Nothing on release, like the iPhone keyboard.
 *
 * The keyboard's own Haptics switch decides, so the system's touch-feedback setting is ignored;
 * Android honours that flag only up to 12, and from 13 a keyboard follows the system setting.
 */
fun View.keyDownTick() {
    performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_TAP,
        @Suppress("DEPRECATION") HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
    )
}
