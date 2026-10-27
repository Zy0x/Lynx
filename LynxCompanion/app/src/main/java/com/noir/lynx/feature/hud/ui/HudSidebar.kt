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
fun HudSidebar(
    snapshot: HudSnapshot,
    viewport: HudViewport,
    open: Boolean,
    preferences: HudPreferencesStore,
    action: HudActionState,
    apps: List<HudApp>,
    rates: List<Float>,
    onOpen: (Boolean) -> Unit,
    onDrag: (Float) -> Unit,
    onProfile: (String) -> Unit,
    onHz: (Float) -> Unit,
    onCapture: () -> Unit,
    onApp: (HudApp) -> Unit,
    onStop: () -> Unit,
    onBoostRam: () -> Unit = {},
    dockRight: Boolean = false,
    onDrag2D: ((Float, Float) -> Unit)? = null,
    onDragEnd: () -> Unit = {},
    bypassStatus: HudBypassStatus = HudBypassStatus(),
    focusActive: Boolean = false,
    onToggleFocus: () -> Unit = {},
    onToggleBypass: (Boolean) -> Unit = {},
    onRefreshBypass: () -> Unit = {},
    onProbeNetwork: suspend () -> HudNetworkSample = { HudNetworkSample() },
    touchStatus: HudTouchStatus = HudTouchStatus(),
    shieldStatus: HudShieldStatus = HudShieldStatus(),
    brightnessLocked: Boolean = false,
    onToggleTouchBoost: (Boolean) -> Unit = {},
    onToggleHeadsUpShield: (Boolean) -> Unit = {},
    onSelectFocusLevel: (Int) -> Unit = {},
    onToggleBrightnessLock: (Boolean) -> Unit = {},
    isSkipping: Boolean = false,
    isPaused: Boolean = false,
    isTappingLive: Boolean = false,
    onToggleAutoSkip: () -> Unit = {},
    onSnapshotScene: () -> Unit = {},
    sceneResult: com.noir.lynx.feature.hud.autoskip.GenshinSkipEngine.SceneSnapshotResult? = null
) {
    val prefs by preferences.changes.collectAsState(initial = preferences.read())
    SideEffect {
        val level = snapshot[HudMetric.BATTERY_LEVEL]
        com.noir.lynx.feature.hud.addins.HudGamingAddins.current?.acceptBattery(
            level.value?.takeIf { level.status == "valid" && it in 0.0..100.0 }?.toInt())
    }
    var page by remember { mutableStateOf(HudPage.Summary) }
    var manageApps by remember { mutableStateOf(false) }
    var addinPage by rememberSaveable { mutableIntStateOf(-1) }
    val addinScrolls = (0..8).associateWith { rememberScrollState() }
    val scrolls = mapOf(
        HudPage.Summary to rememberScrollState(),
        HudPage.Details to rememberScrollState(),
        HudPage.Lab to rememberScrollState(),
        HudPage.Tools to rememberScrollState()
    )
    val pageState = rememberSaveableStateHolder()
    val context = LocalContext.current
    var appLabel by remember { mutableStateOf("") }
    var activeInfoModal by remember { mutableStateOf<HudInfoData?>(null) }

    LaunchedEffect(snapshot.packageName) {
        appLabel = ""
        appLabel = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(snapshot.packageName, 0)
                ).toString()
            }.getOrDefault(snapshot.packageName)
        }
    }

    HudTheme {
        if (!open) {
            HudMiniOverlay(
                snapshot = snapshot,
                prefs = prefs,
                viewport = viewport,
                onOpen = { onOpen(true) },
                onDrag = onDrag,
                dockRight = dockRight,
                onDrag2D = onDrag2D,
                onDragEnd = onDragEnd,
                isSkipping = isSkipping,
                isPaused = isPaused,
                onToggleAutoSkip = onToggleAutoSkip
            )
        } else {
            val panelShape = if (dockRight) {
                RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            } else {
                RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            }
            val drawerWidthFloat = (viewport.panelWidth - 15f).coerceAtLeast(1f)
            val drawerWidth = drawerWidthFloat.dp
            val navigationLabels = HudPage.values().map { hudString(it.label) }
            val layout = HudLayoutPolicy.resolve(drawerWidthFloat, hudLabelWidth(navigationLabels) + 2f,
                preferMenu = !viewport.landscape)
            val inSubscreen = manageApps || page != HudPage.Summary

            val drawerSurface: @Composable () -> Unit = {
                CompositionLocalProvider(LocalHudInfo provides { activeInfoModal = it }) {
                    Box(
                        modifier = Modifier
                            .width(drawerWidth)
                            .heightIn(max = viewport.panelHeight.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = panelShape,
                            color = HudBackground
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                        HudPanelHeader(
                            appLabel = when {
                                manageApps -> hudString(R.string.hud_apps_manage)
                                !layout.useRail && !(page == HudPage.Lab && addinPage >= 0) -> appLabel
                                page == HudPage.Details -> hudString(R.string.hud_monitor)
                                page == HudPage.Lab -> hudString(hudAddinTitles.getOrNull(addinPage) ?: R.string.hud_lab)
                                page == HudPage.Tools -> hudString(R.string.hud_tools)
                                else -> appLabel
                            },
                            battery = snapshot[HudMetric.BATTERY_LEVEL],
                            batteryTemp = snapshot[HudMetric.BATTERY_TEMP],
                            charging = snapshot.charging,
                            bypassActive = bypassStatus.enabled || bypassStatus.active,
                            canNavigateBack = inSubscreen,
                            onBack = {
                                if (page == HudPage.Lab && addinPage >= 0) addinPage = -1
                                else {
                                    manageApps = false
                                    page = HudPage.Summary
                                }
                            },
                            onClose = { onOpen(false) }
                        )

                        HudRecordingBar()
                        if (!layout.useRail) {
                            HudPageSelector(
                                selected = page,
                                onSelect = {
                                    manageApps = false
                                    if (it == HudPage.Lab) addinPage = -1
                                    page = it
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (layout.useRail && !dockRight) {
                                HudNavigationRail(
                                    selected = page,
                                    width = layout.railWidth,
                                    onSelect = {
                                        manageApps = false
                                        if (it == HudPage.Lab) addinPage = -1
                                        page = it
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                if (manageApps) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        HudAppDock(
                                            apps = apps,
                                            managing = true,
                                            onManage = { manageApps = false },
                                            onApp = {
                                                onOpen(false)
                                                onApp(it)
                                            }
                                        )
                                        HudAppManager(prefs, preferences)
                                    }
                                } else {
                                    pageState.SaveableStateProvider(page.name) {
                                        val recorder = HudGamingAddins.current?.recorder
                                        val recState by (recorder?.state?.collectAsState() ?: remember { mutableStateOf(null) })
                                        val isRecording = recState?.busy == true
                                        var recNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
                                        LaunchedEffect(isRecording) {
                                            while (isRecording) {
                                                recNow = SystemClock.elapsedRealtime()
                                                kotlinx.coroutines.delay(1000)
                                            }
                                        }
                                        val recordDuration = if (isRecording && recState != null) {
                                            if (recState?.phase == HudRecordingPhase.COUNTDOWN) {
                                                "${recState?.countdown}s"
                                            } else {
                                                val elapsed = ((recNow - (recState?.startedAt ?: 0L)).coerceAtLeast(0) / 1000)
                                                String.format(Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60)
                                            }
                                        } else null

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(if (page == HudPage.Lab && addinPage >= 0) addinScrolls.getValue(addinPage) else scrolls.getValue(page))
                                                .padding(horizontal = 4.dp, vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            when (page) {
                                                HudPage.Summary -> {
                                                    HudSummaryContent(
                                                        landscape = viewport.landscape,
                                                        snapshot = snapshot, action = action,
                                                        onProfile = onProfile, onCapture = onCapture,
                                                        onDetails = { page = HudPage.Details }, onBoostRam = onBoostRam
                                                    ) {
                                                    HudQuickTileRow(
                                                        pinned = prefs.pinned,
                                                        focusActive = focusActive,
                                                        busy = action.busy,
                                                        isRecording = isRecording,
                                                        recordDuration = recordDuration,
                                                        onCapture = onCapture,
                                                        onToggleRecord = {
                                                            if (isRecording) {
                                                                recorder?.stop()
                                                            } else {
                                                                onOpen(false)
                                                                recorder?.start()
                                                            }
                                                        },
                                                        onBoostRam = onBoostRam,
                                                        onTogglePin = { preferences.pin(!prefs.pinned) },
                                                        onToggleFocus = onToggleFocus,
                                                        autoSkipEnabled = prefs.autoSkipEnabled,
                                                        onToggleAutoSkip = { preferences.setAutoSkipEnabled(!prefs.autoSkipEnabled) }
                                                    )
                                                    HudAppDock(
                                                        apps = apps,
                                                        managing = manageApps,
                                                        onManage = { manageApps = !manageApps },
                                                        onApp = {
                                                            onOpen(false)
                                                            onApp(it)
                                                        }
                                                    )
                                                    }
                                                }
                                                HudPage.Details -> HudDetails(snapshot = snapshot)
                                                HudPage.Lab -> HudLab(
                                                    snapshot = snapshot,
                                                    bypassStatus = bypassStatus,
                                                    busy = action.busy,
                                                    onToggleBypass = onToggleBypass,
                                                    onRefreshBypass = onRefreshBypass,
                                                    onProbeNetwork = onProbeNetwork,
                                                    touchStatus = touchStatus,
                                                    shieldStatus = shieldStatus,
                                                    onToggleTouchBoost = onToggleTouchBoost,
                                                    onToggleHeadsUpShield = onToggleHeadsUpShield,
                                                    onSelectFocusLevel = onSelectFocusLevel,
                                                    selectedAddin = addinPage,
                                                    onSelectAddin = { addinPage = it },
                                                    brightnessLocked = brightnessLocked,
                                                    onToggleBrightnessLock = onToggleBrightnessLock
                                                )
                                                HudPage.Tools -> HudTools(
                                                    prefs = prefs,
                                                    store = preferences,
                                                    rates = rates,
                                                    activeHz = snapshot[HudMetric.DISPLAY_HZ],
                                                    action = action,
                                                    onHz = onHz,
                                                    onCapture = onCapture,
                                                    onStop = onStop,
                                                    onBoostRam = onBoostRam,
                                                    brightnessLocked = brightnessLocked,
                                                    onToggleBrightnessLock = onToggleBrightnessLock,
                                                    onToggleAutoSkip = { enabled ->
                                                        preferences.setAutoSkipEnabled(enabled)
                                                    },
                                                    onSetAutoSkipSpeed = { preferences.setAutoSkipSpeed(it) },
                                                    onSetAutoSkipChoiceBottom = { preferences.setAutoSkipChoiceBottom(it) },
                                                    isSkipping = isSkipping,
                                                    isPaused = isPaused,
                                                    isTappingLive = isTappingLive,
                                                    onSnapshotScene = onSnapshotScene,
                                                    sceneResult = sceneResult
                                                )
                                            }
                                        }
                                    }
                                }

                                // Floating Feedback Banner at BottomCenter of Viewport
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = action.busy || action.message != null || !action.detailMessage.isNullOrBlank(),
                                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                                ) {
                                    HudFeedbackBanner(
                                        message = if (action.busy) hudString(R.string.hud_pending) else (action.message?.let { hudString(it) } ?: "OK"),
                                        detail = action.detailMessage,
                                        busy = action.busy,
                                        actionType = action.actionType
                                    )
                                }
                            }

                            if (layout.useRail && dockRight) {
                                HudNavigationRail(
                                    selected = page,
                                    width = layout.railWidth,
                                    onSelect = {
                                        manageApps = false
                                        if (it == HudPage.Lab) addinPage = -1
                                        page = it
                                    }
                                )
                            }
                        }
                    }
                    }
                    if (activeInfoModal != null) {
                        HudInfoModal(
                            data = activeInfoModal!!,
                            shape = panelShape,
                            onDismiss = { activeInfoModal = null }
                        )
                    }
                }
            }
        }

            // Protruding Sidekick Handle Ear attached to the inner edge of the open drawer
            Row(
                modifier = Modifier.width(viewport.panelWidth.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dockRight) {
                    SidekickEdgeTab(
                        dockRight = true,
                        modifier = Modifier.clickable { onOpen(false) }
                    )
                    drawerSurface()
                } else {
                    drawerSurface()
                    SidekickEdgeTab(
                        dockRight = false,
                        modifier = Modifier.clickable { onOpen(false) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun HudFeedbackBanner(
    message: String,
    detail: String? = null,
    busy: Boolean = false,
    actionType: String? = null
) {
    val isRed = actionType == "record"
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
                val icon = when (actionType) {
                    "ram" -> Icons.Default.Bolt
                    "capture" -> Icons.Default.CameraAlt
                    "record" -> Icons.Default.Videocam
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
