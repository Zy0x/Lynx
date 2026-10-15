package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noir.lynx.R
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.triggerMountXBoost

/** Displays the SD policy readback; internal storage retains its kernel settings. */
@Composable
fun MountXStorageCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val storage = uiState.mountXStorageInfo
    val context = LocalContext.current
    LynxCard(
        title = stringResource(R.string.storage_policy_title),
        icon = Icons.Default.SdCard,
        accentColor = AccentPurple,
        modifier = modifier
    ) {
        Text(stringResource(R.string.storage_policy_description), color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        if (storage.isDetected) {
            Text(stringResource(R.string.storage_policy_device,
                storage.parentDevice, storage.mountPoint, storage.fsType), color = TextPrimary)
            Text(stringResource(
                if (storage.isOptimized) R.string.storage_policy_conforms else R.string.storage_policy_pending
            ), color = if (storage.isOptimized) AccentGreen else TextSecondary)
            Text(stringResource(R.string.storage_policy_read_ahead, storage.currentReadAheadKb), color = AccentPurple)
            storage.targetReadAheadKb?.let { target ->
                Text(stringResource(R.string.storage_policy_target, target), color = TextSecondary)
            }
            Text(stringResource(R.string.storage_policy_scheduler,
                storage.currentScheduler.ifEmpty { stringResource(R.string.storage_policy_unavailable) }),
                color = TextSecondary)
        } else {
            Text(stringResource(R.string.storage_policy_no_sd), color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.triggerMountXBoost(context, true) },
            enabled = storage.isDetected && storage.targetReadAheadKb != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(stringResource(R.string.storage_policy_apply)) }
        OutlinedButton(
            onClick = { viewModel.triggerMountXBoost(context, false) },
            enabled = storage.isDetected,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(stringResource(R.string.storage_policy_restore)) }
        Text(stringResource(R.string.storage_policy_restore_note), color = TextSecondary)
    }
}
