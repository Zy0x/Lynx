package com.noir.lynx.feature.system.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.system.model.AndroidCrashIncident
import com.noir.lynx.feature.system.model.LogCategoryTab
import com.noir.lynx.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDiagnosticsSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val diagState = uiState.diagnosticHubState

    LaunchedEffect(Unit) {
        viewModel.selectDiagnosticTab(diagState.selectedTab)
    }

    Scaffold(
        containerColor = BgDeepOled,
        topBar = {
            Surface(
                color = BgCard,
                border = BorderStroke(1.dp, BorderGlass)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BgElevated)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Pusat Log & Forensik Sistem",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Deteksi Crash, Last Kmsg, Dropbox & Daemon",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── ACTION ROW: QUICK SUMMARY & EXPORT ZIP ───────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.copyQuickDiagnosticSummary(context) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = AccentCyan.copy(alpha = 0.08f)
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Salin Ringkasan", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { viewModel.exportDiagnosticZip(context) },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue
                            ),
                            enabled = !diagState.isExportingZip
                        ) {
                            if (diagState.isExportingZip) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mengemas ZIP...", color = Color.White, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ekspor Laporan ZIP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── CATEGORY TAB BAR ──────────────────────────────────────
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(LogCategoryTab.values()) { tab ->
                            val isSelected = diagState.selectedTab == tab
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AccentCyan.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentCyan else BorderGlass
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.selectDiagnosticTab(tab) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        when (tab) {
                                            LogCategoryTab.KERNEL_PANIC -> Icons.Default.Warning
                                            LogCategoryTab.ANDROID_CRASH -> Icons.Default.BugReport
                                            LogCategoryTab.LYNX_DAEMON -> Icons.Default.Memory
                                            LogCategoryTab.LIVE_DMESG -> Icons.Default.Terminal
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) AccentCyan else TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        tab.title,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (diagState.selectedTab) {
                LogCategoryTab.KERNEL_PANIC -> KernelPanicTabContent(
                    panic = diagState.kernelPanic,
                    onRefresh = { viewModel.loadKernelPanicDetails() },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(diagState.kernelPanic.fullLog))
                    }
                )
                LogCategoryTab.ANDROID_CRASH -> AndroidCrashTabContent(
                    crashState = diagState.androidCrashes,
                    onRefresh = { viewModel.loadAndroidCrashes() },
                    onSelectIncident = { viewModel.selectAndroidCrashIncident(it) }
                )
                LogCategoryTab.LYNX_DAEMON -> LynxDaemonTabContent(
                    daemonState = diagState.lynxDaemon,
                    onFilterChange = { viewModel.loadLynxDaemonLogs(it) },
                    onRefresh = { viewModel.loadLynxDaemonLogs(diagState.lynxDaemon.filter) },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(diagState.lynxDaemon.logs.joinToString("\n")))
                    }
                )
                LogCategoryTab.LIVE_DMESG -> LiveDmesgTabContent(
                    dmesgState = uiState.dmesgState,
                    onLoadLogs = { viewModel.loadDmesgLog(it) },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(uiState.dmesgState.logs.joinToString("\n")))
                    }
                )
            }

            // ── MODAL SHEET FOR CRASH DETAIL INCIDENT ────────────────────────
            diagState.androidCrashes.selectedIncident?.let { incident ->
                ModalBottomSheet(
                    onDismissRequest = { viewModel.selectAndroidCrashIncident(null) },
                    containerColor = BgCard,
                    scrimColor = Color.Black.copy(alpha = 0.65f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(incident.packageName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${incident.tag} • ${incident.timestamp}", color = AccentOrange, fontSize = 11.5.sp)
                            }
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(incident.fullLog))
                            }) {
                                Icon(Icons.Default.ContentCopy, "Salin Stack Trace", tint = AccentCyan)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgSurfaceLowest,
                            border = BorderStroke(1.dp, BorderGlass),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    incident.fullLog,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KernelPanicTabContent(
    panic: com.noir.lynx.feature.system.model.KernelPanicDetails,
    onRefresh: () -> Unit,
    onCopy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Status Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (panic.isAbnormalReboot) Color(0xFF2A1515) else Color(0xFF132219),
            border = BorderStroke(
                1.dp,
                if (panic.isAbnormalReboot) Color(0xFFFF5252).copy(alpha = 0.5f) else AccentGreen.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (panic.isAbnormalReboot) Icons.Default.Warning else Icons.Default.CheckCircle,
                    null,
                    tint = if (panic.isAbnormalReboot) Color(0xFFFF5252) else AccentGreen,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (panic.isAbnormalReboot) "TERDETEKSI KERNEL PANIC / CRASH BOOT" else "BOOT NORMAL & AMAN",
                        color = if (panic.isAbnormalReboot) Color(0xFFFF5252) else AccentGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Boot Reason: ${panic.bootReason} • Sumber: ${panic.logSource}",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                    if (panic.panicSignature.isNotBlank()) {
                        Text(
                            panic.panicSignature,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = AccentCyan)
                }
            }
        }

        // Terminal Log Container
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgSurfaceLowest,
            border = BorderStroke(1.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("REKAMAN LOG KERNEL PANIC / LAST KMSG", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, "Salin Log", tint = AccentCyan, modifier = Modifier.size(15.dp))
                    }
                }

                if (panic.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = panic.fullLog,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidCrashTabContent(
    crashState: com.noir.lynx.feature.system.model.AndroidCrashState,
    onRefresh: () -> Unit,
    onSelectIncident: (AndroidCrashIncident) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Riwayat Crash & ANR Dropbox (${crashState.incidents.size})",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Refresh", tint = AccentCyan)
            }
        }

        if (crashState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        } else if (crashState.incidents.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Tidak ada rekaman crash/ANR di Dropbox baru-baru ini.", color = TextSecondary, fontSize = 12.5.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(crashState.incidents, key = { it.id }) { incident ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectIncident(incident) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tagColor = when {
                                incident.tag.contains("anr") -> AccentOrange
                                incident.tag.contains("watchdog") -> AccentPurple
                                else -> Color(0xFFFF5252)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tagColor.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, tagColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    incident.tag.replace("data_app_", "").replace("system_server_", "").uppercase(),
                                    color = tagColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    incident.packageName,
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    incident.stackTraceSnippet,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    lineHeight = 14.sp
                                )
                                Text(
                                    incident.timestamp,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LynxDaemonTabContent(
    daemonState: com.noir.lynx.feature.system.model.LynxDaemonLogState,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val filters = listOf("ALL" to "Semua", "ERRORS" to "Error & Warn", "PROFILE" to "Profil AI", "CHARGING" to "Charging")
                items(filters) { (key, label) ->
                    val isSel = daemonState.filter == key
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) AccentCyan.copy(alpha = 0.2f) else BgElevated,
                        border = BorderStroke(1.dp, if (isSel) AccentCyan else BorderGlass),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onFilterChange(key) }
                    ) {
                        Text(
                            label,
                            color = if (isSel) AccentCyan else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, "Salin", tint = AccentCyan, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = AccentCyan, modifier = Modifier.size(16.dp))
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgSurfaceLowest,
            border = BorderStroke(1.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (daemonState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    daemonState.logs.forEach { line ->
                        val lineColor = when {
                            line.contains("error", ignoreCase = true) || line.contains("fail", ignoreCase = true) -> Color(0xFFFF5252)
                            line.contains("warn", ignoreCase = true) -> AccentOrange
                            line.contains("profile", ignoreCase = true) || line.contains("commit", ignoreCase = true) -> AccentCyan
                            line.contains("telemetr", ignoreCase = true) -> AccentGreen
                            else -> TextPrimary
                        }
                        Text(
                            text = line,
                            color = lineColor,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDmesgTabContent(
    dmesgState: com.noir.lynx.data.DmesgLogState,
    onLoadLogs: (String) -> Unit,
    onCopy: () -> Unit
) {
    var searchFilter by remember { mutableStateOf(dmesgState.filter) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchFilter,
                onValueChange = {
                    searchFilter = it
                    onLoadLogs(it)
                },
                placeholder = { Text("Filter dmesg (e.g. panic, thermal, cpu)...", color = TextSecondary, fontSize = 11.5.sp) },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderGlass,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ContentCopy, "Salin", tint = AccentCyan, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onLoadLogs(searchFilter) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, "Refresh", tint = AccentCyan, modifier = Modifier.size(18.dp))
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgSurfaceLowest,
            border = BorderStroke(1.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (dmesgState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    dmesgState.logs.takeLast(250).forEach { line ->
                        val lineColor = when {
                            line.contains("error", ignoreCase = true) || line.contains("panic", ignoreCase = true) || line.contains("fail", ignoreCase = true) -> Color(0xFFFF5252)
                            line.contains("warn", ignoreCase = true) -> AccentOrange
                            else -> TextPrimary
                        }
                        Text(
                            text = line,
                            color = lineColor,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
