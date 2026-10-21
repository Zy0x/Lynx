package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
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
        Column(Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = description }
            .pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); drag(amount.y) } },
            verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(3.dp).height(16.dp).background(HudCyan, RoundedCornerShape(2.dp)))
                Text(hudString(R.string.hud_title), color = HudCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Text(appLabel.ifBlank { hudString(R.string.hud_unknown_app) }, fontSize = 12.sp,
                color = HudMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(hudString(R.string.hud_value, battery.text(), battery.unit), fontSize = 12.sp,
            color = HudMuted, fontFamily = FontFamily.Monospace)
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, hudString(R.string.hud_close), Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun HudAppDock(apps: List<HudApp>, onApp: (HudApp) -> Unit) {
    if (apps.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        .padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        apps.forEach { app ->
            Box(Modifier.size(48.dp).clickable(role = Role.Button) { onApp(app) }, contentAlignment = Alignment.Center) {
                Image(app.icon.asImageBitmap(), app.label, Modifier.size(28.dp))
            }
        }
    }
}
