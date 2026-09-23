package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/**
 * The frame the toolbar's sheets share: a scrim over the keys that closes the sheet when tapped,
 * and the sheet itself at the bottom.
 *
 * Both span the keyboard's full width, as the toolbar's background does; only the sheet's content
 * keeps the keys' [sidePadding]. A sheet that fills all the room under the toolbar has square top
 * corners, a panel that grows out of the toolbar: rounded, its corners showed the scrim as two
 * grey squares against the undimmed toolbar, and the scrim, inset by the side padding, showed as
 * a box with straight edges. A shorter sheet keeps its rounded corners over the scrim.
 */
@Composable
fun KeyboardSheet(
    onDismiss: () -> Unit,
    sidePadding: Dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKeyboardColors.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val room = constraints.maxHeight.toFloat()
        val shape = remember(room) { SheetShape(room) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.popup)
                .navigationBarsPadding()
                .padding(horizontal = sidePadding)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                // A short keyboard (the 80% height setting) has less room than the rows need.
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            content = content,
        )
    }
}

/**
 * Rounded at the top, unless the sheet is as tall as [room]: then square, flush with the toolbar.
 * Decided when the outline is drawn, from the sheet's own size, so there is no frame of the
 * wrong corners first.
 */
internal class SheetShape(private val room: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        if (size.height >= room - 0.5f) {
            RectangleShape.createOutline(size, layoutDirection, density)
        } else {
            ROUNDED.createOutline(size, layoutDirection, density)
        }

    private companion object {
        val ROUNDED = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    }
}
