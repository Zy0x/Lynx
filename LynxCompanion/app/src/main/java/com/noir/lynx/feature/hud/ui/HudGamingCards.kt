package com.noir.lynx.feature.hud.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.noir.lynx.R
import com.noir.lynx.feature.hud.addins.*
import com.noir.lynx.feature.hud.combo.ExecutionMode
import com.noir.lynx.feature.hud.combo.MacroCombo
import com.noir.lynx.feature.hud.combo.TouchShareParser
import com.noir.lynx.core.locale.LynxText as Text
import kotlinx.coroutines.delay
import java.util.Locale

internal val hudAddinTitles = listOf(
    R.string.hud_addin_focus,          // 0
    R.string.hud_lab_bypass_title,     // 1
    R.string.hud_lab_touch_title,      // 2
    R.string.hud_lab_network_title,    // 3
    R.string.hud_magic_title,          // 4
    R.string.hud_record_title,         // 5
    R.string.hud_console_title,        // 6
    R.string.hud_session_title,        // 7
    R.string.hud_gameshift_title       // 8
)

@Composable
internal fun HudAddinNavigation(onSelect: (Int) -> Unit) {
    val descriptions = listOf(
        R.string.hud_addin_focus_brief,
        R.string.hud_addin_charge_brief,
        R.string.hud_addin_touch_brief,
        R.string.hud_addin_network_brief,
        R.string.hud_addin_magic_brief,
        R.string.hud_record_brief,
        R.string.hud_console_brief,
        R.string.hud_session_brief,
        R.string.hud_gameshift_desc
    )
    val icons = listOf(
        Icons.Default.NotificationsOff,
        Icons.Default.BatteryChargingFull,
        Icons.Default.TouchApp,
        Icons.Default.NetworkCheck,
        Icons.Default.AutoFixHigh,
        Icons.Default.Videocam,
        Icons.Default.Tune,
        Icons.Default.Timer,
        Icons.Default.Bolt
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0..3, 4..8).forEachIndexed { group, indices ->
            Text(hudString(if (group == 0) R.string.hud_addin_group_game else R.string.hud_addin_group_tools), fontSize = 12.sp, color = HudMuted)
            Surface(color = HudCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, HudBorder)) {
                Column {
                    indices.forEach { index ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onSelect(index) }
                            .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(icons[index], contentDescription = null, tint = HudCyan, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(hudString(hudAddinTitles[index]), fontSize = 12.sp, color = Color.White)
                                Text(hudString(descriptions[index]), fontSize = 12.sp, color = HudMuted)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HudMuted, modifier = Modifier.size(16.dp))
                        }
                        if (index != indices.last) HorizontalDivider(Modifier.padding(start = 38.dp), color = HudBorder, thickness = .5.dp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddinToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    info: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            if (!info.isNullOrBlank()) {
                HudInfoButton(title = label, info = info)
            }
        }
        Switch(checked, onChange, enabled = enabled, colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = HudBlueBadge,
            uncheckedThumbColor = HudMuted, uncheckedTrackColor = HudElevated))
    }
}

