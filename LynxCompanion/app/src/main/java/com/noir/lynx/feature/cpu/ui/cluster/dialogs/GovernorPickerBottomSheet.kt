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
import com.noir.lynx.core.locale.LynxText as Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernorPickerBottomSheet(
    cluster: CpuClusterInfo,
    onDismiss: () -> Unit,
    onGovChange: (policyId: Int, gov: String) -> Unit
) {
    val clusterAccent = if (cluster.id > 0) AccentOrange else AccentCyan
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
                        text = stringResource(R.string.cpu_cluster_pick_gov),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.cpu_cluster_gov_subtitle, cluster.id, cluster.role),
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
                        text = cluster.curGov,
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cluster.availGovs.forEach { gov ->
                    val isSelected = gov == cluster.curGov
                    val (tag, desc) = when (gov.lowercase()) {
                        "schedutil" -> Pair(
                            stringResource(R.string.cpu_gov_tag_recommended),
                            stringResource(R.string.cpu_gov_schedutil_desc)
                        )
                        "performance" -> Pair(
                            stringResource(R.string.cpu_gov_tag_max_perf),
                            stringResource(R.string.cpu_gov_performance_desc)
                        )
                        "powersave" -> Pair(
                            stringResource(R.string.cpu_gov_tag_extreme_saver),
                            stringResource(R.string.cpu_gov_powersave_desc)
                        )
                        "ondemand" -> Pair(
                            stringResource(R.string.cpu_gov_tag_traditional),
                            stringResource(R.string.cpu_gov_ondemand_desc)
                        )
                        "conservative" -> Pair(
                            stringResource(R.string.cpu_gov_tag_gradual),
                            stringResource(R.string.cpu_gov_conservative_desc)
                        )
                        "userspace" -> Pair(
                            stringResource(R.string.cpu_gov_tag_manual),
                            stringResource(R.string.cpu_gov_userspace_desc)
                        )
                        else -> Pair(
                            stringResource(R.string.cpu_gov_tag_alternative),
                            stringResource(R.string.cpu_gov_other_desc)
                        )
                    }

                    Surface(
                        onClick = {
                            onGovChange(cluster.id, gov)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) clusterAccent.copy(alpha = 0.2f) else Color(0xFF141722),
                        border = BorderStroke(1.dp, if (isSelected) clusterAccent else BorderGlass),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = gov,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) clusterAccent else TextPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) clusterAccent.copy(alpha = 0.2f) else Color(0xFF1C1F2B)
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) clusterAccent else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = desc,
                                    fontSize = 10.5.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = clusterAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
