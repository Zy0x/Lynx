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
