package com.noir.lynx.feature.battery.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.BatteryDetails
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentGreen
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.AccentRed
import com.noir.lynx.designsystem.theme.BgCard
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary

/**
 * BatteryDetailBottomSheet — Modal bottom sheet inspecting raw charger registers & thermal zone matrix with i18n support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryDetailBottomSheet(
    battDetails: BatteryDetails,
    onDismiss: () -> Unit
) {
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
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.battery_raw_telemetry_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = TextSecondary
                    )
                }
            }

            // Section 0: Hardware Ground Truth & Anti-Spoof
            Text(androidx.compose.ui.res.stringResource(com.noir.lynx.R.string.audit_soc_ground_truth), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentGreen)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, if (battDetails.isPropsSpoofed) AccentOrange.copy(alpha = 0.5f) else BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.noir.lynx.R.string.audit_physical_platform), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text(battDetails.hardwarePlatform.ifBlank { "Generic Linux" }, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGreen, fontFamily = FontFamily.Monospace)
                    }
                    if (battDetails.isPropsSpoofed) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.noir.lynx.R.string.audit_spoofer_status), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = AccentOrange)
                            Text(androidx.compose.ui.res.stringResource(com.noir.lynx.R.string.audit_spoofer_active), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentOrange, fontFamily = FontFamily.Monospace)
                        }
                        if (battDetails.spoofDetails.isNotBlank()) {
                            Text(battDetails.spoofDetails, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = TextSecondary)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.noir.lynx.R.string.audit_brand_integrity), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                            Text(androidx.compose.ui.res.stringResource(com.noir.lynx.R.string.audit_brand_original), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Section 1: Raw ADC Registers
            Text("REGISTER CHARGER & ADC PHYSICAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentCyan)
            if (battDetails.rawAdcDetails.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        battDetails.rawAdcDetails.forEach { (k, v) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(k, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                                Text(v, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            } else {
                Text("Tidak ada register ADC terdeteksi.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }

            // Section 2: Thermal Zone Matrix
            Text("MATRIKS SENSOR THERMAL ZONE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentOrange)
            if (battDetails.thermalZoneMatrix.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        battDetails.thermalZoneMatrix.forEach { (name, temp) ->
                            val tColor = if (temp >= 46f) AccentRed else if (temp >= 42f) AccentOrange else AccentGreen
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                                Text(String.format(java.util.Locale.US, "%.1f°C", temp), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tColor)
                            }
                        }
                    }
                }
            } else {
                Text("Tidak ada data thermal zone.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
