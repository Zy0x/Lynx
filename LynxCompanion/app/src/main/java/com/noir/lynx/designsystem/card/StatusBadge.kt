package com.noir.lynx.designsystem.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.*

// ============================================================
//  Status Badge & Header Helpers
// ============================================================

@Composable
fun StatusBadge(profile: String) {
    val (labelRes, color) = when (profile) {
        "auto"        -> R.string.profile_auto to AccentCyan
        "balance"     -> R.string.profile_balance to AccentBlue
        "performance" -> R.string.profile_performance to AccentOrange
        "extreme"     -> R.string.profile_extreme to AccentRed
        "powersave"   -> R.string.profile_powersave to Color(0xFF00E676)
        else          -> R.string.profile_standby to TextSecondary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier
                    .size(6.dp)
                    .padding(end = 2.dp)
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(labelRes),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                letterSpacing = 0.sp,
            )
        }
    }
}
