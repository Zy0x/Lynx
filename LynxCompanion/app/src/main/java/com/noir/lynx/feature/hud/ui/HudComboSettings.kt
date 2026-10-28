package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.feature.hud.combo.ExecutionMode
import com.noir.lynx.feature.hud.combo.MacroCombo
import com.noir.lynx.core.locale.LynxText as Text

@Composable
internal fun HudComboSettings(combo: MacroCombo, editable: Boolean, canRemove: Boolean,
    onUpdate: (MacroCombo) -> Unit, onRemove: () -> Unit) {
    var name by rememberSaveable(combo.id, combo.name) { mutableStateOf(combo.name) }
    OutlinedTextField(name, { name = it.take(20) }, label = { Text(hudString(R.string.hud_combo_name)) },
        enabled = editable, modifier = Modifier.fillMaxWidth())
    HudAction(hudString(R.string.hud_combo_save_name), enabled = editable && name.isNotBlank() && name.trim() != combo.name,
        onClick = { onUpdate(combo.copy(name = name.trim())) }, modifier = Modifier.fillMaxWidth())
    AddinToggle(hudString(R.string.hud_combo_floating, combo.displayBadge), combo.enabled, enabled = editable) {
        onUpdate(combo.copy(enabled = it))
    }
    Text(hudString(R.string.hud_combo_execution), fontSize = 12.sp, color = HudMuted)
    val modes = listOf(ExecutionMode.ONE_SHOT, ExecutionMode.HOLD_LOOP)
    val labels = listOf(hudString(R.string.hud_combo_once), hudString(R.string.hud_combo_loop))
    HudAdaptiveGrid(labels, maximum = 2) { index ->
        HudChoice(labels[index], combo.executionMode == modes[index], { onUpdate(combo.copy(executionMode = modes[index])) },
            Modifier.weight(1f), enabled = editable)
    }
    if (combo.executionMode == ExecutionMode.HOLD_LOOP) {
        Text(hudString(R.string.hud_combo_delay, combo.loopDelayMs), fontSize = 12.sp, color = HudCyan)
        Slider(combo.loopDelayMs.toFloat().coerceIn(50f, 1000f), { onUpdate(combo.copy(loopDelayMs = it.toLong())) },
            valueRange = 50f..1000f, enabled = editable, modifier = Modifier.heightIn(min = 48.dp))
    }
    if (canRemove) HudAction(hudString(R.string.hud_combo_delete, combo.displayBadge), enabled = editable,
        onClick = onRemove, modifier = Modifier.fillMaxWidth(), isDanger = true)
}
