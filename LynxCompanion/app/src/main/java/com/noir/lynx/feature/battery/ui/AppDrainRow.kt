package com.noir.lynx.feature.battery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.AppDrainItem
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.cpu.ui.ProcessIcon
import java.util.Locale

@Composable
fun AppDrainRow(
    item: AppDrainItem,
    maxDrainPct: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgCard,
        border = BorderStroke(0.7.dp, BorderGlass),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatteryConsumerIcon(
                packageName = item.packageName,
                appName = item.appName,
                modifier = Modifier.size(34.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${kotlin.math.round(item.drainPercent).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (item.drainMah > 0f) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (item.drainMah >= 10f) "${kotlin.math.round(item.drainMah).toInt()} mAh" else String.format(Locale.US, "%.1f mAh", item.drainMah),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(5.dp))

                val progressRatio = if (maxDrainPct > 0f) {
                    (item.drainPercent / maxDrainPct).coerceIn(0.03f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(BgElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AccentCyan, AccentBlue)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryConsumerIcon(
    packageName: String,
    appName: String,
    modifier: Modifier = Modifier
) {
    val isSystemOrHardware = packageName in listOf("screen", "idle", "wakelock", "audio", "wifi", "cell", "cellular", "system_services", "kernel", "android", "audioserver", "mediaextractor", "bluetooth", "camera")

    if (isSystemOrHardware) {
        val (icon, tint) = when (packageName) {
            "screen" -> Pair(Icons.Default.StayCurrentPortrait, AccentCyan)
            "idle" -> Pair(Icons.Default.Bedtime, AccentBlue)
            "wakelock" -> Pair(Icons.Default.Timer, AccentOrange)
            "audio", "audioserver" -> Pair(Icons.Default.VolumeUp, AccentPurple)
            "wifi" -> Pair(Icons.Default.Wifi, AccentCyan)
            "cell", "cellular" -> Pair(Icons.Default.SignalCellularAlt, AccentGreen)
            "kernel" -> Pair(Icons.Default.Memory, AccentGreen)
            "android", "system_services" -> Pair(Icons.Default.Android, AccentGreen)
            "camera" -> Pair(Icons.Default.Videocam, AccentOrange)
            else -> Pair(Icons.Default.Memory, AccentCyan)
        }
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            color = BgElevated,
            border = BorderStroke(0.6.dp, BorderSubtle)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = appName,
                    tint = tint,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    } else {
        ProcessIcon(
            packageName = packageName,
            processName = appName,
            modifier = modifier
        )
    }
}
