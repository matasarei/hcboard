package net.matasar.keyboard.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the keyboard window last learned about the system's bottom bar, for the settings screen
 * and `dumpsys`: a device that draws the bar over the keys without reporting it can be diagnosed
 * from the phone itself. Holds sizes and inset values only, never anything typed.
 */
object BottomBarDiagnostics {
    var report: String by mutableStateOf("The keyboard has not been shown yet.")
}
