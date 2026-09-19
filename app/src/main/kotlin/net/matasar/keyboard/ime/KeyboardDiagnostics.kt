package net.matasar.keyboard.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.matasar.keyboard.BuildConfig

/**
 * What the keyboard last learned about its own window and the field it is typing into, for the
 * settings screen and `dumpsys`: a device that draws the bottom bar over the keys, or an app
 * whose field turns the suggestions off, can be diagnosed from the phone itself. The report
 * opens with the build it came from, so a copied report says which code it describes.
 *
 * The version, sizes, inset values, an input type and a package name only — never anything
 * typed, and never a field's hint or its existing content.
 */
object KeyboardDiagnostics {
    var insets: String by mutableStateOf("The keyboard has not been shown yet.")
    var field: String by mutableStateOf("No field yet.")

    /** The installed build: its version name and the commit CI built it from, or `local build`. */
    fun versionLine(version: String = BuildConfig.VERSION_NAME, commit: String = BuildConfig.COMMIT): String =
        "hcboard $version ($commit)"

    /** The whole report, as the settings screen shows it and copies it. */
    fun report(versionLine: String = versionLine(), field: String = this.field, insets: String = this.insets): String =
        "$versionLine\n\n$field\n\n$insets"
}
