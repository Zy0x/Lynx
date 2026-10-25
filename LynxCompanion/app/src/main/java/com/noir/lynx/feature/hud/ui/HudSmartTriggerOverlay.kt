package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Modern Material 3 Expressive Tokens
private val M3Cyan = Color(0xFF00E5FF)
private val M3Amber = Color(0xFFFFB300)
private val M3Slate = Color(0xFF4A5568)
private val M3SurfaceDark = Color(0xF20D1117)

/**
 * Ultra-modern Material 3 floating token for Dialogue Auto-Skip.
 *
 * Replaces generic raw emojis with crisp, hardware-accelerated vector iconography,
 * glassmorphic radial elevation, breathing neon pulse on active dialogue,
 * and micro status beacon.
 */
@Composable
fun HudSmartTriggerOverlay(
    isSkipping: Boolean,
    isPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "autoSkipPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val targetBorderColor = when {
        isSkipping -> M3Cyan.copy(alpha = pulseAlpha)
        isPaused -> M3Amber
        else -> Color.White.copy(alpha = 0.22f)
    }

    val targetIcon: ImageVector = when {
        isSkipping -> Icons.Rounded.FastForward
        isPaused -> Icons.Rounded.Pause
        else -> Icons.Rounded.PlayArrow
    }

    val targetIconTint = when {
        isSkipping -> M3Cyan
        isPaused -> M3Amber
        else -> Color.White.copy(alpha = 0.92f)
    }

    val gradientBrush = when {
        isSkipping -> Brush.verticalGradient(
            colors = listOf(Color(0xF00A2538), Color(0xF505141E))
        )
        isPaused -> Brush.verticalGradient(
            colors = listOf(Color(0xF02B1D0E), Color(0xF5181008))
        )
        else -> Brush.verticalGradient(
            colors = listOf(Color(0xF0181F2B), Color(0xF50C1017))
        )
    }

    val statusDotColor = when {
        isSkipping -> M3Cyan.copy(alpha = pulseAlpha)
        isPaused -> M3Amber
        else -> Color.White.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .size(42.dp)
            .shadow(
                elevation = if (isSkipping) 8.dp else 4.dp,
                shape = RoundedCornerShape(13.dp),
                ambientColor = if (isSkipping) M3Cyan.copy(alpha = 0.35f) else Color.Black,
                spotColor = if (isSkipping) M3Cyan.copy(alpha = 0.5f) else Color.Black
            )
            .clip(RoundedCornerShape(13.dp))
            .background(gradientBrush)
            .border(
                border = BorderStroke(if (isSkipping || isPaused) 1.5.dp else 1.dp, targetBorderColor),
                shape = RoundedCornerShape(13.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Main Material 3 Vector Icon
        Icon(
            imageVector = targetIcon,
            contentDescription = if (isSkipping) "Auto-Skip Active" else if (isPaused) "Auto-Skip Paused" else "Auto-Skip Standby",
            tint = targetIconTint,
            modifier = Modifier.size(22.dp)
        )

        // Micro Engine Beacon (Top-Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(statusDotColor)
        )
    }
}
