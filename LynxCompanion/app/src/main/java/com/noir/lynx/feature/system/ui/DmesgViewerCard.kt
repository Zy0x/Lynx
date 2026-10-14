package com.noir.lynx.feature.system.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

@Composable
fun DmesgViewerCard(
    dmesgState: DmesgLogState,
    onLoadLogs: (String) -> Unit,
    onExport: () -> Unit,
) {
    var searchFilter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (dmesgState.logs.isEmpty()) {
            onLoadLogs("")
        }
    }

    LynxCard(
        title = "Kernel dmesg Ring Buffer Viewer",
        icon = Icons.Default.Description,
        accentColor = AccentBlue
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                    placeholder = { Text("Filter dmesg (e.g. cpu, thermal, gpu)...", color = TextSecondary, fontSize = 11.5.sp) },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                IconButton(onClick = { onLoadLogs(searchFilter) }) {
                    Icon(Icons.Default.Refresh, null, tint = AccentBlue)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                if (dmesgState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        dmesgState.logs.takeLast(100).forEach { line ->
                            Text(
                                text = line,
                                color = when {
                                    line.contains("error", ignoreCase = true) || line.contains("fail", ignoreCase = true) -> AccentRed
                                    line.contains("warn", ignoreCase = true) -> AccentOrange
                                    else -> TextSecondary
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            LynxActionButton(
                text = "Ekspor Log Lengkap ke /sdcard/Debug/",
                icon = Icons.Default.FileDownload,
                onClick = onExport,
                accentColor = AccentBlue
            )
        }
    }
}

