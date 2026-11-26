package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.hud.HudApp
import com.noir.lynx.feature.hud.data.*

/**
 * Modern Bento Dock App Manager with Instant Search, Filter Chips,
 * and frictionless individual window size overrides.
 */
@Composable
internal fun HudAppManager(
    prefs: HudPreferences,
    store: HudPreferencesStore,
    modifier: Modifier = Modifier,
    headerContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val catalog = remember(context) { HudAppCatalog(context) }
    val apps by catalog.apps.collectAsState(initial = emptyList())
    val selected = HudDockPolicy.packages(prefs.dockPackages, apps.map { it.packageName }.toSet())
    val saved = prefs.dockPackages ?: selected

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = Semua, 1 = Terpilih, 2 = Belum Dipilih
    var editingApp by remember { mutableStateOf<String?>(null) }

    // Pre-order: selected apps first, then alphabetical unselected apps
    val orderedApps = remember(apps, selected) {
        val byPackage = apps.associateBy { it.packageName }
        selected.mapNotNull(byPackage::get) + apps.filter { it.packageName !in selected }
    }

    // Filtered by search query & filter chip
    val displayedApps = remember(orderedApps, searchQuery, selectedFilter, selected) {
        val query = searchQuery.trim().lowercase()
        orderedApps.filter { app ->
            val matchesSearch = query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query)
            val matchesFilter = when (selectedFilter) {
                1 -> app.packageName in selected
                2 -> app.packageName !in selected
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (headerContent != null) {
            headerContent()
        }

        // ============================================================
        // 1. TOP DOCK HEADER BENTO CARD (Stats & Description)
        // ============================================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "APLIKASI DOCK TERPILIH",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            letterSpacing = 0.6.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentCyan.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${selected.size} Dipilih",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "Aplikasi dengan tanda centang akan muncul di bilah dock Lynx. Anda dapat meluncurkannya langsung sebagai jendela mengambang saat game aktif.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                // ============================================================
                // 2. SEARCH BAR
                // ============================================================
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Cari nama aplikasi atau package...",
                            fontSize = 12.sp,
                            color = TextSecondary.copy(alpha = 0.6f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = if (searchQuery.isNotEmpty()) AccentCyan else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Hapus pencarian",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        cursorColor = AccentCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                )

                // ============================================================
                // 3. FILTER PILLS (Semua, Terpilih, Belum Dipilih)
                // ============================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filterOptions = listOf(
                        Triple(0, "Semua", apps.size),
                        Triple(1, "Terpilih", selected.size),
                        Triple(2, "Lainnya", (apps.size - selected.size).coerceAtLeast(0))
                    )

                    filterOptions.forEach { (filterId, label, count) ->
                        val isFilterActive = selectedFilter == filterId
                        Surface(
                            onClick = { selectedFilter = filterId },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isFilterActive) AccentCyan.copy(alpha = 0.15f) else BgElevated,
                            border = BorderStroke(
                                0.6.dp,
                                if (isFilterActive) AccentCyan.copy(alpha = 0.6f) else BorderSubtle
                            ),
                            modifier = Modifier.heightIn(min = 34.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isFilterActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isFilterActive) AccentCyan else TextSecondary
                                )
                                if (count > 0) {
                                    Text(
                                        text = "($count)",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isFilterActive) AccentCyan else TextSecondary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ============================================================
        // 4. APPS LIST CONTAINER (Height bounded for comfortable scrolling)
        // ============================================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (apps.isEmpty()) {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Memindai aplikasi launcher Android...",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else if (displayedApps.isEmpty()) {
                // No results state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty())
                                "Tidak ada aplikasi bernama '$searchQuery'"
                            else
                                "Tidak ada aplikasi pada filter ini",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayedApps, key = { it.packageName }) { app ->
                        val isChosen = app.packageName in selected
                        val isEditing = editingApp == app.packageName
                        val customSize = prefs.appSizes[app.packageName]

                        AppItemBentoTile(
                            app = app,
                            isChosen = isChosen,
                            isEditing = isEditing,
                            customSize = customSize,
                            onToggleChosen = { enable ->
                                store.setDockPackages(
                                    saved.toMutableList().apply {
                                        if (enable && app.packageName !in this) add(app.packageName)
                                        else if (!enable) remove(app.packageName)
                                    }
                                )
                            },
                            onToggleEditing = {
                                editingApp = if (isEditing) null else app.packageName
                            },
                            onMoveUp = {
                                val idx = selected.indexOf(app.packageName)
                                if (idx > 0) {
                                    val next = saved.toMutableList()
                                    val from = next.indexOf(app.packageName)
                                    val to = next.indexOf(selected[idx - 1])
                                    java.util.Collections.swap(next, from, to)
                                    store.setDockPackages(next)
                                }
                            },
                            onMoveDown = {
                                val idx = selected.indexOf(app.packageName)
                                if (idx in 0 until selected.lastIndex) {
                                    val next = saved.toMutableList()
                                    val from = next.indexOf(app.packageName)
                                    val to = next.indexOf(selected[idx + 1])
                                    java.util.Collections.swap(next, from, to)
                                    store.setDockPackages(next)
                                }
                            },
                            canMoveUp = selected.indexOf(app.packageName) > 0,
                            canMoveDown = selected.indexOf(app.packageName) in 0 until selected.lastIndex,
                            onCustomSizeChange = { w, h ->
                                store.setAppSize(app.packageName, HudAppSize(w, h))
                            },
                            onClearCustomSize = {
                                store.setAppSize(app.packageName, null)
                            },
                            defaultWidth = prefs.floatingWidthPct,
                            defaultHeight = prefs.floatingHeightPct
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 5. APP ITEM BENTO TILE
// ============================================================
@Composable
private fun AppItemBentoTile(
    app: HudApp,
    isChosen: Boolean,
    isEditing: Boolean,
    customSize: HudAppSize?,
    onToggleChosen: (Boolean) -> Unit,
    onToggleEditing: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onCustomSizeChange: (Int, Int) -> Unit,
    onClearCustomSize: () -> Unit,
    defaultWidth: Int,
    defaultHeight: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isChosen) AccentCyan.copy(alpha = 0.08f) else BgElevated,
        border = BorderStroke(
            0.8.dp,
            if (isChosen) AccentCyan.copy(alpha = 0.45f) else BorderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Checkbox & Label Area (Touch target >= 48dp)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = isChosen,
                            role = Role.Checkbox,
                            onValueChange = onToggleChosen
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(
                        checked = isChosen,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentCyan,
                            checkmarkColor = Color.Black,
                            uncheckedColor = TextSecondary.copy(alpha = 0.6f)
                        )
                    )

                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = app.label,
                                fontSize = 12.5.sp,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                color = if (isChosen) Color.White else Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (customSize != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${customSize.width}×${customSize.height}%",
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = app.packageName,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Settings button to open custom size & ordering
                IconButton(
                    onClick = onToggleEditing,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Tune,
                        contentDescription = "Pengaturan ukuran aplikasi",
                        tint = if (isEditing) AccentCyan else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Editor: Custom App Size & Reordering
            AnimatedVisibility(
                visible = isEditing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)

                    // Reorder Row (If chosen)
                    if (isChosen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Urutan Posisi di Dock",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = onMoveUp,
                                    enabled = canMoveUp,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BgElevated,
                                        contentColor = AccentCyan,
                                        disabledContainerColor = BgElevated.copy(alpha = 0.4f),
                                        disabledContentColor = TextSecondary.copy(alpha = 0.3f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 36.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Naik", modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Naik", fontSize = 10.sp)
                                }

                                Button(
                                    onClick = onMoveDown,
                                    enabled = canMoveDown,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BgElevated,
                                        contentColor = AccentCyan,
                                        disabledContainerColor = BgElevated.copy(alpha = 0.4f),
                                        disabledContentColor = TextSecondary.copy(alpha = 0.3f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 36.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Turun", modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Turun", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Custom Size Toggle
                    val hasCustomSize = customSize != null
                    val curWidth = customSize?.width ?: defaultWidth
                    val curHeight = customSize?.height ?: defaultHeight

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ukuran Khusus Aplikasi Ini",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Switch(
                            checked = hasCustomSize,
                            onCheckedChange = { enable ->
                                if (enable) onCustomSizeChange(curWidth, curHeight)
                                else onClearCustomSize()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentCyan,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = BgElevated
                            ),
                            modifier = Modifier.heightIn(min = 36.dp)
                        )
                    }

                    // Sliders when custom size is enabled
                    if (hasCustomSize) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Lebar Jendela", fontSize = 10.5.sp, color = TextSecondary)
                                Text(
                                    text = "$curWidth%",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                            }
                            Slider(
                                value = curWidth.toFloat(),
                                onValueChange = { onCustomSizeChange(it.toInt(), curHeight) },
                                valueRange = 35f..95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentCyan,
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = BgElevated
                                ),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tinggi Jendela", fontSize = 10.5.sp, color = TextSecondary)
                                Text(
                                    text = "$curHeight%",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                            }
                            Slider(
                                value = curHeight.toFloat(),
                                onValueChange = { onCustomSizeChange(curWidth, it.toInt()) },
                                valueRange = 35f..90f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentCyan,
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = BgElevated
                                ),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
