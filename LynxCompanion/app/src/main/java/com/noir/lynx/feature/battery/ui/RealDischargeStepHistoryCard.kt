package com.noir.lynx.feature.battery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.BatteryInfoStats
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*

@Composable
fun RealDischargeStepHistoryCard(
    stats: BatteryInfoStats,
    currentLevel: Int,
    modifier: Modifier = Modifier
) {
    val points = stats.drainHistoryPoints
    val steps = stats.dischargeSteps
    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }
    val historySeries = remember(points, steps) { BatteryHistorySeries(points, steps) }
    val onScrubbedIndexChange = remember { { index: Int? -> scrubbedIndex = index } }
    var historyTab by remember { mutableIntStateOf(0) }
    var isStepsExpanded by remember { mutableStateOf(false) }
    var isAppsExpanded by remember { mutableStateOf(false) }

    LynxCard(
        title = if (historyTab == 0) stringResource(R.string.battery_drain_history_time) else stringResource(R.string.battery_app_drain),
        icon = if (historyTab == 0) Icons.Default.Timeline else Icons.Default.Apps,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        BatteryHistoryTabSelector(
            selectedTab = historyTab,
            onSelectTab = { historyTab = it }
        )
        if (historyTab == 0) {
            BatteryHistoryTimePanel(
                historySeries = historySeries,
                currentLevel = currentLevel,
                scrubbedIndex = scrubbedIndex,
                onScrubbedIndexChange = onScrubbedIndexChange,
                isStepsExpanded = isStepsExpanded,
                onToggleStepsExpanded = { isStepsExpanded = it }
            )
        } else {
            BatteryHistoryAppPanel(
                stats = stats,
                isAppsExpanded = isAppsExpanded,
                onToggleAppsExpanded = { isAppsExpanded = it }
            )
        }
    }
}