@Composable
internal fun HudGlowControls() {
    val runtime = HudGamingAddins.current ?: return
    val enabled by runtime.glow.collectAsState()
    val color by runtime.color.collectAsState()
    val connected by runtime.listenerConnected.collectAsState()
    val glowInfo = "${hudString(R.string.hud_glow_description)}\n\n" +
        "• Animasi Kilau Tepi: Menampilkan garis cahaya animasi di sekeliling bezel layar saat ada notifikasi masuk saat bermain game.\n\n" +
        "• Akses Notifikasi: Memerlukan izin Notification Listener agar dapat mendeteksi pesan masuk dari aplikasi chat/pesan."

    AddinToggle(
        label = hudString(R.string.hud_glow_toggle),
        checked = enabled,
        info = glowInfo,
        onChange = runtime::setGlow
    )
    if (!connected) {
        HudAction(hudString(R.string.hud_glow_grant), onClick = runtime::openNotificationAccess, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(R.string.hud_glow_cyan, R.string.hud_glow_blue, R.string.hud_glow_amber).forEachIndexed { index, label ->
            FilterChip(color == index, { runtime.setColor(index) }, label = { Text(hudString(label), fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(containerColor = HudElevated, labelColor = HudMuted,
                    selectedContainerColor = HudBlueBadge.copy(alpha = .3f), selectedLabelColor = Color.White),
                border = BorderStroke(1.dp, if (color == index) HudBlue else HudBorder), modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    HudAction(hudString(R.string.hud_glow_preview), onClick = { runtime.previewGlow() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
}

@Composable
internal fun HudGameNetworkControls() {
    val runtime = HudGamingAddins.current ?: return
    val boost by runtime.wifiBoost.collectAsState()
    val turboEnabled by runtime.networkTurbo.turboEnabled.collectAsState()
    val cellularStandby by runtime.networkTurbo.cellularStandby.collectAsState()
    val tcpTuning by runtime.networkTurbo.tcpTuning.collectAsState()
    val qos by runtime.qos.state.collectAsState()
    val pkg by runtime.magic.foreground.collectAsState()
    var network by remember { mutableStateOf(runtime.network()) }
    var speed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(runtime) {
        var previous = TrafficStats.getTotalRxBytes()
        var at = SystemClock.elapsedRealtime()
        while (true) {
            delay(1000)
            network = runtime.network()
            val now = TrafficStats.getTotalRxBytes(); val time = SystemClock.elapsedRealtime()
            speed = if (previous >= 0 && now >= previous && time > at) {
                String.format(Locale.US, "%.2f Mbps", (now - previous) * .008 / (time - at))
            } else null
            previous = now; at = time
        }
    }

    Text(hudString(R.string.hud_game_network_status,
        if (network.transport == "Mobile") hudString(R.string.hud_network_mobile) else network.transport,
        hudString(if (network.validated) R.string.hud_network_validated else R.string.hud_network_unvalidated)), fontSize = 12.sp, color = HudBlue)
    Text(hudString(R.string.hud_network_throughput, speed ?: "—"), fontSize = 12.sp, color = HudMuted)

    // Dual-Channel Network Turbo Switches
    AddinToggle(
        label = hudString(R.string.hud_net_turbo_toggle),
        checked = turboEnabled,
        info = hudString(R.string.hud_net_turbo_desc),
        onChange = runtime.networkTurbo::setTurboEnabled
    )

    if (turboEnabled) {
        AddinToggle(
            label = hudString(R.string.hud_net_turbo_cellular),
            checked = cellularStandby,
            info = "Menjaga koneksi data seluler tetap aktif di latar belakang saat terhubung ke Wi-Fi sehingga failover ke seluler instan (0 ms) saat sinyal Wi-Fi terputus.",
            onChange = runtime.networkTurbo::setCellularStandby
        )
        AddinToggle(
            label = hudString(R.string.hud_net_turbo_tcp),
            checked = tcpTuning,
            info = "Menerapkan TCP BBR congestion control, TCP window scaling, dan buffer tuning untuk meminimalkan bufferbloat dan jitter koneksi game.",
            onChange = runtime.networkTurbo::setTcpTuning
        )
    }

    AddinToggle(
        label = hudString(R.string.hud_wifi_latency),
        checked = boost,
        enabled = Build.VERSION.SDK_INT >= 29 && (network.wifi || boost),
        info = hudString(R.string.hud_wifi_latency_note),
        onChange = runtime::setWifiBoost
    )

    Text(hudString(R.string.hud_network_dual,
        hudString(if (network.wifi) R.string.hud_active else R.string.hud_addin_inactive),
        hudString(if (network.cellular) R.string.hud_active else R.string.hud_addin_inactive)), fontSize = 12.sp, color = HudMuted)

    val qosNote = hudString(if (qos.capability == com.noir.lynx.feature.hud.data.HudCapability.READY) R.string.hud_network_qos_note else R.string.hud_network_qos_unavailable)
    AddinToggle(
        label = hudString(R.string.hud_network_qos),
        checked = qos.enabled,
        enabled = !qos.busy && qos.capability == com.noir.lynx.feature.hud.data.HudCapability.READY && validMagicPackage(pkg),
        info = qosNote,
        onChange = runtime::setGameQos
    )
}

/**
 * Universal Kombo Makro (Combo Macro) Controller.
 * Fully interactive multi-combo manager with TouchShare passphrase export/import.
 */
@Composable
internal fun HudComboControls() {
    val runtime = HudGamingAddins.current ?: return
    val comboManager = runtime.comboManager
    val combos by comboManager.combos.collectAsState()
    val activeRunning by comboManager.activeRunning.collectAsState()
    val pkg by comboManager.foreground.collectAsState()
    val root by runtime.rootAvailable.collectAsState()
    val context = LocalContext.current

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    selectedIndex = selectedIndex.coerceIn(0, (combos.size - 1).coerceAtLeast(0))
    val currentCombo = combos.getOrElse(selectedIndex) { MacroCombo() }

    var showImportBox by rememberSaveable { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var editName by remember(currentCombo.id) { mutableStateOf(currentCombo.name) }

    val isRunning = activeRunning != null
    val isCurrentRunning = activeRunning == currentCombo.id

    val comboInfo = "${hudString(R.string.hud_magic_description)}\n\n" +
        "• Rekam Sentuhan: Tekan tombol Rekam untuk merekam koordinat dan urutan ketukan layar langsung di atas game.\n\n" +
        "• Mode Eksekusi: '1x Tekan' untuk mengeksekusi kombo satu kali jalan, atau 'Ulang (Loop)' untuk mengulang kombo berulang-ulang selama tombol ditahan.\n\n" +
        "• Tombol Melayang: Munculkan tombol pintas bulat (C₁, C₂...) di layar game dengan dukungan single-tap eksekusi instan dan long-press untuk memindahkan posisi tombol.\n\n" +
        "• TouchShare: Bagikan urutan kombo melalui token portabel Base64 (Salin Kode / Impor Kode).\n\n" +
        "• Batasan Sistem: ${hudString(R.string.hud_magic_limits)}"

    val badgeText = when {
        isCurrentRunning -> "● Berjalan"
        currentCombo.taps.isNotEmpty() -> "● ${currentCombo.taps.size} Titik"
        else -> "Siap"
    }
    val badgeColor = when {
        isCurrentRunning -> HudRed
        currentCombo.taps.isNotEmpty() -> HudCyan
        else -> HudBlue
    }

    HudSection(
        title = hudString(R.string.hud_magic_title),
        badge = badgeText,
        accentColor = badgeColor,
        info = comboInfo
    ) {
        // --- 1. Compact Slot Selector Tabs (C₁, C₂, C₃, C₄, C₅ + Add) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            combos.forEachIndexed { index, c ->
                val isSelected = selectedIndex == index
                val hasTaps = c.taps.isNotEmpty()
                Surface(
                    selected = isSelected,
                    onClick = {
                        selectedIndex = index
                        isRenaming = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isSelected -> HudCyan
                        hasTaps -> HudBlueBadge.copy(alpha = 0.35f)
                        else -> HudElevated
                    },
                    border = BorderStroke(1.dp, if (isSelected) HudCyan else HudBorder),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = c.displayBadge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isSelected) Color(0xFF0A0E14) else if (hasTaps) Color.White else HudMuted
                        )
                    }
                }
            }

            if (combos.size < 5) {
                Surface(
                    onClick = {
                        comboManager.addCombo()
                        selectedIndex = combos.size
                        isRenaming = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = HudElevated,
                    border = BorderStroke(1.dp, HudBorder),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Tambah Slot",
                            tint = HudCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // --- 2. Combo Name & App Target Strip ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRenaming) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BasicTextField(
                        value = editName,
                        onValueChange = { editName = it.take(20) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(HudElevated)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    IconButton(
                        onClick = {
                            if (editName.isNotBlank()) {
                                comboManager.updateCombo(currentCombo.copy(name = editName.trim()))
                            }
                            isRenaming = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Simpan", tint = HudCyan, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            editName = currentCombo.name
                            isRenaming = true
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Slot ${currentCombo.displayBadge}: ${currentCombo.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Ubah Nama",
                        tint = HudMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            if (pkg.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = HudElevated,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = pkg.substringAfterLast('.'),
                        fontSize = 10.sp,
                        color = HudBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // --- 3. In-Game Floating Trigger Toggle ---
        AddinToggle(
            label = "Tombol Melayang di Game (${currentCombo.displayBadge})",
            checked = currentCombo.enabled,
            onChange = { enabled ->
                comboManager.updateCombo(currentCombo.copy(enabled = enabled))
            }
        )

        // --- 4. Mode Eksekusi (Full-Width Segmented Bar — Zero Collision) ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mode Eksekusi", fontSize = 11.5.sp, color = HudMuted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HudElevated)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isOneShot = currentCombo.executionMode == ExecutionMode.ONE_SHOT
                // Segment 1: 1x Tekan
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOneShot) HudBlueBadge.copy(alpha = 0.55f) else Color.Transparent,
                    border = if (isOneShot) BorderStroke(1.dp, HudCyan.copy(alpha = 0.5f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            comboManager.updateCombo(currentCombo.copy(executionMode = ExecutionMode.ONE_SHOT))
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "⚡ 1x Tekan",
                            fontSize = 11.5.sp,
                            fontWeight = if (isOneShot) FontWeight.Bold else FontWeight.Normal,
                            color = if (isOneShot) Color.White else HudMuted
                        )
                    }
                }
                // Segment 2: Ulang (Loop)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isOneShot) HudBlueBadge.copy(alpha = 0.55f) else Color.Transparent,
                    border = if (!isOneShot) BorderStroke(1.dp, HudCyan.copy(alpha = 0.5f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            comboManager.updateCombo(currentCombo.copy(executionMode = ExecutionMode.HOLD_LOOP))
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "🔁 Ulang (Loop)",
                            fontSize = 11.5.sp,
                            fontWeight = if (!isOneShot) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isOneShot) Color.White else HudMuted
                        )
                    }
                }
            }
        }

        if (currentCombo.executionMode == ExecutionMode.HOLD_LOOP) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Jeda Antar-Siklus", fontSize = 11.5.sp, color = HudMuted)
                    Text("${currentCombo.loopDelayMs} ms", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = HudCyan)
                }
                Slider(
                    value = currentCombo.loopDelayMs.toFloat(),
                    onValueChange = { comboManager.updateCombo(currentCombo.copy(loopDelayMs = it.toLong())) },
                    valueRange = 50f..1000f,
                    modifier = Modifier.heightIn(min = 40.dp)
                )
            }
        }

        // --- 5. Touch Sequence & Recording Bento Card ---
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF131822),
            border = BorderStroke(1.dp, if (currentCombo.taps.isNotEmpty()) HudCyan.copy(alpha = 0.25f) else HudBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (currentCombo.taps.isNotEmpty()) HudCyan.copy(alpha = 0.15f) else HudElevated,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = if (currentCombo.taps.isNotEmpty()) HudCyan else HudMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (currentCombo.taps.isEmpty()) "Belum ada ketukan" else "${currentCombo.taps.size} Titik Terekam",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (currentCombo.taps.isEmpty()) "Ketuk Rekam di layar" else "Siap dijalankan di game",
                            fontSize = 10.5.sp,
                            color = HudMuted
                        )
                    }
                }

                Button(
                    onClick = { comboManager.setEditing(currentCombo.id) },
                    enabled = !isRunning && root,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentCombo.taps.isEmpty()) HudCyan else HudElevated,
                        contentColor = if (currentCombo.taps.isEmpty()) Color.Black else HudCyan
                    ),
                    border = if (currentCombo.taps.isNotEmpty()) BorderStroke(1.dp, HudCyan.copy(alpha = 0.5f)) else null,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (currentCombo.taps.isEmpty()) Color.Red else HudCyan)
                        )
                        Text(
                            text = if (currentCombo.taps.isEmpty()) "Rekam" else "Edit",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 6. TouchShare Action Buttons (Salin & Impor — Non-Wrapping) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HudAction(
                label = hudString(R.string.hud_touchshare_export),
                enabled = currentCombo.taps.isNotEmpty(),
                onClick = {
                    val code = TouchShareParser.exportToString(currentCombo)
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("LynxCombo", code))
                    Toast.makeText(context, context.getString(R.string.hud_touchshare_copied), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                icon = Icons.Default.Share
            )

            HudAction(
                label = hudString(R.string.hud_touchshare_import),
                onClick = {
                    showImportBox = !showImportBox
                },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                icon = Icons.Default.ContentPaste
            )
        }

        // --- 7. In-Place Expandable TouchShare Import Box ---
        AnimatedVisibility(
            visible = showImportBox,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            var inputCode by remember { mutableStateOf("") }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF141924),
                border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        hudString(R.string.hud_touchshare_dialog_title),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it },
                        placeholder = { Text("LYNX-COMBO#1#...", color = HudMuted, fontSize = 11.sp) },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = HudCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showImportBox = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(hudString(R.string.hud_magic_cancel), color = HudMuted, fontSize = 11.5.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val imported = TouchShareParser.importFromString(inputCode, currentCombo.id)
                                if (imported != null) {
                                    comboManager.updateCombo(imported)
                                    Toast.makeText(context, context.getString(R.string.hud_touchshare_success), Toast.LENGTH_SHORT).show()
                                    showImportBox = false
                                } else {
                                    Toast.makeText(context, context.getString(R.string.hud_touchshare_invalid), Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HudCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Terapkan", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 8. Main Execution Action ---
        HudAction(
            label = hudString(if (isCurrentRunning) R.string.hud_magic_stop else R.string.hud_magic_start),
            enabled = isRunning || (root && currentCombo.taps.isNotEmpty()),
            onClick = {
                if (isCurrentRunning) {
                    comboManager.stopExecution()
                } else {
                    comboManager.executeCombo(currentCombo.id)
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            isDanger = isCurrentRunning,
            accentColor = if (isCurrentRunning) HudRed else HudCyan
        )

        // --- 9. Delete Slot Text Button (if > 1) ---
        if (combos.size > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        comboManager.removeCombo(currentCombo.id)
                        selectedIndex = 0
                    }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Hapus",
                        tint = HudRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        "Hapus Slot ${currentCombo.displayBadge}",
                        fontSize = 11.5.sp,
                        color = HudRed.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (!root) Text(hudString(R.string.hud_cap_needs_root), fontSize = 12.sp, color = HudOrange)
    }
}


/**
 * Universal ZoneTouch Master Controls.
 */
@Composable
internal fun HudZoneTouchControls() {
    val runtime = HudGamingAddins.current ?: return
    val zoneTouch = runtime.zoneTouch
    val esportsEnabled by zoneTouch.esportsEnabled.collectAsState()
    val samplingBoost by zoneTouch.samplingBoost.collectAsState()
    val touchSlop by zoneTouch.touchSlop.collectAsState()
    val edgeDeadzone by zoneTouch.edgeDeadzone.collectAsState()

    val zoneTouchInfo = "${hudString(R.string.hud_zonetouch_desc)}\n\n" +
        "• Esports Low-Latency Touch: Memaksa responsivitas touch event ke prioritas tertinggi di subsistem Android InputReader.\n\n" +
        "• Sampling Rate Boost: Meningkatkan touch report rate hingga batas maksimal hardware digitizer (240Hz - 480Hz).\n\n" +
        "• Ultra-Sensitive Touch Slop (2px): Mengurangi ambang batas jarak pergerakan jari sebelum diakui sebagai gesture scroll/swipe, membuat sentuhan sangat peka.\n\n" +
        "• Anti-Mistouch Edge Deadzone: Mengatur lebar area mati di tepi layar (0-32px) untuk mencegah ketidaksengajaan sentuhan telapak tangan saat memegang HP."

    HudSection(
        title = hudString(R.string.hud_zonetouch_title),
        badge = hudString(if (esportsEnabled) R.string.hud_active else R.string.hud_addin_inactive),
        accentColor = if (esportsEnabled) HudCyan else HudBlue,
        info = zoneTouchInfo
    ) {
        AddinToggle(
            label = hudString(R.string.hud_zonetouch_esports),
            checked = esportsEnabled,
            onChange = zoneTouch::setEsportsEnabled
        )

        if (esportsEnabled) {
            AddinToggle(
                label = hudString(R.string.hud_zonetouch_sampling),
                checked = samplingBoost,
                onChange = zoneTouch::setSamplingBoost
            )

            AddinToggle(
                label = hudString(R.string.hud_zonetouch_slop),
                checked = touchSlop <= 2,
                info = hudString(R.string.hud_zonetouch_slop_desc),
                onChange = { ultra ->
                    zoneTouch.setTouchSlop(if (ultra) 2 else 8)
                }
            )

            Text(hudString(R.string.hud_zonetouch_deadzone, edgeDeadzone), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Slider(
                value = edgeDeadzone.toFloat(),
                onValueChange = { zoneTouch.setEdgeDeadzone(it.toInt()) },
                valueRange = 0f..32f,
                modifier = Modifier.heightIn(min = 44.dp)
            )
        }
    }
}

/**
 * Universal GameShift / Heavy Mode Reactive Turbo Controls.
 */
@Composable
internal fun HudGameShiftControls() {
    val runtime = HudGamingAddins.current ?: return
    val gameShift = runtime.gameShift
    val enabled by gameShift.enabled.collectAsState()
    val isBurst by gameShift.isBurstActive.collectAsState()
    val spikes by gameShift.spikeCount.collectAsState()

    val gameShiftInfo = "${hudString(R.string.hud_gameshift_desc)}\n\n" +
        "• Reaktif Spike FPS Drop: Otomatis mendeteksi saat terjadi drop frame berat atau lonjakan beban CPU/GPU saat teamfight di game.\n\n" +
        "• CPU/GPU Clock Burst: Memberikan burst boost sesaat pada frekuensi inti CPU dan GPU untuk menstabilkan 60/90/120 FPS secara instan."

    HudSection(
        title = hudString(R.string.hud_gameshift_title),
        badge = if (isBurst) "🚀 TURBO BURST" else hudString(if (enabled) R.string.hud_active else R.string.hud_addin_inactive),
        accentColor = if (isBurst) HudRed else (if (enabled) HudCyan else HudBlue),
        info = gameShiftInfo
    ) {

        AddinToggle(
            label = hudString(R.string.hud_gameshift_toggle),
            checked = enabled,
            onChange = gameShift::setEnabled
        )

        Text(hudString(R.string.hud_gameshift_status, spikes), fontSize = 12.sp, color = if (isBurst) HudCyan else HudMuted)
    }
}

@Composable
internal fun HudMagicBoxControls() {
    // Backwards-compatible router to HudComboControls
    HudComboControls()
}
