package com.noir.lynx.feature.system.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*

private val DMESG_FILTER_PRESETS = listOf(
    "Semua" to "",
    "⚠️ Error & Panic" to "error",
    "⚡ CPU & Sched" to "cpu",
    "🎮 GPU & Driver" to "gpu",
    "🔥 Suhu & Termal" to "thermal",
    "🔋 Daya & Baterai" to "battery",
    "📶 Jaringan (WLAN)" to "wlan",
    "💾 Memori & ZRAM" to "zram"
)

@Composable
fun DmesgViewerCard(
    dmesgState: DmesgLogState,
    onLoadLogs: (String) -> Unit,
    onExport: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var searchFilter by remember { mutableStateOf("") }
    var activeFilterChip by remember { mutableStateOf("Semua") }

    LaunchedEffect(Unit) {
        if (dmesgState.logs.isEmpty()) {
            onLoadLogs("")
        }
    }

    // Hitung statistik keparahan log
    val totalLogs = dmesgState.logs.size
    val errorCount = remember(dmesgState.logs) {
        dmesgState.logs.count { line ->
            line.contains("error", ignoreCase = true) ||
            line.contains("panic", ignoreCase = true) ||
            line.contains("fatal", ignoreCase = true) ||
            line.contains("fail", ignoreCase = true)
        }
    }
    val warnCount = remember(dmesgState.logs) {
        dmesgState.logs.count { line ->
            line.contains("warn", ignoreCase = true)
        }
    }

    LynxCard(
        title = "Konsol Log Kernel (dmesg)",
        icon = Icons.Default.Description,
        accentColor = AccentBlue
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Ring buffer kernel real-time untuk mendiagnosis crash, driver, dan stabilitas sistem.",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // 1. Severity Counter Ribbon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "$totalLogs", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "Total Log", color = TextTertiary, fontSize = 9.sp)
                    }
                }

                // Error / Panic
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (errorCount > 0) AccentRed.copy(alpha = 0.14f) else BgElevated,
                    border = BorderStroke(0.6.dp, if (errorCount > 0) AccentRed.copy(alpha = 0.45f) else BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$errorCount",
                            color = if (errorCount > 0) AccentRed else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(text = "Error / Panic", color = if (errorCount > 0) AccentRed else TextTertiary, fontSize = 9.sp)
                    }
                }

                // Warning
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (warnCount > 0) AccentOrange.copy(alpha = 0.14f) else BgElevated,
                    border = BorderStroke(0.6.dp, if (warnCount > 0) AccentOrange.copy(alpha = 0.45f) else BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$warnCount",
                            color = if (warnCount > 0) AccentOrange else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(text = "Peringatan", color = if (warnCount > 0) AccentOrange else TextTertiary, fontSize = 9.sp)
                    }
                }
            }

            // 2. Quick Filter Chips (Grid 2x4 Seimbang TANPA horizontalScroll)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DMESG_FILTER_PRESETS.chunked(4).forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chunk.forEach { (label, filterKeyword) ->
                            val isSelected = activeFilterChip == label
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                                border = BorderStroke(
                                    0.7.dp,
                                    if (isSelected) AccentBlue.copy(alpha = 0.6f) else BorderSubtle
                                ),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    activeFilterChip = label
                                    searchFilter = filterKeyword
                                    onLoadLogs(filterKeyword)
                                }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) AccentBlue else TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Search & Control Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = {
                        searchFilter = it
                        activeFilterChip = "Kustom"
                        onLoadLogs(it)
                    },
                    placeholder = { Text("Cari keyword teks di dmesg...", color = TextTertiary, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        if (searchFilter.isNotBlank()) {
                            IconButton(onClick = {
                                searchFilter = ""
                                activeFilterChip = "Semua"
                                onLoadLogs("")
                            }) {
                                Icon(Icons.Default.Close, "Bersihkan", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp, color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Tombol Refresh Log
                IconButton(
                    onClick = { onLoadLogs(searchFilter) },
                    modifier = Modifier.size(40.dp)
                ) {
                    if (dmesgState.isLoading) {
                        CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Segarkan", tint = AccentBlue, modifier = Modifier.size(20.dp))
                    }
                }

                // Tombol Salin Log
                IconButton(
                    onClick = {
                        if (dmesgState.logs.isNotEmpty()) {
                            val allText = dmesgState.logs.joinToString("\n")
                            clipboardManager.setText(AnnotatedString(allText))
                            Toast.makeText(context, "${dmesgState.logs.size} baris log disalin ke papan klip", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin Log", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            // 4. Log Output Terminal Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
            ) {
                if (dmesgState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Membaca ring buffer kernel...", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                } else if (dmesgState.logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tidak ada entri log yang cocok dengan filter '${searchFilter}'",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        dmesgState.logs.takeLast(150).forEach { line ->
                            Text(
                                text = line,
                                color = when {
                                    line.contains("error", ignoreCase = true) ||
                                    line.contains("panic", ignoreCase = true) ||
                                    line.contains("fatal", ignoreCase = true) ||
                                    line.contains("fail", ignoreCase = true) -> AccentRed

                                    line.contains("warn", ignoreCase = true) -> AccentOrange

                                    line.contains("lynx", ignoreCase = true) -> AccentCyan

                                    line.contains("thermal", ignoreCase = true) -> AccentOrange

                                    else -> TextSecondary
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 12.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 5. Ekspor Log Lengkap
            LynxActionButton(
                text = "Ekspor Log Lengkap ke /sdcard/Debug/",
                icon = Icons.Default.FileDownload,
                onClick = onExport,
                accentColor = AccentBlue
            )
        }
    }
}
