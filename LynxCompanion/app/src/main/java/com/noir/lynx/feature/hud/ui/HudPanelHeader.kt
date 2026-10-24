package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudApp
import com.noir.lynx.feature.hud.data.HudReading

@Composable
internal fun HudPanelHeader(appLabel: String, battery: HudReading, onDrag: (Float) -> Unit, onClose: () -> Unit) {
    val drag by rememberUpdatedState(onDrag)
    val description = hudString(R.string.hud_drag)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = description }
            .pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); drag(amount.y) } },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(hudString(R.string.hud_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HudCyan)
            Text(appLabel.ifBlank { "—" }, Modifier.weight(1f), fontSize = 12.sp, color = HudMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(hudString(R.string.hud_value, battery.text(), battery.unit), Modifier.padding(start = 4.dp),
            fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = HudMuted)
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, hudString(R.string.hud_close), Modifier.size(18.dp), tint = HudMuted)
        }
    }
}

@Composable
internal fun HudAppDock(apps: List<HudApp>, onManage: () -> Unit, onApp: (HudApp) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        apps.forEach { app ->
            IconButton(onClick = { onApp(app) }, modifier = Modifier.size(48.dp)) {
                Image(app.icon.asImageBitmap(), app.label, Modifier.size(28.dp))
            }
        }
        IconButton(onClick = onManage, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, hudString(R.string.hud_apps_manage), Modifier.size(18.dp), tint = HudCyan)
        }
    }
}
