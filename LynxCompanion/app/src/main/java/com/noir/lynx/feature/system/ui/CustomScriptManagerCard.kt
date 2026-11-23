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
import com.noir.lynx.core.locale.localizedUiText
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
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*

private val SHELL_SNIPPETS = listOf(
    "Drop Caches" to "sync\necho 3 > /proc/sys/vm/drop_caches",
    "FSTRIM" to "fstrim -v /data\nfstrim -v /cache 2>/dev/null",
    "RAM Compaction" to "echo 1 > /proc/sys/vm/compact_memory 2>/dev/null",
    "TCP FastOpen" to "echo 3 > /proc/sys/net/ipv4/tcp_fastopen",
    "Schedstats Mati" to "echo 0 > /proc/sys/kernel/sched_schedstats",
    "Silent Printk" to "echo '0 0 0 0' > /proc/sys/kernel/printk"
)

@Composable
fun CustomScriptManagerCard(
    scripts: List<CustomScriptItem>,
    onExecute: (CustomScriptItem) -> Unit,
    onSave: (CustomScriptItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingScript by remember { mutableStateOf<CustomScriptItem?>(null) }
    var scriptToDelete by remember { mutableStateOf<CustomScriptItem?>(null) }

    // State untuk form input (Tambah/Edit)
    var formName by remember { mutableStateOf("") }
    var formScript by remember { mutableStateOf("") }
    var formRunOnBoot by remember { mutableStateOf(false) }

    // Dialog Tambah Skrip
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = BgCard,
            title = {
                Text(
                    text = "Tambah Skrip Shell Root Baru",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Skrip akan dieksekusi dengan hak akses root (su). Perintah dapat dijalankan otomatis saat boot selesai.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    OutlinedTextField(
                        value = formName,
                        onValueChange = { formName = it },
                        label = { Text("Nama / Label Skrip", fontSize = 11.5.sp) },
                        placeholder = { Text("Contoh: Optimasi Cache I/O", fontSize = 11.sp, color = TextTertiary) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    // Template Snippet Chips
                    Text(
                        text = "Sisipkan Pola Cepat (Snippet):",
                        color = TextTertiary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for ((label, code) in SHELL_SNIPPETS) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentCyan.copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f)),
                                onClick = {
                                    formScript = if (formScript.isBlank()) code else "$formScript\n$code"
                                }
                            ) {
                                Text(
                                    text = "+ $label",
                                    color = AccentCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = formScript,
                        onValueChange = { formScript = it },
                        label = { Text("Perintah Shell (Bash/Sh Root)", fontSize = 11.5.sp) },
                        placeholder = { Text("# Masukkan kode perintah shell di sini\n...", fontSize = 11.sp, color = TextTertiary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .padding(bottom = 8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Jalankan Otomatis Saat Boot", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Text("Eksekusi setelah sistem selesai boot via service.sh", color = TextSecondary, fontSize = 10.sp)
                            }
                            Switch(
                                checked = formRunOnBoot,
                                onCheckedChange = { formRunOnBoot = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentCyan)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (formName.isNotBlank() && formScript.isNotBlank()) {
                            onSave(
                                CustomScriptItem(
                                    id = System.currentTimeMillis().toString(),
                                    name = formName.trim(),
                                    script = formScript.trim(),
                                    runOnBoot = formRunOnBoot
                                )
                            )
                            formName = ""
                            formScript = ""
                            formRunOnBoot = false
                            showAddDialog = false
                            Toast.makeText(context, "Skrip berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDeepOled)
                ) {
                    Text("Simpan Skrip", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // Dialog Edit Skrip
    editingScript?.let { currentEdit ->
        AlertDialog(
            onDismissRequest = { editingScript = null },
            containerColor = BgCard,
            title = {
                Text(
                    text = "Edit Skrip Shell",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = formName,
                        onValueChange = { formName = it },
                        label = { Text("Nama / Label Skrip", fontSize = 11.5.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    // Template Snippet Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for ((label, code) in SHELL_SNIPPETS) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentBlue.copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, AccentBlue.copy(alpha = 0.35f)),
                                onClick = {
                                    formScript = if (formScript.isBlank()) code else "$formScript\n$code"
                                }
                            ) {
                                Text(
                                    text = "+ $label",
                                    color = AccentBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = formScript,
                        onValueChange = { formScript = it },
                        label = { Text("Perintah Shell (Bash/Sh Root)", fontSize = 11.5.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(bottom = 8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Jalankan Otomatis Saat Boot", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Text("Eksekusi setelah sistem selesai boot", color = TextSecondary, fontSize = 10.sp)
                            }
                            Switch(
                                checked = formRunOnBoot,
                                onCheckedChange = { formRunOnBoot = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentBlue)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (formName.isNotBlank() && formScript.isNotBlank()) {
                            onSave(
                                currentEdit.copy(
                                    name = formName.trim(),
                                    script = formScript.trim(),
                                    runOnBoot = formRunOnBoot
                                )
                            )
                            editingScript = null
                            Toast.makeText(context, "Perubahan skrip disimpan", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = TextPrimary)
                ) {
                    Text("Perbarui Skrip", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingScript = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // Modal Konfirmasi Hapus
    scriptToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { scriptToDelete = null },
            containerColor = BgCard,
            title = { Text("Hapus Skrip Shell?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus skrip '${target.name}'? Tindakan ini tidak dapat dibatalkan.",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(target.id)
                        scriptToDelete = null
                        Toast.makeText(context, "Skrip dihapus", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextPrimary)
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { scriptToDelete = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // Card Utama
    LynxCard(
        title = "Pengelola Skrip Shell & Boot",
        icon = Icons.Default.Terminal,
        accentColor = AccentCyan
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pustaka Skrip Kernel & Otomatisasi (${scripts.size})",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Button(
                    onClick = {
                        formName = ""
                        formScript = ""
                        formRunOnBoot = false
                        showAddDialog = true
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.18f),
                        contentColor = AccentCyan
                    ),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.45f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah Skrip", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (scripts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Code, null, tint = TextTertiary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Belum ada skrip kustom tersimpan",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            scripts.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = localizedUiText(item.name),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.runOnBoot) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AccentCyan.copy(alpha = 0.15f),
                                            border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = "Auto Run Boot",
                                                color = AccentCyan,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${item.script.lines().size} baris perintah",
                                        color = TextTertiary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Action buttons: Run, Edit, Delete (minimum touch targets)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Execute Button
                                IconButton(
                                    onClick = { onExecute(item) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Jalankan Skrip",
                                        tint = AccentCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Edit Button
                                IconButton(
                                    onClick = {
                                        formName = item.name
                                        formScript = item.script
                                        formRunOnBoot = item.runOnBoot
                                        editingScript = item
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Skrip",
                                        tint = AccentBlue,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                // Delete Button
                                IconButton(
                                    onClick = { scriptToDelete = item },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus Skrip",
                                        tint = AccentRed.copy(alpha = 0.85f),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }

                        // Preview Script Snippet
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BgSurfaceLowest,
                            border = BorderStroke(0.6.dp, BorderGlass),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = item.script.take(160) + if (item.script.length > 160) "..." else "",
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        // Status Eksekusi Terakhir
                        if (item.lastOutput.isNotBlank() || item.lastErrorOutput.isNotBlank() || item.lastRunFailure.isNotBlank() || item.lastDurationMs > 0L) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgSurfaceLowest,
                                border = BorderStroke(0.8.dp, if (item.lastExitCode == 0) AccentCyan.copy(alpha = 0.25f) else AccentOrange.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Status Header Bar
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (item.lastExitCode == 0) AccentGreen.copy(alpha = 0.18f) else AccentOrange.copy(alpha = 0.18f),
                                                border = BorderStroke(0.5.dp, if (item.lastExitCode == 0) AccentGreen.copy(alpha = 0.4f) else AccentOrange.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = if (item.lastExitCode == 0) "✓ Sukses (Exit: 0)" else "✗ Gagal (Exit: ${item.lastExitCode ?: -1})",
                                                    color = if (item.lastExitCode == 0) AccentGreen else AccentOrange,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            if (item.lastDurationMs > 0L) {
                                                Text(
                                                    text = "${item.lastDurationMs}ms",
                                                    color = TextTertiary,
                                                    fontSize = 9.5.sp
                                                )
                                            }
                                        }

                                        // Salin Output Button
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = BgElevated,
                                            border = BorderStroke(0.5.dp, BorderSubtle),
                                            onClick = {
                                                val combined = buildString {
                                                    if (item.lastOutput.isNotBlank()) append(item.lastOutput)
                                                    if (item.lastErrorOutput.isNotBlank()) append("\n[STDERR]\n").append(item.lastErrorOutput)
                                                }
                                                clipboardManager.setText(AnnotatedString(combined))
                                                Toast.makeText(context, "Output skrip disalin ke papan klip", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(11.dp))
                                                Text("Salin", color = TextSecondary, fontSize = 9.sp)
                                            }
                                        }
                                    }

                                    if (item.lastOutput.isNotBlank()) {
                                        Text(
                                            text = item.lastOutput,
                                            color = if (item.lastExitCode == 0) AccentCyan else AccentOrange,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            lineHeight = 13.sp
                                        )
                                    }

                                    if (item.lastErrorOutput.isNotBlank()) {
                                        Text(
                                            text = "[STDERR]: ${item.lastErrorOutput}",
                                            color = AccentOrange,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            lineHeight = 13.sp
                                        )
                                    }

                                    if (item.lastRunFailure.isNotBlank()) {
                                        Text(
                                            text = "Gagal Eksekusi: ${item.lastRunFailure}",
                                            color = AccentRed,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp
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
