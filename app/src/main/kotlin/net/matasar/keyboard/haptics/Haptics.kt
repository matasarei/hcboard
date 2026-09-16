package net.matasar.keyboard.haptics

import android.view.HapticFeedbackConstants
import android.view.View

/** The light tick on key down. Nothing on release, like the iPhone keyboard. */
fun View.keyDownTick() {
    performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_PRESS,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
    )
}
