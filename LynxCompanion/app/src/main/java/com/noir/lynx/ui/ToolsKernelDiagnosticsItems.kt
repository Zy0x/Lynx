package com.noir.lynx.ui
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

internal fun LazyListScope.toolsKernelDiagnosticsItems(uiState: LynxUiState, viewModel: LynxViewModel) {

                        // ── Kernel Capability Matrix & Hardware Node Inspector Card ──
                        item(key = "kernel_capabilities") {
                            val capReport = uiState.capabilityReport
                            LynxCard(
                                title = "Kernel Capability Matrix & Node Inspector",
                                icon = Icons.Default.CheckCircle,
                                accentColor = AccentGreen
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        val score = capReport?.scorePercent ?: 0
                                        Text(
                                            "Skor Kompatibilitas Kernel: $score%",
                                            color = if (score >= 70) AccentGreen else AccentOrange,
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                                        )
                                        Text(
                                            capReport?.kernelRelease?.take(36) ?: "Memindai kernel...",
                                            color = TextSecondary, fontSize = 11.sp, maxLines = 1
                                        )
                                    }
                                    IconButton(onClick = { viewModel.scanKernelCapabilities() }) {
                                        Icon(Icons.Default.Refresh, "Rescan", tint = AccentGreen, modifier = Modifier.size(20.dp))
                                    }
                                }

                                if (capReport == null) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Memeriksa 9 subsistem hardware...", color = TextSecondary, fontSize = 12.sp)
                                    }
                                } else {
                                    capReport.items.forEach { cap ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = BgElevated,
                                            border = BorderStroke(1.dp, BorderGlass),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            cap.title,
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 12.sp
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = if (cap.isSupported) AccentGreen.copy(alpha = 0.18f) else Color.Red.copy(alpha = 0.12f),
                                                            border = BorderStroke(1.dp, if (cap.isSupported) AccentGreen.copy(alpha = 0.5f) else Color.Red.copy(alpha = 0.3f))
                                                        ) {
                                                            Text(
                                                                if (cap.isSupported) "SUPPORTED" else "UNAVAILABLE",
                                                                color = if (cap.isSupported) AccentGreen else Color.Red.copy(alpha = 0.8f),
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        cap.detail,
                                                        color = TextSecondary,
                                                        fontSize = 10.5.sp,
                                                        lineHeight = 14.sp,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── FKM Parity: Custom Shell Script Manager Card ──────────
                        item(key = "custom_scripts") {
                            CustomScriptManagerCard(
                                scripts = uiState.customScripts,
                                onExecute = { viewModel.executeCustomScript(it) },
                                onSave = { viewModel.saveCustomScript(it) },
                                onDelete = { viewModel.deleteCustomScript(it) }
                            )
                        }

                        // ── FKM Parity: Live Kernel Dmesg Ring Buffer Viewer Card ─
                        item(key = "dmesg_viewer") {
                            DmesgViewerCard(
                                dmesgState = uiState.dmesgState,
                                onLoadLogs = { viewModel.loadDmesgLog(it) },
                                onExport = { viewModel.exportDmesgLog() }
                            )
                        }
}
