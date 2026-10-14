package com.noir.lynx.feature.system.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.core.locale.localizedUiText
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

@Composable
fun CustomScriptManagerCard(
    scripts: List<CustomScriptItem>,
    onExecute: (CustomScriptItem) -> Unit,
    onSave: (CustomScriptItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newScript by remember { mutableStateOf("") }
    var newRunOnBoot by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = BgCard,
            title = { Text("Tambah Skrip Shell Baru", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nama Skrip") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, unfocusedBorderColor = BorderGlass, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    OutlinedTextField(
                        value = newScript,
                        onValueChange = { newScript = it },
                        label = { Text("Perintah Shell (Root)") },
                        modifier = Modifier.fillMaxWidth().height(140.dp).padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, unfocusedBorderColor = BorderGlass, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Jalankan Saat Boot", color = TextSecondary, fontSize = 12.sp)
                        Switch(
                            checked = newRunOnBoot,
                            onCheckedChange = { newRunOnBoot = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentCyan)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newScript.isNotBlank()) {
                            onSave(
                                CustomScriptItem(
                                    id = System.currentTimeMillis().toString(),
                                    name = newName.trim(),
                                    script = newScript.trim(),
                                    runOnBoot = newRunOnBoot
                                )
                            )
                            newName = ""
                            newScript = ""
                            newRunOnBoot = false
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Simpan", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    LynxCard(
        title = "Custom Shell Script Manager",
        icon = Icons.Default.Terminal,
        accentColor = AccentCyan
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pustaka Skrip Kernel & Sistem (${scripts.size})",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                TextButton(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentCyan)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            scripts.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(localizedUiText(item.name), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (item.runOnBoot) {
                                    Text("Run on boot", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row {
                                IconButton(onClick = { onExecute(item) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.PlayArrow, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (item.lastOutput.isNotBlank() || item.lastErrorOutput.isNotBlank() || item.lastRunFailure.isNotBlank() || item.lastDurationMs > 0L) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BgSurfaceLowest,
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (item.lastOutput.isNotBlank()) {
                                        Text(
                                            text = item.lastOutput,
                                            color = if (item.lastExitCode == 0) AccentCyan else AccentOrange,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (item.lastErrorOutput.isNotBlank()) {
                                        Text(
                                            text = stringResource(R.string.script_output_stderr_label),
                                            color = AccentOrange,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = item.lastErrorOutput,
                                            color = AccentOrange,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (item.lastRunFailure.isNotBlank()) {
                                        Text(
                                            text = stringResource(R.string.script_execution_failed),
                                            color = AccentOrange,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = item.lastRunFailure,
                                            color = AccentOrange,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (item.lastDurationMs > 0L && item.lastExitCode != null) {
                                        Text(
                                            text = stringResource(
                                                R.string.script_output_summary,
                                                item.lastDurationMs,
                                                item.lastExitCode
                                            ),
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
    }
}

// ============================================================
//  FKM PARITY: KERNEL DMESG VIEWER CARD
// ============================================================
