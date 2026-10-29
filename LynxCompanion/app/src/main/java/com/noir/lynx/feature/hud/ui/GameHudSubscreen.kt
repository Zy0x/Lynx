package com.noir.lynx.feature.hud.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.feature.hud.data.HudPreferencesStore
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.setHudMode
import com.noir.lynx.ui.setHudStyle
import com.noir.lynx.ui.toggleGameHud

/** Native settings use one focused group instead of stacking every feature below the master switch. */
@Composable
fun GameHudSubscreen(uiState: LynxUiState, viewModel: LynxViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = Settings.canDrawOverlays(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val store = remember(context) { HudPreferencesStore(context) }
    val prefs by store.changes.collectAsState(initial = store.read())
    HudTheme {
        Column(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HudSection(hudString(R.string.hud_settings_title)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(hudString(if (uiState.isGameHudActive) R.string.hud_engine_active_brief else R.string.hud_engine_idle_brief),
                        fontSize = 12.sp, color = if (uiState.isGameHudActive) HudCyan else HudMuted, modifier = Modifier.weight(1f))
                    Switch(uiState.isGameHudActive, { viewModel.toggleGameHud(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = HudBlueBadge, checkedThumbColor = Color.White))
                }
                if (!permission) HudAction(hudString(R.string.hud_permission_action), onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }, modifier = Modifier.fillMaxWidth())
            }
            HudSubpages(listOf(R.string.hud_mode_title, R.string.hud_mini_title, R.string.hud_floating_short, R.string.hud_record_title)) { page ->
                when (page) {
                    0 -> HudSection(hudString(R.string.hud_mode_title)) {
                        val labels = listOf(hudString(R.string.hud_sidebar), hudString(R.string.hud_classic))
                        HudAdaptiveGrid(labels, maximum = 2) { index ->
                            HudChoice(labels[index], uiState.hudMode == index, { viewModel.setHudMode(context, index) }, Modifier.weight(1f))
                        }
                        Text(hudString(if (uiState.hudMode == 0) R.string.hud_sidebar_description else R.string.hud_classic_description),
                            fontSize = 12.sp, color = HudMuted)
                        if (uiState.hudMode == 1) {
                            val styles = listOf(R.string.hud_style_pillar, R.string.hud_style_ribbon, R.string.hud_style_esport,
                                R.string.hud_style_tiles, R.string.hud_style_deck, R.string.hud_style_ghost).map { hudString(it) }
                            HudAdaptiveGrid(styles, maximum = 2) { index ->
                                HudChoice(styles[index], uiState.hudStyle == index + 1,
                                    { viewModel.setHudStyle(context, index + 1) }, Modifier.weight(1f))
                            }
                        }
                    }
                    1 -> HudMetricSelection(prefs, store)
                    2 -> HudFloatingSettings(prefs, store)
                    3 -> HudRecorderMasterCard(context)
                }
            }
        }
    }
}
