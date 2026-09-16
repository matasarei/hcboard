package net.matasar.keyboard.ui

import androidx.annotation.DrawableRes
import net.matasar.keyboard.R
import net.matasar.keyboard.layout.KeyIcon

/** Maps the layout's icon names to the vector drawables in res/drawable. */
@DrawableRes
fun KeyIcon.drawable(): Int = when (this) {
    KeyIcon.SHIFT -> R.drawable.ic_shift
    KeyIcon.SHIFT_FILLED -> R.drawable.ic_shift_filled
    KeyIcon.BACKSPACE -> R.drawable.ic_backspace
    KeyIcon.ENTER -> R.drawable.ic_enter
    KeyIcon.ARROW_LEFT -> R.drawable.ic_arrow_left
    KeyIcon.ARROW_RIGHT -> R.drawable.ic_arrow_right
    KeyIcon.ARROW_UP -> R.drawable.ic_arrow_up
    KeyIcon.ARROW_DOWN -> R.drawable.ic_arrow_down
    KeyIcon.UNDO -> R.drawable.ic_undo
    KeyIcon.REDO -> R.drawable.ic_redo
    KeyIcon.KEYBOARD_HIDE -> R.drawable.ic_keyboard_hide
    KeyIcon.GLOBE -> R.drawable.ic_globe
    KeyIcon.SEARCH -> R.drawable.ic_search
    KeyIcon.SEND -> R.drawable.ic_send
    KeyIcon.CHECK -> R.drawable.ic_check
}
