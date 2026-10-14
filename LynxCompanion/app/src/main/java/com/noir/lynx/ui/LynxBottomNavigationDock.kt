package com.noir.lynx.ui
import androidx.compose.foundation.layout.BoxScope

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.feature.battery.*
import com.noir.lynx.feature.battery.ui.*

import android.os.Bundle
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.util.VelocityTrackerAddPointsFix
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.data.DeepTunable
import com.noir.lynx.data.TunableType
import com.noir.lynx.data.TunableOption
import com.noir.lynx.data.AppProfileRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.noir.lynx.feature.cpu.ui.*
import com.noir.lynx.feature.gpu.ui.*
import com.noir.lynx.feature.thermal.ui.*
import com.noir.lynx.feature.telemetry.ui.*
import com.noir.lynx.feature.battery.ui.*
import com.noir.lynx.feature.memory.ui.*
import com.noir.lynx.feature.network.ui.*
import com.noir.lynx.feature.system.ui.*
import com.noir.lynx.feature.navigation.model.TuningCategory

@Composable
internal fun BoxScope.LynxBottomNavigationDock(
    uiState: LynxUiState,
    currentAccent: Color,
    onSelectTab: (Int) -> Unit
) {
        val haptic = LocalHapticFeedback.current
        val navItems = listOf(
            Triple("Dashboard", Icons.Default.Speed, 0),
            Triple("Tuning Engine", Icons.Default.Tune, 1),
            Triple("Tools", Icons.Default.Build, 2),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xD90E121E), // Luxury Translucent OLED Glass (~85% opacity)
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color(0x40FFFFFF), // Specular light reflection on top edge
                            Color(0x18FFFFFF), // Mid translucent edge
                            Color(0x0AFFFFFF)  // Base glass edge
                        )
                    )
                ),
                shadowElevation = 0.dp,
                modifier = Modifier.shadow(
                    elevation = 18.dp,
                    shape = CircleShape,
                    spotColor = currentAccent.copy(alpha = 0.35f),
                    ambientColor = currentAccent.copy(alpha = 0.18f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = 0.76f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    navItems.forEach { (label, icon, index) ->
                        val isSelected = uiState.currentTab == index

                        val animatedBgColor by animateColorAsState(
                            targetValue = if (isSelected) currentAccent.copy(alpha = 0.16f) else Color.Transparent,
                            animationSpec = tween(durationMillis = 220),
                            label = "nav_bg_$index"
                        )
                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (isSelected) currentAccent.copy(alpha = 0.45f) else Color.Transparent,
                            animationSpec = tween(durationMillis = 220),
                            label = "nav_border_$index"
                        )
                        val animatedIconTint by animateColorAsState(
                            targetValue = if (isSelected) currentAccent else TextSecondary.copy(alpha = 0.70f),
                            animationSpec = tween(durationMillis = 200),
                            label = "nav_icon_$index"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 0.95f,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                            label = "nav_scale_$index"
                        )
                        val horizontalPadding by animateDpAsState(
                            targetValue = if (isSelected) 16.dp else 12.dp,
                            animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow),
                            label = "nav_pad_$index"
                        )

                        Surface(
                            modifier = Modifier
                                .height(48.dp)
                                .defaultMinSize(minWidth = 48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Reselecting an active tab must not discard its subpage.
                                    if (uiState.currentTab != index) {
                                        android.util.Log.i("LynxNavigation", "tab ${uiState.currentTab} -> $index")
                                        onSelectTab(index)
                                    }
                                },
                            shape = CircleShape,
                            color = animatedBgColor,
                            border = BorderStroke(1.dp, animatedBorderColor),
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = horizontalPadding)
                                    .fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = animatedIconTint,
                                    modifier = Modifier
                                        .size(21.dp)
                                        .scale(iconScale)
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn(animationSpec = tween(180, delayMillis = 40)) +
                                            expandHorizontally(
                                                animationSpec = spring(
                                                    dampingRatio = 0.76f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                expandFrom = Alignment.Start
                                            ),
                                    exit = fadeOut(animationSpec = tween(120)) +
                                            shrinkHorizontally(
                                                animationSpec = spring(
                                                    dampingRatio = 0.76f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                shrinkTowards = Alignment.Start
                                            )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = label,
                                            color = currentAccent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}
