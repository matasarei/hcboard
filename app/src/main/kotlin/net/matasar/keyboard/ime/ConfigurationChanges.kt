package net.matasar.keyboard.ime

import android.content.pm.ActivityInfo

/**
 * The configuration changes that make the input view wrong: the ones that move or resize the
 * display. The view outlives a fold or a rotation, and the window keeps measuring the app's room
 * from it, so the keyboard ends up laid out for a screen that is no longer there. A locale, a
 * font scale or a theme change is not one of these — the composition follows those on its own.
 */
internal const val GEOMETRY_CHANGES: Int =
    ActivityInfo.CONFIG_SCREEN_SIZE or
        ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
        ActivityInfo.CONFIG_SCREEN_LAYOUT or
        ActivityInfo.CONFIG_ORIENTATION or
        ActivityInfo.CONFIG_DENSITY

/** Whether [diff], as `Configuration.diff` reports it, calls for a new input view. */
internal fun rebuildsInputView(diff: Int): Boolean = diff and GEOMETRY_CHANGES != 0
