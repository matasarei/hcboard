package net.matasar.keyboard.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.R
import net.matasar.keyboard.ui.theme.Accents

/**
 * The accent as a row of colour circles, no names on them: the phone's own palette first (only on
 * Android 12+, where there is one; below that it is the blue swatch), then the fixed colours.
 * TalkBack reads each by its colour's name, as a choice among them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentSwatches(selected: AccentColour, theme: ThemeChoice, onPick: (AccentColour) -> Unit) {
    val dark = theme.asDarkTheme() ?: isSystemInDarkTheme()
    val phonePalette = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Below Android 12 the phone's palette is the fixed blue, so the blue swatch stands for it.
    val shown = if (phonePalette || selected != AccentColour.SYSTEM) selected else AccentColour.BLUE
    val context = LocalContext.current
    // The phone's own accent, built once per mode rather than on every redraw of the row.
    val phonePrimary = remember(context, dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
        } else {
            null
        }
    }
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (accent in AccentColour.entries) {
            val fill = when (accent) {
                AccentColour.SYSTEM -> phonePrimary ?: continue
                else -> Accents.getValue(accent).of(dark).primary
            }
            Swatch(
                fill = fill,
                phonePalette = accent == AccentColour.SYSTEM,
                name = stringResource(accent.nameRes()),
                selected = accent == shown,
                onClick = { onPick(accent) },
            )
        }
    }
}

@Composable
private fun Swatch(fill: Color, phonePalette: Boolean, name: String, selected: Boolean, onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { contentDescription = name }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(if (selected) Modifier.border(2.dp, onSurface, CircleShape) else Modifier)
                .padding(if (selected) 4.dp else 0.dp)
                .clip(CircleShape)
                // The phone's palette wears a rainbow ring around its own colour: it is every colour the wallpaper gives.
                .then(if (phonePalette) Modifier.background(Brush.sweepGradient(RAINBOW)).padding(4.dp).clip(CircleShape) else Modifier)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = checkOn(fill), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A check that reads on [fill]: dark on the light tones (dark theme, yellow), light on the rest. */
private fun checkOn(fill: Color): Color =
    if (0.299f * fill.red + 0.587f * fill.green + 0.114f * fill.blue > 0.6f) Color.Black else Color.White

private val RAINBOW = listOf(
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE53935),
)

private fun AccentColour.nameRes(): Int = when (this) {
    AccentColour.SYSTEM -> R.string.settings_accent_system
    AccentColour.RED -> R.string.settings_accent_red
    AccentColour.ORANGE -> R.string.settings_accent_orange
    AccentColour.YELLOW -> R.string.settings_accent_yellow
    AccentColour.GREEN -> R.string.settings_accent_green
    AccentColour.TEAL -> R.string.settings_accent_teal
    AccentColour.BLUE -> R.string.settings_accent_blue
    AccentColour.INDIGO -> R.string.settings_accent_indigo
    AccentColour.PURPLE -> R.string.settings_accent_purple
    AccentColour.PINK -> R.string.settings_accent_pink
}
