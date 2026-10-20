package com.noir.lynx.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*

/**
 * ProfilesAutomationScreen — Zen Minimalist Screen for App Automation.
 * Redesigned to eliminate AI cliches, dense cognitive clutter, and horizontal overlap.
 * Focuses on psychological calm, clear status visibility, and robust touch ergonomics.
 */
@Composable
fun ProfilesAutomationScreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") } // "all", "game", "active"

    val filteredRules = remember(uiState.appProfileRules, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        uiState.appProfileRules
            .filter { rule ->
                val matchesSearch = q.isBlank() ||
                    rule.appName.lowercase().contains(q) ||
                    rule.packageName.lowercase().contains(q)
                val matchesFilter = when (selectedFilter) {
                    "game" -> rule.isGame
                    "active" -> rule.enabled
                    else -> true
                }
                matchesSearch && matchesFilter
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = Color(0xFF10141F),
            shape = RoundedCornerShape(18.dp),
            icon = {
                Surface(
                    shape = CircleShape,
                    color = AccentRed.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.35f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Hapus Semua Aturan?",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "Seluruh konfigurasi profil aplikasi (${uiState.appProfileRules.size} aturan) akan dihapus. Anda dapat memindai ulang game kapan saja.",
                    color = TextSecondary.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showClearAllDialog = false
                        viewModel.clearAllAppProfileRules(context)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = AccentRed.copy(alpha = 0.18f),
                    border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.5f)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hapus Semua",
                            color = AccentRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            dismissButton = {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showClearAllDialog = false
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Batal",
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        )
    }

    LaunchedEffect(uiState.appProfileRules) {
        val pkgs = uiState.appProfileRules.take(20).map { it.packageName }
        AppIconLoader.preload(context, pkgs)
    }

    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        flingBehavior = rememberFluidFlingBehavior(),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "auto_top_spacer") {
            Spacer(Modifier.height(2.dp))
        }

        // 1. Zen Master Service Card
        item(key = "auto_hero_service_card") {
            AutomationHeroServiceCard(
                uiState = uiState,
                viewModel = viewModel,
                context = context
            )
        }

        // 2. Section Header: Aturan Aplikasi
        item(key = "auto_section_header") {
            AutomationSectionHeader(
                uiState = uiState,
                showOverflowMenu = showOverflowMenu,
                onOpenOverflowMenu = { showOverflowMenu = true },
                onOverflowMenuDismiss = { showOverflowMenu = false },
                onAddAppClick = onAddAppClick,
                onClearAllClick = { showClearAllDialog = true },
                viewModel = viewModel,
                context = context
            )
        }

        // 3. Search & Filter Bar (Only if rules exist)
        if (uiState.appProfileRules.isNotEmpty()) {
            item(key = "auto_search_filter_bar") {
                AutomationSearchFilterBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedFilter = selectedFilter,
                    onSelectFilter = { selectedFilter = it },
                    totalCount = uiState.appProfileRules.size,
                    gameCount = uiState.appProfileRules.count { it.isGame },
                    activeCount = uiState.appProfileRules.count { it.enabled }
                )
            }
        }

        // 4. App Rules List or Empty State
        if (uiState.appProfileRules.isEmpty()) {
            if (uiState.isAppRulesLoading) {
                item(key = "auto_loading_rules_card") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF10141F),
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Memuat aturan...",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                item(key = "auto_empty_rules_card") {
                    AutomationEmptyRulesCard(
                        uiState = uiState,
                        viewModel = viewModel,
                        context = context,
                        onAddAppClick = onAddAppClick
                    )
                }
            }
        } else if (filteredRules.isEmpty()) {
            item(key = "auto_empty_search_card") {
                AutomationEmptySearchCard(
                    onReset = {
                        searchQuery = ""
                        selectedFilter = "all"
                    }
                )
            }
        } else {
            items(
                items = filteredRules,
                key = { it.packageName },
                contentType = { "app_rule_bento" }
            ) { rule ->
                AppRuleBentoCard(
                    rule = rule,
                    context = context,
                    viewModel = viewModel,
                    onEditRuleClick = onEditRuleClick
                )
            }
        }

        // Bottom Navigation Bar spacer
        item(key = "auto_bottom_nav_spacer") {
            Spacer(modifier = Modifier.height(110.dp).navigationBarsPadding())
        }
    }
}

/**
 * UnifiedAppAutomationCard — Backward-compatible wrapper delegating to ProfilesAutomationScreen.
 */
@Composable
fun UnifiedAppAutomationCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit
) {
    ProfilesAutomationScreen(
        uiState = uiState,
        viewModel = viewModel,
        context = context,
        onAddAppClick = onAddAppClick,
        onEditRuleClick = onEditRuleClick
    )
}
