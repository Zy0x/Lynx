package com.noir.lynx.feature.cpu.ui.cluster.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.CpuClusterInfo
import com.noir.lynx.designsystem.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequencyPickerBottomSheet(
    cluster: CpuClusterInfo,
    isMinPicker: Boolean,
    onDismiss: () -> Unit,
    onFreqChange: (policyId: Int, minFreq: Long?, maxFreq: Long?) -> Unit
) {
    val clusterAccent = if (cluster.id > 0) AccentOrange else AccentCyan
    val freqs = cluster.availFreqs.sorted()
    val curFreq = if (isMinPicker) cluster.curMin else cluster.curMax
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFA0D1017),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = Color(0x33FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isMinPicker) {
                            stringResource(R.string.cpu_cluster_pick_min_limit)
                        } else {
                            stringResource(R.string.cpu_cluster_pick_max_limit)
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.cpu_cluster_policy_subtitle, cluster.id, cluster.role, cluster.cpus),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = clusterAccent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "${curFreq / 1000} MHz",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = clusterAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Stepped frequencies grid (2 columns)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                freqs.chunked(2).forEach { rowFreqs ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowFreqs.forEach { f ->
                            val mhz = (f / 1000).toInt()
                            val isSelected = f == curFreq || (curFreq > 0 && f / 1000 == curFreq / 1000)
                            val isAutoAdjust = if (isMinPicker) f > cluster.curMax else f < cluster.curMin

                            Surface(
                                onClick = {
                                    if (isMinPicker) {
                                        val newMax = if (f > cluster.curMax) f else cluster.curMax
                                        onFreqChange(cluster.id, f, newMax)
                                    } else {
                                        val newMin = if (f < cluster.curMin) f else cluster.curMin
                                        onFreqChange(cluster.id, newMin, f)
                                    }
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isSelected -> clusterAccent.copy(alpha = 0.22f)
                                    isAutoAdjust -> Color(0xFF1C1914)
                                    else -> Color(0xFF141722)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected -> clusterAccent
                                        isAutoAdjust -> Color(0x66E5A93C)
                                        else -> BorderGlass
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (mhz >= 1000) {
                                                String.format(Locale.US, "%.2f GHz", mhz / 1000f)
                                            } else {
                                                "$mhz MHz"
                                            },
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isSelected -> clusterAccent
                                                isAutoAdjust -> Color(0xFFE5C158)
                                                else -> TextPrimary
                                            }
                                        )
                                        Text(
                                            text = "$mhz MHz",
                                            fontSize = 9.sp,
                                            color = if (isAutoAdjust) Color(0xFF998855) else TextSecondary
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = clusterAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else if (isAutoAdjust) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0x33E5A93C)
                                        ) {
                                            Text(
                                                text = if (isMinPicker) {
                                                    stringResource(R.string.cpu_cluster_auto_max)
                                                } else {
                                                    stringResource(R.string.cpu_cluster_auto_min)
                                                },
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE5C158),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (rowFreqs.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
