package com.noir.lynx.feature.cpu.ui.cluster.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.GovernorTunable
import com.noir.lynx.designsystem.theme.*
import java.util.Locale

@Composable
fun TunableEditDialog(
    policyId: Int,
    gov: String,
    tunable: GovernorTunable,
    onDismiss: () -> Unit,
    onApply: (policyId: Int, gov: String, key: String, value: String) -> Unit
) {
    var editValueText by remember { mutableStateOf(tunable.currentValue) }
    val isPerfCluster = policyId > 0
    val clusterAccent = if (isPerfCluster) AccentOrange else AccentCyan

    val cleanTitle = when (tunable.key.lowercase()) {
        "up_rate_limit_us", "rate_limit_us" -> stringResource(R.string.tunable_ramp_up_title)
        "down_rate_limit_us" -> stringResource(R.string.tunable_ramp_down_title)
        "iowait_boost_enable" -> stringResource(R.string.tunable_iowait_title)
        "hispeed_freq" -> stringResource(R.string.tunable_hispeed_freq_title)
        "go_hispeed_load" -> "Go HiSpeed Load"
        "up_threshold" -> stringResource(R.string.tunable_up_threshold_title)
        "down_threshold" -> stringResource(R.string.tunable_down_threshold_title)
        "sampling_rate" -> stringResource(R.string.tunable_sampling_rate_title)
        "timer_rate" -> "Timer Rate"
        "sampling_down_factor" -> stringResource(R.string.tunable_sampling_down_factor_title)
        "min_sample_time" -> stringResource(R.string.tunable_min_sample_time_title)
        else -> tunable.displayName.replace(" Us", "").replace(" Ms", "").replace(" Khz", "").trim()
    }

    val quickSuggestions = when (tunable.key) {
        "up_rate_limit_us", "rate_limit_us" -> if (isPerfCluster) {
            listOf(
                "0" to "0 µs (Instan / Seimbang)",
                "500" to "500 µs",
                "1000" to "1 ms",
                "20000" to "20 ms (Hemat Daya)"
            )
        } else {
            listOf(
                "0" to "0 µs (Responsif)",
                "500" to "500 µs",
                "1000" to "1 ms (Seimbang)",
                "2000" to "2 ms",
                "10000" to "10 ms (Hemat Daya)"
            )
        }
        "down_rate_limit_us" -> if (isPerfCluster) {
            listOf(
                "500" to "0.5 ms (Hemat Daya)",
                "2000" to "2 ms",
                "5000" to "5 ms (Responsif)",
                "10000" to "10 ms (Seimbang)",
                "30000" to "30 ms (OEM)"
            )
        } else {
            listOf(
                "1000" to "1 ms (Hemat Daya)",
                "5000" to "5 ms",
                "10000" to "10 ms (Responsif)",
                "20000" to "20 ms (Seimbang)",
                "30000" to "30 ms (OEM)"
            )
        }
        "iowait_boost_enable" -> listOf(
            "1" to "1 (Aktif)",
            "0" to "0 (Nonaktif)"
        )
        "up_threshold", "go_hispeed_load" -> listOf(
            "70" to "70% (Responsif)",
            "80" to "80% (Seimbang)",
            "85" to "85%",
            "90" to "90% (Hemat)"
        )
        "down_threshold" -> listOf(
            "20" to "20%",
            "30" to "30% (Stabil)",
            "40" to "40%"
        )
        "sampling_down_factor" -> listOf(
            "1" to "1x (Normal)",
            "2" to "2x",
            "4" to "4x (Stabil)",
            "10" to "10x"
        )
        else -> emptyList()
    }

    val parsedUs = editValueText.trim().toLongOrNull()
    val conversionPreview = if (parsedUs != null && (tunable.key.endsWith("_us") || tunable.unit == "µs")) {
        when {
            parsedUs == 0L -> stringResource(R.string.tunable_instant_preview)
            parsedUs >= 1000L && parsedUs % 1000L == 0L -> stringResource(R.string.tunable_preview_ms, parsedUs / 1000, parsedUs)
            parsedUs >= 1000L -> stringResource(R.string.tunable_preview_ms_float, String.format(Locale.US, "%.2f", parsedUs / 1000f), parsedUs)
            else -> stringResource(R.string.tunable_preview_us, parsedUs)
        }
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        titleContentColor = TextPrimary,
        title = {
            Text(
                text = stringResource(R.string.cpu_cluster_edit_param_title, cleanTitle),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.cpu_cluster_sysfs_node, tunable.key),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = editValueText,
                    onValueChange = { editValueText = it },
                    label = {
                        val unitLabel = tunable.unit.ifBlank { stringResource(R.string.cpu_cluster_fallback_num) }
                        Text(stringResource(R.string.cpu_cluster_input_val_label, unitLabel))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = clusterAccent,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    )
                )

                if (conversionPreview != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = clusterAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = conversionPreview,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = clusterAccent
                        )
                    }
                }

                if (quickSuggestions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.cpu_cluster_recommended_quick_options),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickSuggestions.forEach { (valStr, labelStr) ->
                            val isSelected = editValueText.trim() == valStr
                            Surface(
                                onClick = { editValueText = valStr },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) clusterAccent.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(1.dp, if (isSelected) clusterAccent else BorderGlass)
                            ) {
                                Text(
                                    text = labelStr,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) clusterAccent else TextPrimary,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (editValueText.isNotBlank()) {
                    onApply(policyId, gov, tunable.key, editValueText.trim())
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.action_apply), color = clusterAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        }
    )
}
