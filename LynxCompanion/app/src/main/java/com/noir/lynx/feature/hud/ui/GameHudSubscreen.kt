package com.noir.lynx.feature.hud.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.*

/** Native HUD controls share persisted metric selection with the running sidebar. */
@Composable
fun GameHudSubscreen(uiState: LynxUiState, viewModel: LynxViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    HudTheme {
        Column(modifier.fillMaxWidth().padding(horizontal = 16.dp,vertical = 8.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!Settings.canDrawOverlays(context)) Surface(color = HudCard,shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(hudString(R.string.hud_permission),fontSize = 14.sp)
                    HudAction(hudString(R.string.hud_permission_action),onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}")))
                    })
                }
            }
            Surface(color = HudCard,shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 48.dp),verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(hudString(R.string.hud_settings_title),fontSize = 18.sp)
                        Text(hudString(if (uiState.isGameHudActive) R.string.hud_on else R.string.hud_off),color = HudMuted,fontSize = 12.sp)
                    }
                    Switch(uiState.isGameHudActive,{ viewModel.toggleGameHud(context,it) })
                }
            }
            Surface(color = HudCard,shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(hudString(R.string.hud_mode_title),fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.hud_sidebar,R.string.hud_classic).forEachIndexed { index,label ->
                            FilterChip(selected = uiState.hudMode == index,onClick = { viewModel.setHudMode(context,index) },
                                label = { Text(hudString(label),fontSize = 12.sp) },modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    if (uiState.hudMode == 1) {
                        Text(hudString(R.string.hud_style),fontSize = 14.sp)
                        val styles = listOf(R.string.hud_style_pillar,R.string.hud_style_ribbon,R.string.hud_style_esport,R.string.hud_style_tiles,R.string.hud_style_deck,R.string.hud_style_ghost)
                        styles.withIndex().toList().chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { (index,label) ->
                                    FilterChip(selected = uiState.hudStyle == index+1,onClick = { viewModel.setHudStyle(context,index+1) },
                                        label = { Text(hudString(label),fontSize = 12.sp) },modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                                }
                            }
                        }
                    }
                }
            }
            HudMiniSettings()
        }
    }
}
