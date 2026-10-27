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
import androidx.compose.material.icons.filled.*
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

    var showImportDialog by rememberSaveable { mutableStateOf(false) }

    val isRunning = activeRunning != null
    val isCurrentRunning = activeRunning == currentCombo.id

    val comboInfo = "${hudString(R.string.hud_magic_description)}\n\n" +
        "• Rekam Sentuhan: Tekan tombol merah untuk merekam koordinat dan urutan ketukan layar.\n\n" +
        "• Mode Eksekusi: 1x Tekan untuk menjalankan kombo sekali, atau Ulang (Loop) untuk mengeksekusi kombo berulang-ulang selama tombol ditahan.\n\n" +
        "• Tombol Melayang (C1, C2...): Saat diaktifkan, tombol pintas kecil akan muncul di atas game agar dapat memicu kombo secara instan.\n\n" +
        "• TouchShare: Bagikan kode kombo ke teman atau impor kombo dari teks salinan.\n\n" +
        "• Batasan Sistem: ${hudString(R.string.hud_magic_limits)}"

    HudSection(
        title = hudString(R.string.hud_magic_title),
        badge = hudString(if (isRunning) R.string.hud_active else R.string.hud_addin_inactive),
        accentColor = if (isRunning) HudCyan else HudBlue,
        info = comboInfo
    ) {

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(pkg.ifBlank { "—" }, fontSize = 12.sp, color = HudBlue)
            Text(currentCombo.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HudCyan)
        }

        // Horizontal Combo Selector Chips (C₁, C₂, C₃...) + Add Button
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            combos.forEachIndexed { index, c ->
                val isSelected = selectedIndex == index
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedIndex = index },
                    label = { Text("${c.displayBadge} ${c.name}") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = HudElevated,
                        labelColor = HudMuted,
                        selectedContainerColor = HudBlueBadge.copy(alpha = .35f),
                        selectedLabelColor = Color.White
                    ),
                    border = BorderStroke(1.dp, if (isSelected) HudCyan else HudBorder),
                    modifier = Modifier.heightIn(min = 44.dp)
                )
            }

            if (combos.size < 5) {
                IconButton(
                    onClick = {
                        val created = comboManager.addCombo()
                        selectedIndex = combos.size
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Tambah Kombo", tint = HudCyan)
                }
            }
        }

        // Enable in-game floating trigger button toggle
        AddinToggle(
            label = "Tampilkan Tombol Melayang di Game (${currentCombo.displayBadge})",
            checked = currentCombo.enabled,
            onChange = { enabled ->
                comboManager.updateCombo(currentCombo.copy(enabled = enabled))
            }
        )

        // Execution Mode Selector: One-Shot vs Hold Loop
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mode Eksekusi", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = currentCombo.executionMode == ExecutionMode.ONE_SHOT,
                    onClick = { comboManager.updateCombo(currentCombo.copy(executionMode = ExecutionMode.ONE_SHOT)) },
                    label = { Text("1x Tekan", fontSize = 11.5.sp) },
                    modifier = Modifier.height(36.dp)
                )
                FilterChip(
                    selected = currentCombo.executionMode == ExecutionMode.HOLD_LOOP,
                    onClick = { comboManager.updateCombo(currentCombo.copy(executionMode = ExecutionMode.HOLD_LOOP)) },
                    label = { Text("Ulang (Loop)", fontSize = 11.5.sp) },
                    modifier = Modifier.height(36.dp)
                )
            }
        }

        if (currentCombo.executionMode == ExecutionMode.HOLD_LOOP) {
            Text("Jeda Loop: ${currentCombo.loopDelayMs} ms", fontSize = 12.sp, color = HudMuted)
            Slider(
                value = currentCombo.loopDelayMs.toFloat(),
                onValueChange = { comboManager.updateCombo(currentCombo.copy(loopDelayMs = it.toLong())) },
                valueRange = 50f..1000f,
                modifier = Modifier.heightIn(min = 44.dp)
            )
        }

        // Tap summary
        Text(
            text = "Urutan Sentuhan: ${currentCombo.taps.size} titik terekam",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (currentCombo.taps.isNotEmpty()) HudCyan else HudMuted
        )

        // Big Action: Rekam / Edit di Layar Penuh
        HudAction(
            label = "🔴 Rekam / Edit Kombo di Layar (${currentCombo.displayBadge})",
            enabled = !isRunning && root,
            onClick = {
                comboManager.setEditing(currentCombo.id)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            accentColor = HudCyan,
            icon = Icons.Default.TouchApp
        )

        // TouchShare Row: Salin & Tempel Passphrase
        Row(
            Modifier.fillMaxWidth(),
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
                    showImportDialog = true
                },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                icon = Icons.Default.ContentPaste
            )
        }

        // Test Run Button
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

        // Delete Combo Button (if > 1)
        if (combos.size > 1) {
            HudAction(
                label = "Hapus Slot ${currentCombo.displayBadge}",
                enabled = !isRunning,
                onClick = {
                    comboManager.removeCombo(currentCombo.id)
                    selectedIndex = 0
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                isDanger = true
            )
        }

        if (!root) Text(hudString(R.string.hud_cap_needs_root), fontSize = 12.sp, color = HudOrange)
    }

    // TouchShare Import Dialog
    if (showImportDialog) {
        var inputCode by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showImportDialog = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xF216161A),
                border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.5f)),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(hudString(R.string.hud_touchshare_dialog_title), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(hudString(R.string.hud_touchshare_dialog_desc), fontSize = 12.sp, color = HudMuted)

                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it },
                        placeholder = { Text("LYNX-COMBO#1#...", color = HudMuted) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = HudCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showImportDialog = false }) {
                            Text(hudString(R.string.hud_magic_cancel), color = Color(0xFFAAAAAA))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val imported = TouchShareParser.importFromString(inputCode, currentCombo.id)
                                if (imported != null) {
                                    comboManager.updateCombo(imported)
                                    Toast.makeText(context, context.getString(R.string.hud_touchshare_success), Toast.LENGTH_SHORT).show()
                                    showImportDialog = false
                                } else {
                                    Toast.makeText(context, context.getString(R.string.hud_touchshare_invalid), Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HudCyan, contentColor = Color.Black)
                        ) {
                            Text("Terapkan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
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
