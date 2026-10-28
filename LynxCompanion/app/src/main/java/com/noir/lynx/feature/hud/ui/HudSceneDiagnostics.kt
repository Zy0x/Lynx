package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.noir.lynx.feature.hud.autoskip.GenshinSkipEngine

@Composable
internal fun HudSceneDiagnostics(
    enabled: Boolean, isPaused: Boolean, isTappingLive: Boolean,
    onSnapshotScene: () -> Unit, sceneResult: GenshinSkipEngine.SceneSnapshotResult?
) {
    HudExpandableSection(
        title = hudString(R.string.hud_tool_scene_title),
        detail = hudString(if (isPaused) R.string.hud_skip_paused else if (isTappingLive) R.string.hud_skip_running else R.string.hud_skip_standby),
        initiallyOpen = false,
        info = hudString(R.string.hud_tool_scene_help)
    ) {
        if (enabled) {
            Spacer(modifier = Modifier.height(6.dp))
            HudAction(
                label = hudString(R.string.hud_tool_scene_capture),
                onClick = onSnapshotScene,
                modifier = Modifier.fillMaxWidth()
            )

            if (sceneResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = HudBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isDialog = sceneResult.telemetry.dialogueDeclared
                        val verdictBg = if (isDialog) Color(0x2A43E5C1) else Color(0x2AFBBF24)
                        val verdictBorder = if (isDialog) HudCyan.copy(alpha = 0.5f) else HudOrange.copy(alpha = 0.5f)
                        val verdictColor = if (isDialog) HudCyan else HudOrange
                        Surface(
                            color = verdictBg,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.75.dp, verdictBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isDialog) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = verdictColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isDialog) hudString(R.string.hud_autoskip_scene_detected)
                                           else hudString(R.string.hud_autoskip_scene_non_dialog),
                                    color = verdictColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = hudString(
                                R.string.hud_autoskip_scene_pixels,
                                sceneResult.telemetry.matchingPixels,
                                sceneResult.telemetry.thresholdRequired
                            ),
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp
                        )
                        Text(
                            text = hudString(
                                R.string.hud_autoskip_scene_zone,
                                sceneResult.telemetry.xMin,
                                sceneResult.telemetry.xMax,
                                sceneResult.telemetry.yRow
                            ),
                            color = HudMuted,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "${sceneResult.baseName}_full.png",
                            color = HudMuted.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

}
