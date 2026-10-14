package com.noir.lynx.feature.cpu.ui

import java.util.Locale
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.feature.navigation.model.TuningCategory

// ============================================================
//  Live Hardware Telemetry Card — Luxury Bento-Box Layout
// ============================================================

import com.noir.lynx.ui.*

@Composable
fun CpuControlCenterCard(
    isMasterOverride: Boolean,
    activeProfile: com.noir.lynx.profiles.CpuControlProfile,
    recoveryInfo: com.noir.lynx.engine.RecoveryInfo,
    onToggleMasterOverride: (Boolean) -> Unit,
    onSelectProfile: (com.noir.lynx.profiles.CpuControlProfile) -> Unit,
    onOpenRecoveryCenter: () -> Unit,
    onOpenProtectedTasks: () -> Unit,
    applyOnBoot: Boolean,
    onApplyOnBootChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LynxCard(
        title = "CPU CONTROL CENTER",
        icon = Icons.Default.Speed,
        accentColor = if (isMasterOverride) AccentBlue else AccentGreen,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 1. Header Status Bar with Recovery Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isMasterOverride) AccentBlue.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isMasterOverride) AccentBlue.copy(alpha = 0.4f) else AccentGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isMasterOverride) AccentBlue else AccentGreen)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isMasterOverride) "Lynx Override Aktif" else "Managed by System (OEM)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMasterOverride) AccentBlue else AccentGreen
                        )
                    }
                }

                // Quick Action: Recovery Center
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenRecoveryCenter() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Recovery",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            // 2. Master Switch
            LynxSwitch(
                label = "Mode Kontrol CPU",
                subLabel = if (isMasterOverride) "Lynx mengambil alih kontrol penjadwalan CPU, daya, dan performa dari OEM" else "Sistem Android/OEM sepenuhnya mengontrol penjadwalan & daya",
                checked = isMasterOverride,
                onCheckedChange = onToggleMasterOverride
            )

            // 3. Profiles Layer (Shown when Master Switch is ON)
            if (isMasterOverride) {
                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

                Text(
                    text = "PILIH PROFIL PERFORMA",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTertiary,
                    letterSpacing = 1.sp
                )

                // 4 Profile Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pGaming = com.noir.lynx.profiles.CpuControlProfile.GAMING
                    val isGaming = activeProfile == pGaming
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectProfile(pGaming) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isGaming) AccentBlue.copy(alpha = 0.15f) else BgElevated,
                        border = BorderStroke(if (isGaming) 1.5.dp else 1.dp, if (isGaming) AccentBlue else BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = if (isGaming) AccentBlue else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Gaming",
                                fontSize = 11.sp,
                                fontWeight = if (isGaming) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isGaming) AccentBlue else TextPrimary
                            )
                        }
                    }

                    val pBalanced = com.noir.lynx.profiles.CpuControlProfile.BALANCED
                    val isBalanced = activeProfile == pBalanced
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectProfile(pBalanced) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBalanced) AccentCyan.copy(alpha = 0.15f) else BgElevated,
                        border = BorderStroke(if (isBalanced) 1.5.dp else 1.dp, if (isBalanced) AccentCyan else BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Balance,
                                contentDescription = null,
                                tint = if (isBalanced) AccentCyan else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Seimbang",
                                fontSize = 11.sp,
                                fontWeight = if (isBalanced) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isBalanced) AccentCyan else TextPrimary
                            )
                        }
                    }

                    val pBattery = com.noir.lynx.profiles.CpuControlProfile.BATTERY
                    val isBattery = activeProfile == pBattery
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectProfile(pBattery) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBattery) AccentOrange.copy(alpha = 0.15f) else BgElevated,
                        border = BorderStroke(if (isBattery) 1.5.dp else 1.dp, if (isBattery) AccentOrange else BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = if (isBattery) AccentOrange else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Hemat",
                                fontSize = 11.sp,
                                fontWeight = if (isBattery) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isBattery) AccentOrange else TextPrimary
                            )
                        }
                    }

                    val pCustom = com.noir.lynx.profiles.CpuControlProfile.CUSTOM
                    val isCustom = activeProfile == pCustom
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectProfile(pCustom) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCustom) AccentPurple.copy(alpha = 0.15f) else BgElevated,
                        border = BorderStroke(if (isCustom) 1.5.dp else 1.dp, if (isCustom) AccentPurple else BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isCustom) AccentPurple else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Pakar",
                                fontSize = 11.sp,
                                fontWeight = if (isCustom) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isCustom) AccentPurple else TextPrimary
                            )
                        }
                    }
                }

                // Description Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = activeProfile.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = activeProfile.description,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = TextSecondary
                        )

                        // Protected Tasks Quick Link
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenProtectedTasks() }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "6 Proses Sistem Kritis Dilindungi",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = AccentOrange
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "(Lihat)",
                                fontSize = 10.5.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

            // 4. Persistence & Recovery Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recovery Point:",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle)
                ) {
                    Text(
                        text = recoveryInfo.lastCheckpointTime,
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            LynxSwitch(
                label = "Terapkan Saat Boot",
                subLabel = "Simpan snapshot profil aktif & jalankan health-check saat booting",
                checked = applyOnBoot,
                onCheckedChange = onApplyOnBootChange
            )
        }
    }
}

// ============================================================
//  RECOVERY CENTER MODAL BOTTOM SHEET
// ============================================================
