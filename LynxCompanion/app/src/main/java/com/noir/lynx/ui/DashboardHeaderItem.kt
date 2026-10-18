package com.noir.lynx.ui

import com.noir.lynx.core.locale.localizedUiText
import androidx.compose.foundation.lazy.LazyListScope

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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.noir.lynx.R
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
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.noir.lynx.feature.cpu.ui.*
import com.noir.lynx.feature.gpu.ui.*
import com.noir.lynx.feature.thermal.ui.*
import com.noir.lynx.feature.telemetry.ui.*
import com.noir.lynx.feature.battery.ui.*
import com.noir.lynx.feature.memory.ui.*
import com.noir.lynx.feature.network.ui.*
import com.noir.lynx.feature.system.ui.*
import com.noir.lynx.feature.navigation.model.TuningCategory

internal fun LazyListScope.dashboardHeaderItem(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    currentAccent: Color,
    onExtremeConfirmRequired: () -> Unit = {},
    onNavigateToProfilesCatalog: () -> Unit = {}
) {
    val state = uiState.state
    item(key = "header") {
        val context = LocalContext.current
        var showThemePalette by remember { mutableStateOf(false) }

        // ── Clean Compact Unified All-in-One Header Card ────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(22.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // ── ROW 1: Brand logo (option 1), Deity codename status, and right-side actions (palette and custom). ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgElevated,
                            border = BorderStroke(0.8.dp, BorderSubtle),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_lynx_logo),
                                contentDescription = localizedUiText("Lynx App Icon"),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Lynx Kernel",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                letterSpacing = 0.sp,
                            )
                            val activeToast = uiState.activeModeToast
                            val showMorph = activeToast != null && (uiState.modeFeedbackDualMorph || uiState.modeFeedbackStyle == "header_morph")
                            AnimatedContent(
                                targetState = if (showMorph) activeToast else null,
                                transitionSpec = {
                                    (slideInVertically { -it } + fadeIn(tween(140)))
                                        .togetherWith(slideOutVertically { it } + fadeOut(tween(140)))
                                },
                                label = "HeaderSubtitleMorph"
                            ) { toast ->
                                if (toast != null) {
                                    val morphColor = Color(toast.accentColorHex)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = morphColor,
                                            modifier = Modifier.size(6.dp)
                                        ) {}
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Mode ${toast.modeLabel} Aktif",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = morphColor,
                                            letterSpacing = 0.2.sp
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = AccentGreen,
                                            modifier = Modifier.size(6.dp)
                                        ) {}
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Codename: Deity",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = AccentGreen,
                                            letterSpacing = 0.2.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right-side actions: theme palette and custom buttons.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { showThemePalette = !showThemePalette },
                            shape = RoundedCornerShape(8.dp),
                            color = if (showThemePalette) currentAccent.copy(alpha = 0.16f) else BgElevated,
                            border = BorderStroke(0.8.dp, if (showThemePalette) currentAccent else BorderSubtle),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = localizedUiText("Pilih Tema Aksen"),
                                    tint = currentAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = onNavigateToProfilesCatalog,
                            shape = RoundedCornerShape(8.dp),
                            color = BgElevated,
                            border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.45f)),
                            modifier = Modifier.defaultMinSize(minHeight = 30.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Kustom",
                                    color = AccentOrange,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                )
                                Spacer(Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = localizedUiText("Buka Profil Kustom"),
                                    tint = AccentOrange,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // ── EXPANDABLE PALETTE: Pilihan 6 Warna Tema Aksen (Smooth Animation) ──
                AnimatedVisibility(
                    visible = showThemePalette,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgElevated,
                            border = BorderStroke(0.8.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "TEMA AKSEN",
                                    color = TextSecondary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(
                                        "cyan" to AccentCyan,
                                        "blue" to AccentBlue,
                                        "purple" to AccentPurple,
                                        "orange" to AccentOrange,
                                        "red" to AccentRed,
                                        "green" to AccentGreen
                                    ).forEach { (name, color) ->
                                        val isSelected = uiState.selectedAccent == name
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clickable { viewModel.setAccentColor(name) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = color,
                                                border = if (isSelected) BorderStroke(2.dp, Color.White) else null,
                                                modifier = Modifier.size(if (isSelected) 16.dp else 12.dp)
                                            ) {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── HAIRLINE GLASS DIVIDER ──
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    color = BorderGlass.copy(alpha = 0.6f),
                    thickness = 0.6.dp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // ── SMART AUTOMATION & BASELINE STATUS PILL ──
                val isAutoRunning = uiState.isAppAutomationActive || com.noir.lynx.service.LynxAppAutomationService.isRunning
                val isAutoProfile = state.activeProfile.equals("auto", ignoreCase = true)
                val activeGamePkg = com.noir.lynx.service.LynxAppAutomationService.activeGamePackage
                val activeGameName = com.noir.lynx.service.LynxAppAutomationService.activeGameName
                val hasOverrides = state.profileOverrides.isNotEmpty() || (activeGamePkg != null)
                val baselineId = com.noir.lynx.service.LynxAppAutomationService.currentBaseline
                val baselineDisplayName = when (baselineId.lowercase().trim()) {
                    "performance" -> "Performa Tinggi"
                    "extreme" -> "Ekstrem"
                    "powersave" -> "Hemat Daya"
                    else -> "Seimbang"
                }

                if (isAutoProfile || hasOverrides || isAutoRunning) {
                    val pillAccent = when {
                        hasOverrides -> AccentOrange
                        isAutoProfile -> AccentCyan
                        else -> AccentGreen
                    }
                    val pillIcon = when {
                        hasOverrides -> Icons.Default.SportsEsports
                        isAutoProfile -> Icons.Default.AutoMode
                        else -> Icons.Default.Tune
                    }
                    val pillTitle = when {
                        activeGameName != null -> "OVERRIDE AKTIF: $activeGameName"
                        hasOverrides -> "OVERRIDE OTOMASI AKTIF"
                        isAutoProfile -> "AUTO (AI) ENGINE AKTIF"
                        else -> "OTOMASI PROFIL SIAGA"
                    }
                    val pillSubtext = when {
                        hasOverrides -> "Kembali ke $baselineDisplayName saat keluar game"
                        isAutoProfile -> "Adaptif Pintar • Profil Pemulihan: $baselineDisplayName"
                        else -> "Profil Dasar Pemulihan: $baselineDisplayName"
                    }

                    Surface(
                        onClick = onNavigateToProfilesCatalog,
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, pillAccent.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = pillAccent.copy(alpha = 0.16f),
                                    border = BorderStroke(0.6.dp, pillAccent.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = pillIcon,
                                            contentDescription = null,
                                            tint = pillAccent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = pillAccent,
                                            modifier = Modifier.size(5.dp)
                                        ) {}
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = pillTitle,
                                            color = pillAccent,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.2.sp,
                                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                        )
                                    }
                                    Text(
                                        text = pillSubtext,
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Detail",
                                    color = pillAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = pillAccent,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }

                // ── BARIS 2: 4-Way Segmented Kernel Performance Profiles ──
                val standardProfiles = listOf(
                    HeaderModeItem("powersave", "Hemat", "Efisiensi Daya", Color(0xFF00E676), Icons.Default.BatteryChargingFull),
                    HeaderModeItem("balance", "Seimbang", if (isAutoProfile) "Basis Auto (AI)" else "Harian Seimbang", if (isAutoProfile) AccentCyan else AccentBlue, Icons.Default.Tune),
                    HeaderModeItem("performance", "Performa", "Gaming Responsif", AccentOrange, Icons.Default.Speed),
                    HeaderModeItem("extreme", "Extreme", "Clock Maksimal", AccentRed, Icons.Default.LocalFireDepartment),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    standardProfiles.forEach { item ->
                        val isPhysicallyActive = state.activeProfile.equals(item.id, ignoreCase = true)
                        val isSelected = if (isAutoProfile) (item.id == "balance") else isPhysicallyActive
                        val animatedBgColor by animateColorAsState(
                            targetValue = if (isSelected) item.accentColor.copy(alpha = 0.18f) else BgElevated,
                            animationSpec = tween(durationMillis = 150),
                            label = "mode_bg_${item.id}"
                        )
                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (isSelected) item.accentColor else BorderSubtle,
                            animationSpec = tween(durationMillis = 150),
                            label = "mode_border_${item.id}"
                        )
                        Surface(
                            onClick = {
                                viewModel.triggerModeFeedback(
                                    modeId = item.id,
                                    modeLabel = item.label,
                                    accentColorHex = item.accentColor.toHexLong()
                                )
                                if (item.id == "extreme" && !isPhysicallyActive) {
                                    onExtremeConfirmRequired()
                                } else {
                                    viewModel.selectKernelPerformanceProfile(item.id, context, true)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = animatedBgColor,
                            border = BorderStroke(
                                width = if (isSelected) 1.4.dp else 0.8.dp,
                                color = animatedBorderColor
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 58.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = localizedUiText(item.label),
                                    tint = if (isSelected) item.accentColor else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )

                                Spacer(Modifier.height(3.dp))

                                Text(
                                    text = item.label,
                                    color = if (isSelected) item.accentColor else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0"),
                                    maxLines = 1
                                )

                                Text(
                                    text = item.subtitle,
                                    color = if (isSelected) item.accentColor.copy(alpha = 0.85f) else TextTertiary,
                                    fontSize = 8.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Active custom profile badge.
                val activeProfileObj = uiState.kernelProfileCatalog.profiles.firstOrNull { it.id == state.activeProfile }
                val isCustomActive = activeProfileObj != null && !activeProfileObj.builtIn
                if (isCustomActive && activeProfileObj != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentCyan.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Profil Kustom Aktif: ${activeProfileObj.name}",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Basis: ${activeProfileObj.baseProfile.uppercase()} • ${activeProfileObj.description.ifBlank { "Kustom Pengguna" }}",
                                    color = TextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class HeaderModeItem(
    val id: String,
    val label: String,
    val subtitle: String,
    val accentColor: Color,
    val icon: ImageVector
)

private fun Color.toHexLong(): Long {
    val a = (alpha * 255).toInt() and 0xFF
    val r = (red * 255).toInt() and 0xFF
    val g = (green * 255).toInt() and 0xFF
    val b = (blue * 255).toInt() and 0xFF
    return ((a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()) and 0xFFFFFFFFL
}
