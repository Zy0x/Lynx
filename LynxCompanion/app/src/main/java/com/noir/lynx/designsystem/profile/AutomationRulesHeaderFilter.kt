package com.noir.lynx.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*

/**
 * AutomationSectionHeader — Clean, Proportional Header for App Rules.
 * Prevents horizontal squeezing and button collision on narrow displays.
 */
@Composable
fun AutomationSectionHeader(
    uiState: LynxUiState,
    showOverflowMenu: Boolean,
    onOpenOverflowMenu: () -> Unit,
    onOverflowMenuDismiss: () -> Unit,
    onAddAppClick: () -> Unit,
    onClearAllClick: () -> Unit,
    viewModel: LynxViewModel,
    context: Context,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title & Count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = "Aturan Aplikasi",
                color = TextSecondary.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = Color(0xFF141824),
                border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
            ) {
                Text(
                    text = "${uiState.appProfileRules.size}",
                    color = TextSecondary.copy(alpha = 0.75f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                )
            }
        }

        // Action Group
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pindai Button
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.autoDetectAndSyncInstalledRules(context)
                },
                enabled = !uiState.isScanningInstalledGames,
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF141824),
                border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isScanningInstalledGames) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = AccentCyan,
                            strokeWidth = 1.8.dp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Pindai...",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Pindai",
                            color = TextSecondary.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Tambah Button
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAddAppClick()
                },
                shape = RoundedCornerShape(8.dp),
                color = AccentCyan.copy(alpha = 0.14f),
                border = BorderStroke(0.7.dp, AccentCyan.copy(alpha = 0.45f)),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Tambah",
                        color = AccentCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Overflow Menu Button (⋮)
            Box {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenOverflowMenu()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF141824),
                    border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opsi Lainnya",
                            tint = TextSecondary.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(
                        surface = Color(0xFF141824),
                        surfaceContainer = Color(0xFF141824),
                        surfaceContainerHigh = Color(0xFF141824)
                    )
                ) {
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = onOverflowMenuDismiss,
                        modifier = Modifier
                            .background(Color(0xFF141824), RoundedCornerShape(12.dp))
                            .border(0.8.dp, BorderGlass.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .padding(vertical = 4.dp)
                            .widthIn(min = 190.dp)
                    ) {
                        Surface(
                            onClick = {
                                onOverflowMenuDismiss()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.autoDetectAndSyncInstalledRules(context)
                            },
                            enabled = !uiState.isScanningInstalledGames,
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Pindai Ulang Seluruh Game",
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (uiState.appProfileRules.isNotEmpty()) {
                            HorizontalDivider(
                                color = Color(0x18FFFFFF),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                            Surface(
                                onClick = {
                                    onOverflowMenuDismiss()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onClearAllClick()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = AccentRed,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Hapus Semua Aturan",
                                        color = AccentRed,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * AutomationSearchFilterBar — Clean Search & Segmented Filter Chips.
 */
@Composable
fun AutomationSearchFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    totalCount: Int,
    gameCount: Int,
    activeCount: Int,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Search Input (Height 38dp)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF10141E),
            border = BorderStroke(
                0.6.dp,
                if (searchQuery.isNotEmpty()) AccentCyan.copy(alpha = 0.5f) else Color(0x16FFFFFF)
            ),
            modifier = Modifier.fillMaxWidth().height(38.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (searchQuery.isNotEmpty()) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Cari aplikasi...",
                            color = TextSecondary.copy(alpha = 0.45f),
                            fontSize = 12.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Default
                        ),
                        cursorBrush = SolidColor(AccentCyan),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSearchQueryChange("")
                        },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus pencarian",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Filter Chips (Height 28dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filterOptions = listOf(
                Triple("all", "Semua", totalCount),
                Triple("game", "Game", gameCount),
                Triple("active", "Aktif", activeCount)
            )
            filterOptions.forEach { (filterKey, label, count) ->
                val isSelected = selectedFilter == filterKey
                val animBg by animateColorAsState(
                    targetValue = if (isSelected) AccentCyan.copy(alpha = 0.16f) else Color(0xFF10141E),
                    animationSpec = tween(150),
                    label = "filter_bg_$filterKey"
                )
                val animBorder by animateColorAsState(
                    targetValue = if (isSelected) AccentCyan.copy(alpha = 0.50f) else Color(0x16FFFFFF),
                    animationSpec = tween(150),
                    label = "filter_border_$filterKey"
                )

                Surface(
                    onClick = {
                        if (!isSelected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectFilter(filterKey)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = animBg,
                    border = BorderStroke(0.6.dp, animBorder),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "$count",
                            color = if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * AutomationEmptyRulesCard — Tranquil Empty State.
 */
@Composable
fun AutomationEmptyRulesCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(0.6.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = AccentCyan.copy(alpha = 0.10f),
                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.3f)),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Belum Ada Aturan Aplikasi",
                color = TextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tambahkan aplikasi atau pindai game untuk mengatur profil performa khusus.",
                color = TextSecondary.copy(alpha = 0.75f),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.autoDetectAndSyncInstalledRules(context)
                    },
                    enabled = !uiState.isScanningInstalledGames,
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.16f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.45f)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isScanningInstalledGames) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = AccentCyan,
                                strokeWidth = 1.8.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Memindai...",
                                color = AccentCyan,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Pindai Game",
                                color = AccentCyan,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAddAppClick()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Tambah Manual",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * AutomationEmptySearchCard — Empty state when search returns zero matches.
 */
@Composable
fun AutomationEmptySearchCard(
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(0.6.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tidak Ada Hasil",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Coba kata kunci pencarian atau filter lain.",
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onReset()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Reset Pencarian",
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
