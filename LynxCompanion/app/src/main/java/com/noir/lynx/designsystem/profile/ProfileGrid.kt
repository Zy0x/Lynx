package com.noir.lynx.designsystem.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.profiles.KernelPerformanceProfile
import com.noir.lynx.profiles.KernelProfileCatalog
import com.noir.lynx.profiles.ProfileDomain

@Composable
fun ProfileGrid(
    catalog: KernelProfileCatalog,
    currentProfile: String,
    hasOverrides: Boolean,
    onProfileSelected: (String, Boolean) -> Unit,
    onExtremeConfirmRequired: () -> Unit,
    onProfileSaved: (KernelPerformanceProfile, Boolean, Boolean) -> Unit,
    onProfileDeleted: (String) -> Unit,
    onProfileDuplicated: (String) -> Unit,
    onRestoreBuiltIn: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<KernelPerformanceProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<String?>(null) }
    var pendingExtremeProfile by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val profiles = catalog.profiles

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        profiles.forEach { profile ->
            val item = profileItem(profile)
            val isSelected = profile.id == currentProfile
            val targetBg = if (isSelected) item.color.copy(alpha = 0.12f) else BgElevated
            val targetBorder = if (isSelected) item.color.copy(alpha = 0.70f) else BorderSubtle
            val animatedBg by animateColorAsState(targetBg, animationSpec = tween(180), label = "profile_grid_bg_${profile.id}")
            val animatedBorder by animateColorAsState(targetBorder, animationSpec = tween(180), label = "profile_grid_border_${profile.id}")
            var cardMenuExpanded by remember { mutableStateOf(false) }

            val defaultProf = KernelPerformanceProfile.builtIns().firstOrNull { it.id == profile.id }
            val isModified = profile.builtIn && defaultProf != null && (profile.baseProfile != defaultProf.baseProfile || profile.domainProfiles != defaultProf.domainProfiles || profile.settings.isNotEmpty() || profile.includedSensitiveSettings.isNotEmpty())

            val shortDesc = when (profile.id.lowercase()) {
                "auto" -> "Adaptif otomatis • Seimbang di app biasa, boost saat gaming"
                "powersave" -> "Hemat daya baterai • Dibatasi 60 FPS & downclock siaga"
                "balance" -> "Ideal harian • Responsif, dingin & efisiensi daya seimbang"
                "performance" -> "Fokus gaming • Clock tinggi, devfreq cepat & respons instan"
                "extreme" -> "Performa tanpa batas • Bypass termal penuh (wajib pendingin)"
                else -> profile.description.ifBlank { "Profil turunan ${profile.baseProfile}" }
            }

            val tags = when (profile.id.lowercase()) {
                "auto" -> listOf("AI Dynamic", "Adaptive", "Zero-Lag")
                "powersave" -> listOf("Maks 60 FPS", "Idle Clock", "Hemat Daya")
                "balance" -> listOf("Schedutil", "Dynamic DVFS", "Sejuk & Stabil")
                "performance" -> listOf("Devfreq Boost", "High Clock", "Gaming")
                "extreme" -> listOf("Bypass Termal", "Maks OPP", "Performa Puncak")
                else -> listOf(
                    "CPU: ${profile.domainProfiles[ProfileDomain.CPU] ?: profile.baseProfile}",
                    "GPU: ${profile.domainProfiles[ProfileDomain.GPU] ?: "balanced"}"
                )
            }

            Surface(
                onClick = {
                    if (profile.id == "extreme" && currentProfile != profile.id) onExtremeConfirmRequired()
                    else if (profile.baseProfile == "extreme" && currentProfile != profile.id) pendingExtremeProfile = profile.id
                    else if (currentProfile != profile.id) {
                        if (hasOverrides) pendingSelection = profile.id else onProfileSelected(profile.id, true)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = animatedBg,
                border = BorderStroke(if (isSelected) 1.2.dp else 0.8.dp, animatedBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Squircle Icon Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = item.color.copy(alpha = 0.16f),
                        border = BorderStroke(0.8.dp, item.color.copy(alpha = 0.35f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = item.color,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // 2. Middle Content (Name, Badges, Subtitle, Tags)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.name,
                                color = if (isSelected) item.color else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                            )

                            if (isModified) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentOrange.copy(alpha = 0.16f),
                                    border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "Tersuai",
                                        color = AccentOrange,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            if (!profile.builtIn) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentCyan.copy(alpha = 0.16f),
                                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "Kustom",
                                        color = AccentCyan,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(2.5.dp))

                        Text(
                            text = shortDesc,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                        )

                        Spacer(Modifier.height(5.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BgDeepOled.copy(alpha = 0.6f),
                                    border = BorderStroke(0.5.dp, BorderSubtle)
                                ) {
                                    Text(
                                        text = tag,
                                        color = TextSecondary.copy(alpha = 0.85f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // 3. Right Status & Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (isSelected) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = item.color.copy(alpha = 0.18f),
                                border = BorderStroke(0.8.dp, item.color.copy(alpha = 0.65f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = item.color,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "AKTIF",
                                        color = item.color,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { cardMenuExpanded = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Opsi",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            MaterialTheme(
                                colorScheme = MaterialTheme.colorScheme.copy(
                                    surface = Color(0xFF141722),
                                    onSurface = TextPrimary
                                ),
                                shapes = MaterialTheme.shapes.copy(
                                    extraSmall = RoundedCornerShape(16.dp),
                                    small = RoundedCornerShape(16.dp),
                                    medium = RoundedCornerShape(16.dp)
                                )
                            ) {
                                DropdownMenu(
                                    expanded = cardMenuExpanded,
                                    onDismissRequest = { cardMenuExpanded = false },
                                    modifier = Modifier
                                        .background(Color(0xFF141722), RoundedCornerShape(16.dp))
                                        .border(1.dp, BorderGlass.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                                        .widthIn(min = 190.dp)
                                ) {
                                if (!profile.builtIn) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    "Edit Profil",
                                                    color = TextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                )
                                                Text(
                                                    "Ubah nama & parameter",
                                                    color = TextSecondary.copy(alpha = 0.7f),
                                                    fontSize = 10.sp,
                                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AccentCyan.copy(alpha = 0.14f),
                                                border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f)),
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = null,
                                                        tint = AccentCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        onClick = {
                                            cardMenuExpanded = false
                                            editing = profile
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    "Salin Profil",
                                                    color = TextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                )
                                                Text(
                                                    "Duplikasi sebagai profil baru",
                                                    color = TextSecondary.copy(alpha = 0.7f),
                                                    fontSize = 10.sp,
                                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AccentBlue.copy(alpha = 0.14f),
                                                border = BorderStroke(0.6.dp, AccentBlue.copy(alpha = 0.35f)),
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.ContentCopy,
                                                        contentDescription = null,
                                                        tint = AccentBlue,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        onClick = {
                                            cardMenuExpanded = false
                                            onProfileDuplicated(profile.id)
                                        }
                                    )
                                    HorizontalDivider(
                                        color = BorderGlass.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    "Hapus Profil",
                                                    color = AccentRed,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                )
                                                Text(
                                                    "Hapus permanen dari katalog",
                                                    color = TextSecondary.copy(alpha = 0.7f),
                                                    fontSize = 10.sp,
                                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AccentRed.copy(alpha = 0.14f),
                                                border = BorderStroke(0.6.dp, AccentRed.copy(alpha = 0.35f)),
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = AccentRed,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        onClick = {
                                            cardMenuExpanded = false
                                            pendingDelete = profile.id
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    "Kustomisasi",
                                                    color = TextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                )
                                                Text(
                                                    "Sesuaikan parameter profil",
                                                    color = TextSecondary.copy(alpha = 0.7f),
                                                    fontSize = 10.sp,
                                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AccentCyan.copy(alpha = 0.14f),
                                                border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f)),
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Tune,
                                                        contentDescription = null,
                                                        tint = AccentCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        onClick = {
                                            cardMenuExpanded = false
                                            editing = profile
                                        }
                                    )
                                    val default = KernelPerformanceProfile.builtIns().firstOrNull { it.id == profile.id }
                                    if (default != null && (profile.baseProfile != default.baseProfile || profile.domainProfiles != default.domainProfiles || profile.settings.isNotEmpty() || profile.includedSensitiveSettings.isNotEmpty())) {
                                        HorizontalDivider(
                                            color = BorderGlass.copy(alpha = 0.4f),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        "Pulihkan Bawaan",
                                                        color = AccentOrange,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                    )
                                                    Text(
                                                        "Reset ke nilai pabrik Lynx",
                                                        color = TextSecondary.copy(alpha = 0.7f),
                                                        fontSize = 10.sp,
                                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                    )
                                                }
                                            },
                                            leadingIcon = {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = AccentOrange.copy(alpha = 0.14f),
                                                    border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.35f)),
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Default.RestartAlt,
                                                            contentDescription = null,
                                                            tint = AccentOrange,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            onClick = {
                                                cardMenuExpanded = false
                                                pendingRestore = profile.id
                                            }
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

        // Action CTA: Create / Save Current Profile (Symmetrical Cyberpunk Button)
        Surface(
            onClick = { creating = true },
            shape = RoundedCornerShape(14.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(top = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Simpan Kondisi Saat Ini sebagai Profil Kustom",
                    color = AccentOrange,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom spacer to ensure CTA button clears the floating dock
        Spacer(modifier = Modifier.height(115.dp).navigationBarsPadding())
    }

    if (creating || editing != null) {
        val source = editing ?: KernelPerformanceProfile(
            id = "profil_baru", name = "Profil Baru", description = "Disimpan dari konfigurasi Lynx",
            baseProfile = catalog.find(currentProfile)?.baseProfile ?: "balance",
            domainProfiles = mapOf(
                ProfileDomain.CPU to (catalog.find(currentProfile)?.domainProfiles?.get(ProfileDomain.CPU) ?: "balance"),
                ProfileDomain.GPU to (catalog.find(currentProfile)?.domainProfiles?.get(ProfileDomain.GPU) ?: "balanced")
            ),
            includedDomains = setOf(ProfileDomain.MEMORY, ProfileDomain.DISPLAY, ProfileDomain.NETWORK, ProfileDomain.AUDIO)
        )
        ProfileEditorDialog(
            source = source,
            replacingBuiltIn = source.builtIn,
            allowExistingCustom = editing != null && !source.builtIn,
            onDismiss = { creating = false; editing = null },
            onSave = { profile, replace, replaceCustom -> onProfileSaved(profile, replace, replaceCustom); creating = false; editing = null }
        )
    }

    pendingSelection?.let { selected ->
        Dialog(
            onDismissRequest = { pendingSelection = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF10131B),
                border = BorderStroke(1.dp, BorderGlass),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Tune, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("STATUS OVERRIDE", color = AccentCyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                            Text("Override Tweak Aktif", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Pertahankan override lokal saat mengganti profil induk, atau hapus agar semua domain mengikuti profil baru?",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { pendingSelection = null },
                            shape = RoundedCornerShape(12.dp),
                            color = BgDeepOled,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Batal", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Surface(
                            onClick = { onProfileSelected(selected, true); pendingSelection = null },
                            shape = RoundedCornerShape(12.dp),
                            color = BgDeepOled,
                            border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1.3f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Hapus Override", color = AccentOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            onClick = { onProfileSelected(selected, false); pendingSelection = null },
                            shape = RoundedCornerShape(12.dp),
                            color = AccentCyan,
                            modifier = Modifier.weight(1.3f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Pertahankan", color = BgDeepOled, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    pendingExtremeProfile?.let { selected ->
        Dialog(
            onDismissRequest = { pendingExtremeProfile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF10131B),
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.4f)),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.LocalFireDepartment, null, tint = AccentRed, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("PERINGATAN PERFORMA", color = AccentRed, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                            Text("Profil Dasar Extreme", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Profil ini menggunakan kebijakan dasar Extreme dengan pelepasan batas termal. Pastikan perangkat Anda memiliki pendinginan eksternal yang cukup guna mencegah overheat.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { pendingExtremeProfile = null },
                            shape = RoundedCornerShape(12.dp),
                            color = BgDeepOled,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Batal", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Surface(
                            onClick = {
                                pendingExtremeProfile = null
                                if (hasOverrides) pendingSelection = selected else onProfileSelected(selected, true)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = AccentRed,
                            modifier = Modifier.weight(1.3f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Lanjutkan", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { selected ->
        Dialog(
            onDismissRequest = { pendingDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF10131B),
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.35f)),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("KONFIRMASI PENGHAPUSAN", color = AccentRed, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                            Text("Hapus Profil Kustom?", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Profil kustom ini akan dihapus permanen dari konfigurasi Lynx dan tidak dapat dipulihkan.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { pendingDelete = null },
                            shape = RoundedCornerShape(12.dp),
                            color = BgDeepOled,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Batal", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Surface(
                            onClick = { onProfileDeleted(selected); pendingDelete = null },
                            shape = RoundedCornerShape(12.dp),
                            color = AccentRed,
                            modifier = Modifier.weight(1.3f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Hapus Permanen", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRestore?.let { selected ->
        Dialog(
            onDismissRequest = { pendingRestore = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF10131B),
                border = BorderStroke(1.dp, BorderGlass),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentOrange.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.RestartAlt, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("PEMULIHAN PROFIL", color = AccentOrange, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                            Text("Pulihkan Profil Bawaan?", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Kustomisasi pada ${catalog.find(selected)?.name ?: selected} akan dihapus dan nilai konfigurasi pabrik Lynx akan dipulihkan secara penuh.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { pendingRestore = null },
                            shape = RoundedCornerShape(12.dp),
                            color = BgDeepOled,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Batal", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Surface(
                            onClick = { onRestoreBuiltIn(selected); pendingRestore = null },
                            shape = RoundedCornerShape(12.dp),
                            color = AccentOrange,
                            modifier = Modifier.weight(1.3f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Pulihkan", color = BgDeepOled, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SubsystemDomainItem(
    val domain: ProfileDomain,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
private fun ProfileEditorDialog(
    source: KernelPerformanceProfile,
    replacingBuiltIn: Boolean,
    allowExistingCustom: Boolean,
    onDismiss: () -> Unit,
    onSave: (KernelPerformanceProfile, Boolean, Boolean) -> Unit,
) {
    var name by remember(source.id) { mutableStateOf(source.name) }
    var base by remember(source.id) { mutableStateOf(source.baseProfile) }
    var cpu by remember(source.id) { mutableStateOf(source.domainProfiles[ProfileDomain.CPU] ?: source.baseProfile) }
    var gpu by remember(source.id) { mutableStateOf(source.domainProfiles[ProfileDomain.GPU] ?: "balanced") }
    var presetMenu by remember { mutableStateOf("") }
    var includeThermal by remember(source.id) { mutableStateOf("thermal.mode" in source.includedSensitiveSettings) }
    var includeBattery by remember(source.id) { mutableStateOf("charging.max_battery_percent" in source.includedSensitiveSettings) }
    var includedDomains by remember(source.id) { mutableStateOf(source.includedDomains) }
    var replaceMenu by remember { mutableStateOf(false) }
    var replaceBuiltIn by remember { mutableStateOf(false) }

    val accentColor = when (base.lowercase()) {
        "powersave" -> Color(0xFF00E676)
        "balance" -> AccentBlue
        "performance" -> AccentOrange
        "extreme" -> AccentRed
        else -> AccentCyan
    }

    val headerIcon = when (base.lowercase()) {
        "powersave" -> Icons.Default.BatteryChargingFull
        "balance" -> Icons.Default.Tune
        "performance" -> Icons.Default.Bolt
        "extreme" -> Icons.Default.LocalFireDepartment
        else -> Icons.Default.Tune
    }

    val selectedSensitive = buildSet {
        if (includeThermal) addAll(listOf("thermal.mode", "thermal.custom_temp_limit_c"))
        if (includeBattery) addAll(listOf("charging.max_battery_percent", "charging.high_current_target_percent"))
    }
    val selectedDomains = includedDomains + buildSet {
        if (includeThermal) add(ProfileDomain.THERMAL)
        if (includeBattery) add(ProfileDomain.BATTERY)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .wrapContentHeight()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF10131B),
            border = BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = 0.5f), BorderGlass.copy(alpha = 0.35f))
                )
            ),
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── 1. Floating Header ───────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accentColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = headerIcon,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (source.builtIn) "KUSTOMISASI PROFIL" else "EDITOR PROFIL KUSTOM",
                                    color = accentColor,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                if (source.builtIn) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = BgDeepOled,
                                        border = BorderStroke(0.6.dp, BorderGlass)
                                    ) {
                                        Text(
                                            text = "BAWAAN",
                                            color = TextSecondary,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (source.builtIn) "Kustomisasi ${source.name}" else source.name,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                            )
                        }
                    }

                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = Color(0x18FFFFFF),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f))

                // ── 2. Scrollable Bento Body ─────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section 1: Name / Info
                    if (!source.builtIn) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            label = { Text("Nama Profil Kustom") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.EditNote, null, tint = accentColor, modifier = Modifier.size(20.dp))
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = BorderGlass,
                                focusedLabelColor = accentColor,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = accentColor,
                                focusedContainerColor = BgDeepOled,
                                unfocusedContainerColor = BgDeepOled
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgDeepOled,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = accentColor.copy(alpha = 0.12f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Tune, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Templat Bawaan: ${source.name}",
                                        color = TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                    )
                                    Text(
                                        text = "Kustomisasi ini menyesuaikan parameter eksekusi tanpa mengubah berkas inti sistem.",
                                        color = TextSecondary,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // Section 2: Base Profile & Compute Presets (Bento)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgDeepOled,
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Layers, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "PROFIL INDUK (BASE PROFILE)",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }

                            // 4 Segmented Chips for Base Profile
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    Triple("powersave", "Powersave", Color(0xFF00E676)),
                                    Triple("balance", "Balance", AccentBlue),
                                    Triple("performance", "Performa", AccentOrange),
                                    Triple("extreme", "Extreme", AccentRed)
                                ).forEach { (id, label, chipColor) ->
                                    val isSelected = base.lowercase() == id
                                    Surface(
                                        onClick = {
                                            base = id
                                            cpu = id
                                            gpu = when (id) {
                                                "powersave" -> "battery"
                                                "performance" -> "esports"
                                                "balance" -> "balanced"
                                                else -> id
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) chipColor.copy(alpha = 0.16f) else BgElevated,
                                        border = BorderStroke(
                                            if (isSelected) 1.2.dp else 0.6.dp,
                                            if (isSelected) chipColor else BorderGlass
                                        ),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) chipColor else TextSecondary,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                            )
                                        }
                                    }
                                }
                            }

                            // Dual Core Pickers (CPU & GPU)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // CPU Selector
                                Box(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        onClick = { presetMenu = "cpu" },
                                        shape = RoundedCornerShape(10.dp),
                                        color = BgElevated,
                                        border = BorderStroke(0.8.dp, BorderGlass),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Memory, null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Column {
                                                    Text("CPU PRESET", color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                                    Text(
                                                        text = cpu.uppercase(),
                                                        color = TextPrimary,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                    )
                                                }
                                            }
                                            Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = presetMenu == "cpu",
                                        onDismissRequest = { presetMenu = "" },
                                        modifier = Modifier
                                            .background(Color(0xFF141720), RoundedCornerShape(12.dp))
                                            .border(0.8.dp, BorderGlass, RoundedCornerShape(12.dp))
                                    ) {
                                        listOf("powersave", "balance", "performance", "extreme").forEach { id ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        id.uppercase(),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (cpu == id) AccentCyan else TextPrimary,
                                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                    )
                                                },
                                                onClick = { cpu = id; presetMenu = "" }
                                            )
                                        }
                                    }
                                }

                                // GPU Selector
                                Box(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        onClick = { presetMenu = "gpu" },
                                        shape = RoundedCornerShape(10.dp),
                                        color = BgElevated,
                                        border = BorderStroke(0.8.dp, BorderGlass),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.SportsEsports, null, tint = AccentOrange, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Column {
                                                    Text("GPU PRESET", color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                                    Text(
                                                        text = gpu.uppercase(),
                                                        color = TextPrimary,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                    )
                                                }
                                            }
                                            Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = presetMenu == "gpu",
                                        onDismissRequest = { presetMenu = "" },
                                        modifier = Modifier
                                            .background(Color(0xFF141720), RoundedCornerShape(12.dp))
                                            .border(0.8.dp, BorderGlass, RoundedCornerShape(12.dp))
                                    ) {
                                        listOf("battery", "balanced", "esports", "extreme").forEach { id ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        id.uppercase(),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (gpu == id) AccentOrange else TextPrimary,
                                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                    )
                                                },
                                                onClick = { gpu = id; presetMenu = "" }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Hardware Domain Synchronization (Bento)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgDeepOled,
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "SINKRONISASI SUBSISTEM HARDWARE",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Terapkan setelan subsistem saat profil ini beralih",
                                        color = TextSecondary.copy(alpha = 0.7f),
                                        fontSize = 9.5.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = BorderGlass.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 2.dp))

                            val domainItems = listOf(
                                SubsystemDomainItem(ProfileDomain.MEMORY, "Memori & ZRAM", "ZRAM ratio, Swappiness & LMK thrashing guard", Icons.Default.Storage, AccentCyan),
                                SubsystemDomainItem(ProfileDomain.DISPLAY, "Layar & Sentuhan", "SurfaceFlinger offset & lock refresh rate", Icons.Default.Smartphone, AccentBlue),
                                SubsystemDomainItem(ProfileDomain.NETWORK, "Wi-Fi & Jaringan", "Wi-Fi ping stabilizer & TCP congestion window", Icons.Default.Wifi, AccentGreen),
                                SubsystemDomainItem(ProfileDomain.AUDIO, "Sound & Audio DAC", "Low-latency MMAP & Direct DAC offload", Icons.AutoMirrored.Filled.VolumeUp, AccentCyan)
                            )

                            domainItems.forEach { item ->
                                val isChecked = item.domain in includedDomains
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            includedDomains = if (isChecked) includedDomains - item.domain else includedDomains + item.domain
                                        }
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = item.color.copy(alpha = 0.12f),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(item.icon, null, tint = item.color, modifier = Modifier.size(15.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(item.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text(item.subtitle, color = TextSecondary, fontSize = 9.5.sp, lineHeight = 12.5.sp)
                                        }
                                    }
                                    Switch(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            includedDomains = if (checked) includedDomains + item.domain else includedDomains - item.domain
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = BgDeepOled,
                                            checkedTrackColor = accentColor,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = BgElevated,
                                            uncheckedBorderColor = BorderGlass
                                        ),
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }

                            // Sensitive Domain: Thermal
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { includeThermal = !includeThermal }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AccentRed.copy(alpha = 0.12f),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.LocalFireDepartment, null, tint = AccentRed, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Kebijakan Termal", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Sinkronisasi trip points & batasan suhu saat ini", color = TextSecondary, fontSize = 9.5.sp, lineHeight = 12.5.sp)
                                    }
                                }
                                Switch(
                                    checked = includeThermal,
                                    onCheckedChange = { includeThermal = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = BgElevated,
                                        uncheckedBorderColor = BorderGlass
                                    ),
                                    modifier = Modifier.height(26.dp)
                                )
                            }

                            // Sensitive Domain: Charging / Battery
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { includeBattery = !includeBattery }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AccentGreen.copy(alpha = 0.12f),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.BatteryChargingFull, null, tint = AccentGreen, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Batas Pengisian Baterai", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Sinkronisasi batas kapasitas & arus pengisian saat ini", color = TextSecondary, fontSize = 9.5.sp, lineHeight = 12.5.sp)
                                    }
                                }
                                Switch(
                                    checked = includeBattery,
                                    onCheckedChange = { includeBattery = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = BgElevated,
                                        uncheckedBorderColor = BorderGlass
                                    ),
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                    }

                    // Section 4: Notice for Built-ins
                    if (source.builtIn) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentCyan.copy(alpha = 0.08f),
                            border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Nilai bawaan asli Lynx tetap tersimpan aman dan dapat dipulihkan kapan saja melalui opsi 'Pulihkan Bawaan' pada tombol titik tiga.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f))

                // ── 3. Symmetrical Action Bar ────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Batal
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        color = BgDeepOled,
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Batal",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Simpan
                    Surface(
                        onClick = {
                            val isNew = source.id == "profil_baru"
                            val id = if (source.builtIn || !isNew) source.id else name.lowercase().trim().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "profil_baru" }
                            val chosen = source.copy(
                                id = id,
                                name = name.trim(),
                                baseProfile = base,
                                domainProfiles = source.domainProfiles + mapOf(ProfileDomain.CPU to cpu, ProfileDomain.GPU to gpu),
                                includedDomains = selectedDomains,
                                includedSensitiveSettings = selectedSensitive
                            )
                            if (replacingBuiltIn && !replaceBuiltIn) replaceMenu = true else onSave(chosen, replaceBuiltIn, allowExistingCustom)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(46.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = BgDeepOled,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (source.builtIn) "Simpan Kustomisasi" else "Simpan Profil",
                                color = BgDeepOled,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (replaceMenu) {
        Dialog(
            onDismissRequest = { replaceMenu = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF10131B),
                border = BorderStroke(1.dp, BorderGlass),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentOrange.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.SaveAs, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SIMPAN PERUBAHAN",
                                color = AccentOrange,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Pilihan Penyimpanan Profil",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Anda dapat menimpa profil bawaan (${source.name}) atau menyimpannya sebagai profil kustom baru.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = {
                                replaceMenu = false
                                onSave(
                                    source.copy(
                                        id = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'),
                                        name = name.trim(),
                                        baseProfile = base,
                                        domainProfiles = source.domainProfiles + mapOf(ProfileDomain.CPU to cpu, ProfileDomain.GPU to gpu),
                                        includedDomains = selectedDomains,
                                        includedSensitiveSettings = selectedSensitive,
                                        builtIn = false
                                    ),
                                    false,
                                    false
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = BgDeepOled,
                            border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Simpan Baru", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            onClick = {
                                replaceBuiltIn = true
                                replaceMenu = false
                                onSave(
                                    source.copy(
                                        name = name.trim(),
                                        baseProfile = base,
                                        domainProfiles = source.domainProfiles + mapOf(ProfileDomain.CPU to cpu, ProfileDomain.GPU to gpu),
                                        includedDomains = selectedDomains,
                                        includedSensitiveSettings = selectedSensitive
                                    ),
                                    true,
                                    false
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = AccentOrange,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Timpa Bawaan", color = BgDeepOled, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Item(val name: String, val color: Color, val icon: ImageVector)

private fun profileItem(profile: KernelPerformanceProfile) = when (profile.id) {
    "auto" -> Item(profile.name, AccentCyan, Icons.Default.Bolt)
    "performance" -> Item(profile.name, AccentOrange, Icons.Default.Speed)
    "extreme" -> Item(profile.name, AccentRed, Icons.Default.LocalFireDepartment)
    "powersave" -> Item(profile.name, Color(0xFF00E676), Icons.Default.BatteryChargingFull)
    "balance" -> Item(profile.name, AccentBlue, Icons.Default.Tune)
    else -> Item(profile.name, AccentCyan, Icons.Default.Tune)
}
