package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** The whole keyboard: toolbar on top, the key grid below. Filled in from step 3 on. */
@Composable
fun KeyboardScreen() {
    val colors = LocalKeyboardColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(44.dp))
        Box(modifier = Modifier.fillMaxWidth().height(248.dp))
    }
}
