package com.noir.lynx.designsystem.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.*

// ============================================================
//  SocChip — Hardware Architecture Indicator
// ============================================================

@Composable
fun SocChip(soc: String, isSpoofed: Boolean) {
    val labelRes = when {
        soc == "qcom" && !isSpoofed -> R.string.soc_snapdragon
        soc == "mtk"  && !isSpoofed -> R.string.soc_mediatek
        soc == "qcom" && isSpoofed  -> R.string.soc_snapdragon_spoofed
        soc == "mtk"  && isSpoofed  -> R.string.soc_mediatek_spoofed
        else                         -> R.string.soc_universal_linux
    }
    val color = if (isSpoofed) AccentOrange else AccentCyan

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.25f))
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
