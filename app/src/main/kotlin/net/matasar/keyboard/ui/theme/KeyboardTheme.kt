package net.matasar.keyboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The colour roles the keyboard draws with, derived from the Material 3 scheme so that dynamic
 * colour (Android 12+) and the fixed fallback palette both flow through the same names.
 */
@Immutable
data class KeyboardColors(
    /** Keyboard background behind the keys. */
    val background: Color,
    /** Letter keys. */
    val key: Color,
    val onKey: Color,
    /** Function keys: shift, backspace, ?123, comma, period, the developer strip. */
    val functionKey: Color,
    val onFunctionKey: Color,
    /** Pressed tint for any key. */
    val pressedKey: Color,
    /** The Enter key. */
    val action: Color,
    val onAction: Color,
    /** A modifier armed for one key. */
    val armed: Color,
    val onArmed: Color,
    val armedRing: Color,
    /** A locked modifier. */
    val locked: Color,
    val onLocked: Color,
    /** Press preview and accent popups. */
    val popup: Color,
    val onPopup: Color,
    /** Toolbar icons and chips. */
    val icon: Color,
    val chip: Color,
    val onChip: Color,
    /** Secondary labels: space-bar language, Fn legends, top legends. */
    val subtle: Color,
    /** The one-pixel shadow under keys ("key borders"). */
    val keyShadow: Color,
)

val LocalKeyboardColors = staticCompositionLocalOf<KeyboardColors> {
    error("KeyboardColors are only available inside KeyboardTheme")
}

/** Fixed seed used below Android 12, matching the mock palette. */
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF415F91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF0F2F5E),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF1B1C1F),
    onSurfaceVariant = Color(0xFF5C5F66),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFE6EAF1),
    surfaceContainerHigh = Color(0xFFD2D9E5),
    surfaceContainerHighest = Color(0xFFC9D3E3),
    outlineVariant = Color(0xFFC4C7CF),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    surface = Color(0xFF101214),
    onSurface = Color(0xFFE3E2E6),
    onSurfaceVariant = Color(0xFF8E9099),
    surfaceBright = Color(0xFF35393F),
    surfaceContainer = Color(0xFF1B1D20),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF44484F),
    outlineVariant = Color(0xFF43474E),
)

private fun ColorScheme.toKeyboardColors(dark: Boolean): KeyboardColors = KeyboardColors(
    background = surfaceContainer,
    key = if (dark) surfaceBright else surfaceBright,
    onKey = onSurface,
    functionKey = surfaceContainerHigh,
    onFunctionKey = onSurfaceVariant,
    pressedKey = surfaceContainerHighest,
    action = primary,
    onAction = onPrimary,
    armed = primaryContainer,
    onArmed = onPrimaryContainer,
    armedRing = primary,
    locked = primary,
    onLocked = onPrimary,
    popup = if (dark) surfaceContainerHighest else surfaceBright,
    onPopup = onSurface,
    icon = onSurfaceVariant,
    chip = primaryContainer,
    onChip = onPrimaryContainer,
    subtle = onSurfaceVariant,
    keyShadow = if (dark) Color(0x80000000) else Color(0x29000000),
)

/**
 * Applies the Material 3 scheme (dynamic on Android 12+, fixed below) and exposes
 * [LocalKeyboardColors] for the keyboard composables.
 *
 * @param darkTheme null follows the system; true or false forces it (the theme setting).
 */
@Composable
fun KeyboardTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = darkTheme ?: isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> FallbackDark
        else -> FallbackLight
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalKeyboardColors provides scheme.toKeyboardColors(dark)) {
            content()
        }
    }
}
