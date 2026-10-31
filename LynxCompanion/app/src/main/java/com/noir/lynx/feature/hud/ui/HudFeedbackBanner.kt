package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.noir.lynx.feature.hud.addins.HudGamingAddins
import com.noir.lynx.feature.hud.addins.HudRecordingPhase
import com.noir.lynx.feature.hud.*
import com.noir.lynx.feature.hud.data.*
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun HudFeedbackBanner(
    message: String,
    detail: String? = null,
    busy: Boolean = false,
    actionType: String? = null
) {
    val isFailed = message.contains("gagal", ignoreCase = true) || message.contains("failed", ignoreCase = true)
    val isRed = actionType == "record" || isFailed
    val accentColor = if (isRed) HudRed else HudCyan

    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "SweepTransition")
    val sweepOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "SweepOffset"
    )
    val borderBrush = if (busy) {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(accentColor.copy(alpha = 0.2f), accentColor, accentColor.copy(alpha = 0.2f)),
            startX = sweepOffset - 400f,
            endX = sweepOffset
        )
    } else {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.35f))
        )
    }

    Surface(
        color = Color(0xF2121622),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, borderBrush),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = accentColor,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.2.dp
                )
            } else {
                val icon = when {
                    isFailed -> Icons.Default.Warning
                    actionType == "ram" -> Icons.Default.Bolt
                    actionType == "capture" -> Icons.Default.CameraAlt
                    actionType == "record" -> Icons.Default.Videocam
                    else -> Icons.Default.CheckCircle
                }
                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = message,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = accentColor
                    )
                }
            }
        }
    }
}
