package net.matasar.keyboard.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the keyboard last learned about its own window and the field it is typing into, for the
 * settings screen and `dumpsys`: a device that draws the bottom bar over the keys, or an app
 * whose field turns the suggestions off, can be diagnosed from the phone itself.
 *
 * Sizes, inset values, an input type and a package name only — never anything typed, and never
 * a field's hint or its existing content.
 */
object KeyboardDiagnostics {
    var insets: String by mutableStateOf("The keyboard has not been shown yet.")
    var field: String by mutableStateOf("No field yet.")
}
