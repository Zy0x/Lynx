package com.noir.lynx.feature.hud.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.noir.lynx.ui.toggleGameHud
import com.noir.lynx.ui.setHudMode
import com.noir.lynx.ui.setHudStyle

/** Compact HUD settings, with advanced choices disclosed without resetting stored preferences. */
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
        Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudSection(hudString(R.string.hud_settings_title)) {
                HudPreferenceToggle(hudString(if (uiState.isGameHudActive) R.string.hud_on else R.string.hud_off), uiState.isGameHudActive) {
                    viewModel.toggleGameHud(context, it)
                }
                if (!permission) {
                    Text(hudString(R.string.hud_permission), fontSize = 12.sp, color = HudOrange)
                    HudAction(hudString(R.string.hud_permission_action), onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
            HudExpandableSection(hudString(R.string.hud_mode_title), hudString(if (uiState.hudMode == 0) R.string.hud_sidebar else R.string.hud_classic)) {
                val modes = listOf(hudString(R.string.hud_sidebar), hudString(R.string.hud_classic))
                HudAdaptiveGrid(modes, maximum = 2) { index ->
                    HudChoice(modes[index], uiState.hudMode == index, { viewModel.setHudMode(context, index) }, Modifier.weight(1f))
                }
                if (uiState.hudMode == 1) {
                    val styles = listOf(R.string.hud_style_pillar, R.string.hud_style_ribbon, R.string.hud_style_esport,
                        R.string.hud_style_tiles, R.string.hud_style_deck, R.string.hud_style_ghost).map { hudString(it) }
                    HudAdaptiveGrid(styles, maximum = 2) { index ->
                        HudChoice(styles[index], uiState.hudStyle == index + 1, { viewModel.setHudStyle(context, index + 1) }, Modifier.weight(1f))
                    }
                }
            }
            HudExpandableSection(hudString(R.string.hud_floating_short), hudString(when {
                !prefs.floatingEnabled -> R.string.hud_off
                prefs.floatingEngine == "standalone" -> R.string.floating_engine_root
                else -> R.string.floating_engine_rom
            })) { HudFloatingSettings(prefs, store) }
            HudExpandableSection(hudString(R.string.hud_mini_title), hudString(R.string.hud_selected_count, prefs.selected.size)) {
                HudMetricSelection(prefs, store)
            }
        }
    }
}
