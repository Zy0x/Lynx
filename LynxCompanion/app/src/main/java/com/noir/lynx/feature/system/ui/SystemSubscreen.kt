package com.noir.lynx.feature.system.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.AppIconImage
import com.noir.lynx.ui.LynxViewModel

/**
 * Subscreen 7: Subsystem & Deep Tunables Composable
 */
@Composable
fun SystemSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Otomasi Profil Per-Aplikasi Card ────────────────────
        LynxCard(
            title = "Otomasi Profil Per-Aplikasi",
            icon = Icons.Default.SportsEsports,
            accentColor = AccentGreen
        ) {
            // Master Service Toggle
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Layanan Otomasi Latar Belakang",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Pantau aplikasi aktif dan alihkan profil CPU/GPU seketika (0ms boost saat launch)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = uiState.isAppAutomationActive,
                    onCheckedChange = { viewModel.toggleAppAutomation(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderGlass
                    )
                )
            }

            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 10.dp))

            // Header: Rule Count & Add Button
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${uiState.appProfileRules.size} Aturan Profil Dikonfigurasi",
                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp
                    )
                    Text(
                        "Profil khusus diterapkan otomatis saat aplikasi berada di foreground",
                        color = TextSecondary, fontSize = 10.5.sp
                    )
                }
                Surface(
                    onClick = onAddAppClick,
                    shape = RoundedCornerShape(10.dp),
                    color = AccentGreen.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah", color = AccentGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // App Profile Rules List
            if (uiState.appProfileRules.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Tune, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Belum ada aturan khusus",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Klik tombol 'Tambah' untuk mengunci profil tertentu (Performa, Extreme, Balance, Hemat) ke game atau app favorit.",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                uiState.appProfileRules.forEach { rule ->
                    val profColor = when (rule.targetProfile.lowercase()) {
                        "extreme" -> AccentRed
                        "performance" -> AccentOrange
                        "powersave" -> AccentGreen
                        else -> AccentCyan
                    }
                    val profLabel = when (rule.targetProfile.lowercase()) {
                        "extreme" -> "Extreme"
                        "performance" -> "Performa"
                        "powersave" -> "Hemat"
                        else -> "Balance"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, if (rule.enabled) profColor.copy(alpha = 0.35f) else BorderGlass),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            onEditRuleClick(rule)
                        }
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                AppIconImage(
                                    packageName = rule.packageName,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            rule.appName,
                                            color = if (rule.enabled) TextPrimary else TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                        if (rule.isGame) {
                                            Spacer(Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentOrange.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    "GAME",
                                                    color = AccentOrange,
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = profColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, profColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                profLabel,
                                                color = profColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (rule.targetRefreshRate != null) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentPurple.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "${rule.targetRefreshRate} Hz",
                                                    color = AccentPurple,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (rule.autoFloatingHud) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentCyan.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "OSD",
                                                    color = AccentCyan,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { enabled ->
                                        val updated = rule.copy(enabled = enabled)
                                        viewModel.addOrUpdateAppProfileRule(context, updated)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = profColor,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = BgElevated
                                    ),
                                    modifier = Modifier.scale(0.75f)
                                )
                                IconButton(
                                    onClick = { onEditRuleClick(rule) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Info Relokasi Anti-Throttling OEM ───────────────────
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Pengaturan Anti-Throttling OEM (Joyose/GOS) kini dipusatkan pada modul Thermal & Anti-Throttling.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // ── Deep Kernel & System Tunables Card ──────────────────
        var manualNodePath by remember { mutableStateOf("") }
        var maxVisibleItems by remember { mutableStateOf(20) }
        var deepSearchQuery by remember { mutableStateOf("") }

        LynxCard(
            title = "Deep Kernel & System Tunables",
            icon = Icons.Default.Search,
            accentColor = AccentCyan
        ) {
            Text(
                "Mesin pemindai adaptif universal. Membaca seluruh file konfigurasi kernel, OEM, CPU, GPU, & ROM. Petunjuk internal (#) dikonversi otomatis menjadi widget interaktif (toggle, slider, dropdown pilihan).",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Action Bar: Scan button + Auto-Discovery status + Count info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.runDeepScan() },
                    enabled = !uiState.isDeepScanning,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.16f),
                        contentColor = AccentCyan
                    ),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (uiState.isDeepScanning) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Memindai...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.deepTunables.isEmpty()) "Pindai Kernel" else "Pindai Ulang", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.deepTunables.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "${uiState.deepTunables.size} Node",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass)
                    ) {
                        Text(
                            if (uiState.isDeepScanning) "Memindai Sysfs..." else "Zero Hardcoding",
                            color = if (uiState.isDeepScanning) AccentCyan else TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Manual Node Direct Tester
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = manualNodePath,
                    onValueChange = { manualNodePath = it },
                    placeholder = { Text("/proc/sys/... atau /sys/...", fontSize = 11.sp, color = TextTertiary) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated
                    )
                )
                Button(
                    onClick = {
                        if (manualNodePath.isNotBlank()) {
                            viewModel.inspectManualNode(manualNodePath.trim())
                            manualNodePath = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.18f),
                        contentColor = AccentCyan
                    ),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Uji Node", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Search Bar & Category Filter Chips
            if (uiState.deepTunables.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = deepSearchQuery,
                    onValueChange = {
                        deepSearchQuery = it
                        maxVisibleItems = 20
                    },
                    placeholder = { Text("Cari parameter kernel, deskripsi, atau path...", fontSize = 11.sp, color = TextTertiary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (deepSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { deepSearchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated
                    )
                )

                // Quick Category Filters
                val detectedCategories = remember(uiState.deepTunables) {
                    listOf("Semua") + uiState.deepTunables.map { it.category }.distinct().sorted()
                }
                var activeCategoryFilter by remember { mutableStateOf("Semua") }

                Spacer(Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(detectedCategories) { catName ->
                        val isCatSel = activeCategoryFilter == catName
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCatSel) AccentCyan.copy(alpha = 0.22f) else BgElevated,
                            border = BorderStroke(1.dp, if (isCatSel) AccentCyan else BorderGlass),
                            modifier = Modifier.clickable {
                                activeCategoryFilter = catName
                                maxVisibleItems = 20
                            }
                        ) {
                            Text(
                                catName,
                                color = if (isCatSel) AccentCyan else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isCatSel) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val displayedTunables = remember(uiState.deepTunables, deepSearchQuery, activeCategoryFilter) {
                    uiState.deepTunables.filter { tunable ->
                        val matchesCat = activeCategoryFilter == "Semua" || tunable.category.equals(activeCategoryFilter, ignoreCase = true)
                        val matchesSearch = deepSearchQuery.isBlank() ||
                                tunable.name.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.rawName.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.desc.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.path.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.help.contains(deepSearchQuery, ignoreCase = true)
                        matchesCat && matchesSearch
                    }
                }

                if (displayedTunables.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SearchOff, null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tidak ada parameter yang cocok dengan '$deepSearchQuery'",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val itemsToShow = displayedTunables.take(maxVisibleItems)
                    itemsToShow.forEach { tunable ->
                        DeepTunableItemCard(
                            tunable = tunable,
                            onApply = { path, value -> viewModel.applyDeepTunable(path, value) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (displayedTunables.size > maxVisibleItems) {
                        Button(
                            onClick = { maxVisibleItems += 30 },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BgElevated,
                                contentColor = AccentCyan
                            ),
                            border = BorderStroke(1.dp, BorderGlass)
                        ) {
                            Text("Muat Lebih Banyak (${displayedTunables.size - maxVisibleItems} tersisa)", fontSize = 11.5.sp)
                        }
                    }
                }
            } else if (!uiState.isDeepScanning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Explore, null, tint = AccentCyan, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Belum ada hasil pemindaian sysfs",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tekan tombol 'Pindai Kernel' di atas untuk memulai penemuan parameter secara dinamis pada perangkat Anda.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility alias for legacy call sites.
 */
@Composable
fun TuningSystemCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    SystemSubscreen(
        uiState = uiState,
        viewModel = viewModel,
        onAddAppClick = onAddAppClick,
        onEditRuleClick = onEditRuleClick,
        modifier = modifier
    )
}
