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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext

/**
 * The colour roles the keyboard draws with, derived from the Material 3 scheme so that dynamic
 * colour (Android 12+) and the fixed fallback palette both flow through the same names.
 */
@Immutable
data class KeyboardColors(
    /** Keyboard background behind the keys. */
    val background: Color,
    /** The toolbar's band; the background itself, except where a theme sets the toolbar apart. */
    val toolbar: Color,
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
    /** Secondary labels: space-bar language, the shifted symbol on a key, the language sheet. */
    val subtle: Color,
    /**
     * A key's Fn legend while Fn is idle. Every key on the 60% board carries one, and at full
     * strength thirty of them read as loudly as the glyphs do; recessed, they are there to be
     * looked up rather than read. Fn arming a key still tints it [armedRing].
     */
    val legend: Color,
    /** The one-pixel shadow under keys. */
    val keyShadow: Color,
)

val LocalKeyboardColors = staticCompositionLocalOf<KeyboardColors> {
    error("KeyboardColors are only available inside KeyboardTheme")
}

/** Fixed seed used below Android 12, where there is no wallpaper palette to follow. */
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
    toolbar = surfaceContainer,
    key = surfaceBright,
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
    legend = onSurfaceVariant.copy(alpha = 0.55f),
    keyShadow = if (dark) Color(0x80000000) else Color(0x29000000),
)

/**
 * The dark theme's neutrals, stepped up from the system's own dark [surface] — the colour behind
 * every app — by laying its text colour over it at fixed strengths. The dynamic scheme's container
 * roles are not used here: One UI fills them out of Material's order (its board came out lighter
 * than both its keys and the system's background), so only the surface and its text colour are
 * trusted, and the steps between board, function keys, letter keys and a press are our own.
 */
internal fun KeyboardColors.darkSurfaces(surface: Color, onSurface: Color): KeyboardColors {
    fun step(strength: Float) = onSurface.copy(alpha = strength).compositeOver(surface)
    return copy(
        background = surface,
        toolbar = surface,
        functionKey = step(DarkSteps.FUNCTION_KEY),
        key = step(DarkSteps.LETTER_KEY),
        popup = step(DarkSteps.POPUP),
        pressedKey = step(DarkSteps.PRESSED),
    )
}

/** How much of the text colour each dark surface carries over the system's background. */
internal object DarkSteps {
    const val FUNCTION_KEY = 0.07f
    const val LETTER_KEY = 0.14f
    const val POPUP = 0.20f
    const val PRESSED = 0.26f
}

/**
 * The Black theme's neutrals, measured from a screenshot of Samsung's keyboard in its dark mode: a
 * black board, letter keys a mid grey, and the function keys (Enter included) and the toolbar
 * band one step up from black. The accents — an armed modifier, a chip — stay the dark scheme's.
 */
internal object BlackPalette {
    val board = Color(0xFF000000)
    val band = Color(0xFF171719)
    val letterKey = Color(0xFF39393B)
    val pressedKey = Color(0xFF55555A)
    val popup = Color(0xFF48484B)
    val label = Color(0xFFFDFCFF)
    val icon = Color(0xFFE3E3E6)
}

/** These colours on Samsung's black board: surfaces from [BlackPalette], accents kept. */
internal fun KeyboardColors.black(): KeyboardColors = copy(
    background = BlackPalette.board,
    toolbar = BlackPalette.band,
    key = BlackPalette.letterKey,
    onKey = BlackPalette.label,
    functionKey = BlackPalette.band,
    onFunctionKey = BlackPalette.label,
    pressedKey = BlackPalette.pressedKey,
    action = BlackPalette.band,
    onAction = BlackPalette.label,
    popup = BlackPalette.popup,
    onPopup = BlackPalette.label,
    icon = BlackPalette.icon,
    keyShadow = Color.Transparent,
)

/**
 * Applies the Material 3 scheme (dynamic on Android 12+, fixed below) and exposes
 * [LocalKeyboardColors] for the keyboard composables.
 *
 * @param darkTheme null follows the system; true or false forces it (the theme setting).
 * @param black the Black theme: the dark scheme's accents on [BlackPalette]'s surfaces.
 */
@Composable
fun KeyboardTheme(
    darkTheme: Boolean? = null,
    black: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = black || (darkTheme ?: isSystemInDarkTheme())
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> FallbackDark
        else -> FallbackLight
    }
    MaterialTheme(colorScheme = scheme) {
        val colors = scheme.toKeyboardColors(dark).let {
            when {
                black -> it.black()
                dark -> it.darkSurfaces(scheme.surface, scheme.onSurface)
                else -> it
            }
        }
        CompositionLocalProvider(LocalKeyboardColors provides colors) {
            content()
        }
    }
}
