package com.noir.lynx.feature.media.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noir.lynx.R
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.media.MediaDecoderViewModel
import com.noir.lynx.feature.media.data.MediaDecoderRepository

@StringRes
private fun modeTitle(mode: String): Int = when (mode) {
    "omx_compat" -> R.string.media_mode_omx
    "prefer_hardware" -> R.string.media_mode_hardware
    "prefer_software" -> R.string.media_mode_software
    else -> R.string.media_mode_system
}

@StringRes
private fun modeDescription(mode: String): Int = when (mode) {
    "omx_compat" -> R.string.media_desc_omx
    "prefer_hardware" -> R.string.media_desc_hardware
    "prefer_software" -> R.string.media_desc_software
    else -> R.string.media_desc_system
}

/** Global codec preferences are separate from GPU clock and rendering controls. */
@Composable
fun MediaDecoderCard(controller: MediaDecoderViewModel = viewModel()) {
    val state by controller.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LynxCard(title = stringResource(R.string.media_decoder_title), icon = Icons.Default.VideoSettings, accentColor = AccentCyan) {
        Text(stringResource(R.string.media_decoder_description), color = TextSecondary)
        if (!state.status.available) Text(stringResource(R.string.media_native_unavailable), color = AccentOrange)
        if (state.status.isCodecAsymmetric) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = AccentCyan.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Rekomendasi Cerdas: Asimetri Codec Terdeteksi",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = AccentCyan
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Hardware video decoding pada ROM/perangkat ini hanya tersedia via OpenMAX (OMX), sedangkan Codec2 hanya menyediakan decoder software. Mengaktifkan 'Kompatibilitas OMX' sangat direkomendasikan agar video 1080p/2K/4K bebas glitch dan hemat CPU.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }
        }
        MediaDecoderRepository.modes.forEach { mode ->
            val enabled = state.status.available && mode in state.status.supportedModes && !state.busy
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = state.selectedMode == mode,
                enabled = enabled, role = Role.RadioButton, onClick = { controller.select(mode) })
                .padding(vertical = 6.dp)) {
                RadioButton(selected = state.selectedMode == mode, onClick = null, enabled = enabled)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    val isRecommended = mode == "omx_compat" && state.status.isCodecAsymmetric
                    Text(
                        text = if (isRecommended) stringResource(modeTitle(mode)) + " (Direkomendasikan)" else stringResource(modeTitle(mode)),
                        color = if (isRecommended) AccentCyan else if (enabled) TextPrimary else TextSecondary,
                        fontWeight = if (isRecommended) androidx.compose.ui.text.font.FontWeight.Bold else null
                    )
                    Text(stringResource(modeDescription(mode)), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    if (mode !in state.status.supportedModes) Text(stringResource(R.string.media_mode_unavailable), color = AccentOrange)
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = state.applyOnBoot,
            enabled = state.status.available && state.selectedMode != "system" && !state.busy,
            role = Role.Switch, onValueChange = controller::setBoot)) {
            Text(stringResource(R.string.media_apply_boot), modifier = Modifier.weight(1f), color = TextPrimary)
            Switch(checked = state.applyOnBoot, onCheckedChange = null,
                enabled = state.status.available && state.selectedMode != "system" && !state.busy)
        }
        Text(stringResource(R.string.media_boot_note), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        if (state.status.available) Text(stringResource(if (state.status.sessionPropertyVerified)
            R.string.media_properties_verified else R.string.media_properties_unmanaged), color = TextSecondary)
        if (state.status.pendingRestart) Text(stringResource(R.string.media_pending_restart), color = AccentOrange)
        if (state.applied) Text(stringResource(R.string.media_applied), color = AccentGreen)
        state.error?.let { Text(stringResource(R.string.media_action_failed, it), color = AccentOrange) }
        Button(onClick = { confirm = true }, enabled = state.status.available && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (state.busy) R.string.media_busy else R.string.media_apply))
        }
        TextButton(onClick = controller::refresh, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.media_refresh))
        }
        Text(stringResource(R.string.media_active_decoders), color = TextPrimary)
        if (state.status.activeDecoders.isEmpty()) Text(stringResource(R.string.media_no_active), color = TextSecondary)
        else state.status.activeDecoders.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = AccentCyan) }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
            val count = state.status.registeredDecoders.size
            Text(pluralStringResource(R.plurals.media_registered_count, count, count))
        }
        if (expanded) {
            Text(stringResource(R.string.media_registry_note), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            state.status.registeredDecoders.forEach { decoder ->
                val kind = when (decoder.kind) { "hardware" -> R.string.media_kind_hardware; "software" -> R.string.media_kind_software; else -> R.string.media_kind_vendor }
                Text(stringResource(R.string.media_decoder_entry, decoder.name, decoder.mime, stringResource(kind)),
                    style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.media_confirm_title)) },
        text = { Text(stringResource(R.string.media_confirm_message)) },
        confirmButton = { TextButton(onClick = { confirm = false; controller.apply() }) { Text(stringResource(R.string.media_confirm_apply)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.media_cancel)) } }
    )
}
