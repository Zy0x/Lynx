package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import kotlinx.coroutines.launch

internal val LocalHudPageScroll = compositionLocalOf<ScrollState?> { null }

/** Only one detail group is composed at a time; navigation never invokes a device action. */
@Composable
internal fun HudSubpages(labels: List<Int>, content: @Composable (Int) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val scroll = LocalHudPageScroll.current
    val scope = rememberCoroutineScope()
    val state = rememberSaveableStateHolder()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, label ->
                Box(
                    Modifier.widthIn(min = 80.dp).heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected == index) HudCyan.copy(alpha = .14f) else HudElevated)
                    .selectable(selected == index, role = Role.Tab, onClick = {
                        selected = index
                        scope.launch { scroll?.scrollTo(0) }
                    }).padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(hudString(label), fontSize = 12.sp, color = if (selected == index) Color.White else HudMuted)
                }
            }
        }
        state.SaveableStateProvider(selected) { content(selected) }
    }
}
