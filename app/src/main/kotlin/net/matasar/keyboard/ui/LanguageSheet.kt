package net.matasar.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.matasar.keyboard.R
import net.matasar.keyboard.layout.Language
import net.matasar.keyboard.ui.theme.LocalKeyboardColors

/** The picker a long press on the globe opens: the enabled languages, the current one checked. */
@Composable
fun LanguageSheet(languages: List<Language>, current: Language, onPick: (Language) -> Unit, onDismiss: () -> Unit, sidePadding: Dp) {
    val colors = LocalKeyboardColors.current
    KeyboardSheet(onDismiss = onDismiss, sidePadding = sidePadding, scrollable = false) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.subtle.copy(alpha = 0.4f)),
        )
        Text(
            text = "Language",
            color = colors.subtle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
        )
        for (language in languages) {
            val selected = language == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(language) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(language.nativeName, color = colors.onPopup, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                    Text(language.englishName, color = colors.subtle, fontSize = 12.sp)
                }
                if (selected) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.armedRing, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
