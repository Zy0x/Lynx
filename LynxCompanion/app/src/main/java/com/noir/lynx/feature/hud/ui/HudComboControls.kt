package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.feature.hud.addins.HudGamingAddins
import com.noir.lynx.core.locale.LynxText as Text

/** Slots stay reachable at full touch size; execution, editing and sharing have separate pages. */
@Composable
internal fun HudComboControls() {
    val runtime = HudGamingAddins.current ?: return
    val manager = runtime.comboManager
    val combos by manager.combos.collectAsState()
    val running by manager.activeRunning.collectAsState()
    val pkg by manager.foreground.collectAsState()
    val root by runtime.rootAvailable.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val combo = combos.firstOrNull { it.id == selectedId } ?: combos.firstOrNull() ?: return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        combos.forEach { item ->
            HudChoice(item.displayBadge, item.id == combo.id, { selectedId = item.id }, Modifier.widthIn(min = 48.dp))
        }
        if (combos.size < 5) HudAction(hudString(R.string.hud_combo_add), enabled = running == null,
            onClick = { selectedId = manager.addCombo().id }, modifier = Modifier.widthIn(min = 80.dp))
    }
    key(combo.id) {
        HudSubpages(listOf(R.string.hud_detail_controls, R.string.hud_detail_options, R.string.hud_detail_share)) { section ->
            HudSection(combo.name, badge = combo.displayBadge, info = hudString(R.string.hud_magic_limits)) {
                when (section) {
                    0 -> {
                        Text(hudString(R.string.hud_combo_points, combo.taps.size), fontSize = 12.sp, color = HudCyan)
                        if (pkg.isNotBlank()) Text(pkg, fontSize = 12.sp, color = HudMuted)
                        HudAction(hudString(if (combo.taps.isEmpty()) R.string.hud_combo_record else R.string.hud_combo_edit),
                            enabled = root && running == null, onClick = { manager.setEditing(combo.id) }, modifier = Modifier.fillMaxWidth())
                        val currentRunning = running == combo.id
                        HudAction(hudString(if (currentRunning) R.string.hud_magic_stop else R.string.hud_magic_start),
                            enabled = currentRunning || (running == null && root && combo.taps.isNotEmpty()),
                            onClick = { if (currentRunning) manager.stopExecution() else manager.executeCombo(combo.id) },
                            modifier = Modifier.fillMaxWidth(), isDanger = currentRunning)
                        if (!root) Text(hudString(R.string.hud_cap_needs_root), fontSize = 12.sp, color = HudOrange)
                    }
                    1 -> HudComboSettings(combo, running == null, combos.size > 1, manager::updateCombo) {
                        manager.removeCombo(combo.id)
                        selectedId = null
                    }
                    2 -> HudComboShare(combo, running == null, manager::updateCombo)
                }
            }
        }
    }
}
