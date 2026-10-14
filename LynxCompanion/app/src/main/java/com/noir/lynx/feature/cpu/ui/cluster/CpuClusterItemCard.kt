package com.noir.lynx.feature.cpu.ui.cluster

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.CpuClusterInfo
import com.noir.lynx.designsystem.theme.*

@Composable
fun CpuClusterItemCard(
    cluster: CpuClusterInfo,
    onFreqClick: (cluster: CpuClusterInfo, isMin: Boolean) -> Unit,
    onLockToggle: (policyId: Int, isLock: Boolean, minFreq: Long, maxFreq: Long) -> Unit,
    onGovClick: (cluster: CpuClusterInfo) -> Unit,
    onTunablesClick: (cluster: CpuClusterInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPerfCluster = cluster.id > 0
    val clusterAccent = if (isPerfCluster) AccentOrange else AccentCyan
    val minMhz = (cluster.curMin / 1000).toInt()
    val maxMhz = (cluster.curMax / 1000).toInt()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.22f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. Cluster Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = clusterAccent.copy(alpha = 0.16f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${cluster.id}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = clusterAccent
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.cpu_cluster_policy_title, cluster.id, cluster.role),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.cpu_cluster_cores, cluster.cpus),
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Dual Pill Dropdown Selector + Compact Lock Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Min Frequency Pill
                Surface(
                    onClick = { onFreqClick(cluster, true) },
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.cpu_cluster_min_freq),
                                fontSize = 9.5.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "$minMhz MHz",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = clusterAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Max Frequency Pill
                Surface(
                    onClick = { onFreqClick(cluster, false) },
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.cpu_cluster_max_freq),
                                fontSize = 9.5.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "$maxMhz MHz",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = clusterAccent
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = clusterAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Lock Action Button
                val isLocked = cluster.isLocked
                val lockColor = if (isLocked) AccentGreen else TextSecondary
                val lockBg = if (isLocked) AccentGreen.copy(alpha = 0.16f) else BgSurfaceLowest
                val lockBorder = if (isLocked) AccentGreen.copy(alpha = 0.5f) else BorderGlass

                Surface(
                    onClick = {
                        onLockToggle(cluster.id, !isLocked, cluster.curMin, cluster.curMax)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = lockBg,
                    border = BorderStroke(1.dp, lockBorder),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isLocked) {
                                stringResource(R.string.cpu_cluster_locked)
                            } else {
                                stringResource(R.string.cpu_cluster_unlocked)
                            },
                            tint = lockColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Governor Pill & Quick Tunables Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Governor Selector Pill
                Surface(
                    onClick = { onGovClick(cluster) },
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = clusterAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.cpu_cluster_governor),
                                    fontSize = 9.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = cluster.curGov,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Quick Tunables Button
                Surface(
                    onClick = { onTunablesClick(cluster) },
                    shape = RoundedCornerShape(10.dp),
                    color = clusterAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = clusterAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.cpu_cluster_tunables),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = clusterAccent
                        )
                    }
                }
            }
        }
    }
}
