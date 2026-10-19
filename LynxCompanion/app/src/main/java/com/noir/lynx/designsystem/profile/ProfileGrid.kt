package com.noir.lynx.designsystem.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.scale
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
    gpuPlatform: String = "generic",
    state: com.noir.lynx.data.LynxState = com.noir.lynx.data.LynxState()
) {
    var editing by remember { mutableStateOf<KernelPerformanceProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<String?>(null) }
    var pendingExtremeProfile by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val profiles = catalog.profiles

    val isMtk = gpuPlatform == "mali_ged" || gpuPlatform == "mali_kbase"
    val isQcom = gpuPlatform == "adreno"

    val dynamicScalingTag = when {
        isQcom -> "Dynamic Devfreq"
        isMtk -> "Dynamic DVFS"
        else -> "Dynamic Scaling"
    }
    val perfBoostTag = when {
        isQcom -> "Devfreq Boost"
        isMtk -> "GED Boost"
        else -> "Turbo Boost"
    }
    val peakClockTag = when {
        isMtk -> "Maks OPP"
        else -> "Maks Clock"
    }

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
                "performance" -> if (isMtk) {
                    "Fokus gaming • Clock tinggi, GED boost cepat & respons instan"
                } else {
                    "Fokus gaming • Clock tinggi, devfreq cepat & respons instan"
                }
                "extreme" -> "Performa tanpa batas • Bypass termal penuh (wajib pendingin)"
                else -> profile.description.ifBlank { "Profil kustom berbasis ${profile.baseProfile}" }
            }

            val tags = when (profile.id.lowercase()) {
                "auto" -> listOf("AI Dynamic", "Adaptive", "Zero-Lag")
                "powersave" -> listOf("Maks 60 FPS", "Idle Clock", "Hemat Daya")
                "balance" -> listOf("Schedutil", dynamicScalingTag, "Sejuk & Stabil")
                "performance" -> listOf(perfBoostTag, "High Clock", "Gaming")
                "extreme" -> listOf("Bypass Termal", peakClockTag, "Performa Puncak")
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
                            Surface(
                                onClick = { cardMenuExpanded = true },
                                shape = CircleShape,
                                color = if (cardMenuExpanded) Color(0x22FFFFFF) else Color.Transparent,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Opsi",
                                        tint = if (cardMenuExpanded) AccentCyan else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
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
                                        .clip(RoundedCornerShape(16.dp))
                                        .padding(vertical = 4.dp)
                                        .widthIn(min = 210.dp)
                                ) {
                                    if (!profile.builtIn) {
                                        Surface(
                                            onClick = {
                                                cardMenuExpanded = false
                                                editing = profile
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color.Transparent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
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
                                                Spacer(Modifier.width(12.dp))
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
                                            }
                                        }

                                        Surface(
                                            onClick = {
                                                cardMenuExpanded = false
                                                onProfileDuplicated(profile.id)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color.Transparent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
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
                                                Spacer(Modifier.width(12.dp))
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
                                            }
                                        }

                                        HorizontalDivider(
                                            color = BorderGlass.copy(alpha = 0.4f),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )

                                        Surface(
                                            onClick = {
                                                cardMenuExpanded = false
                                                pendingDelete = profile.id
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color.Transparent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
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
                                                Spacer(Modifier.width(12.dp))
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
                                            }
                                        }
                                    } else {
                                        Surface(
                                            onClick = {
                                                cardMenuExpanded = false
                                                editing = profile
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color.Transparent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
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
                                                Spacer(Modifier.width(12.dp))
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
                                            }
                                        }

                                        val default = KernelPerformanceProfile.builtIns().firstOrNull { it.id == profile.id }
                                        if (default != null && (profile.baseProfile != default.baseProfile || profile.domainProfiles != default.domainProfiles || profile.settings.isNotEmpty() || profile.includedSensitiveSettings.isNotEmpty())) {
                                            HorizontalDivider(
                                                color = BorderGlass.copy(alpha = 0.4f),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                            Surface(
                                                onClick = {
                                                    cardMenuExpanded = false
                                                    pendingRestore = profile.id
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color.Transparent,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
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
                                                    Spacer(Modifier.width(12.dp))
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
                                                }
                                            }
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
        Spacer(Modifier.height(4.dp))
        Surface(
            onClick = { creating = true },
            shape = RoundedCornerShape(14.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
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
            currentState = state,
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
                            Text("STATUS KUSTOMISASI", color = AccentCyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                            Text("Kustomisasi Subsistem Aktif", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Pertahankan kustomisasi subsistem saat mengganti profil global, atau reset semua subsistem mengikuti profil baru?",
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
                                Text("Reset Semua", color = AccentOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            Text("Profil Acuan Ekstrem", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Profil ini menggunakan templat acuan Ekstrem dengan pelepasan batas termal. Pastikan perangkat Anda memiliki pendinginan eksternal yang cukup guna mencegah overheat.",
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

@Composable
private fun SettingOptionChips(
    title: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    accentColor: Color,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 3.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (valKey, label) ->
                val selected = currentValue.equals(valKey, ignoreCase = true)
                Surface(
                    onClick = { onSelect(valKey) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) accentColor.copy(alpha = 0.18f) else BgElevated,
                    border = BorderStroke(
                        if (selected) 1.dp else 0.6.dp,
                        if (selected) accentColor else BorderGlass
                    ),
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 9.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) accentColor else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                        )
                    }
                }
            }
        }
    }
}

private data class PresetOption(
    val id: String,
    val label: String,
    val desc: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
private fun ProfileEditorDialog(
    source: KernelPerformanceProfile,
    currentState: com.noir.lynx.data.LynxState,
    replacingBuiltIn: Boolean,
    allowExistingCustom: Boolean,
    onDismiss: () -> Unit,
    onSave: (KernelPerformanceProfile, Boolean, Boolean) -> Unit,
) {
    var name by remember(source.id) { mutableStateOf(source.name) }
    var base by remember(source.id) { mutableStateOf(source.baseProfile) }
    var cpu by remember(source.id) { mutableStateOf(source.domainProfiles[ProfileDomain.CPU] ?: source.baseProfile) }
    var gpu by remember(source.id) { mutableStateOf(source.domainProfiles[ProfileDomain.GPU] ?: "balanced") }

    val initialUnified = when {
        (cpu == "powersave" || cpu == "battery") && (gpu == "battery" || gpu == "powersave") -> "powersave"
        (cpu == "performance" || cpu == "gaming") && (gpu == "esports" || gpu == "performance") -> "performance"
        cpu == "extreme" && gpu == "extreme" -> "extreme"
        cpu == "balance" && gpu == "balanced" -> "balance"
        else -> null
    }
    var unifiedPreset by remember(source.id) { mutableStateOf(initialUnified ?: if (source.builtIn) source.baseProfile else base) }
    var separateCpuGpu by remember(source.id) { mutableStateOf(initialUnified == null && (cpu != base || gpu != "balanced")) }

    var presetMenu by remember { mutableStateOf("") }
    var includeThermal by remember(source.id) { mutableStateOf("thermal.mode" in source.includedSensitiveSettings || ProfileDomain.THERMAL in source.includedDomains) }
    var includeBattery by remember(source.id) { mutableStateOf("charging.max_battery_percent" in source.includedSensitiveSettings || ProfileDomain.BATTERY in source.includedDomains) }
    var includedDomains by remember(source.id) { mutableStateOf(source.includedDomains) }
    var replaceMenu by remember { mutableStateOf(false) }
    var replaceBuiltIn by remember { mutableStateOf(false) }

    val customSettings = remember(source.id) {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
            putAll(source.settings)
            if (!containsKey("display_touch.refresh_rate_lock")) {
                put("display_touch.refresh_rate_lock", currentState.displayTouch.refreshRateLock.toString())
            }
            if (!containsKey("display_touch.touchboost")) {
                put("display_touch.touchboost", currentState.displayTouch.touchboost.toString())
            }
            if (!containsKey("thermal.mode")) {
                put("thermal.mode", currentState.thermal.mode)
            }
            if (!containsKey("thermal.custom_temp_limit_c")) {
                put("thermal.custom_temp_limit_c", currentState.thermal.customTempLimitC.toString())
            }
            if (!containsKey("charging.max_battery_percent")) {
                put("charging.max_battery_percent", currentState.charging.maxBatteryPercent.toString())
            }
            if (!containsKey("charging.smart_tapering_enabled")) {
                put("charging.smart_tapering_enabled", currentState.charging.smartTaperingEnabled.toString())
            }
            if (!containsKey("memory.swappiness")) {
                put("memory.swappiness", currentState.memory.swappiness.toString())
            }
            if (!containsKey("memory.mglru_enabled")) {
                put("memory.mglru_enabled", currentState.memory.mglruEnabled.toString())
            }
            if (!containsKey("network.tcp_congestion")) {
                put("network.tcp_congestion", currentState.network.tcpCongestion)
            }
            if (!containsKey("network.wifi_ping_stabilizer")) {
                put("network.wifi_ping_stabilizer", currentState.network.wifiPingStabilizer.toString())
            }
            if (!containsKey("audio.low_latency_mmap")) {
                put("audio.low_latency_mmap", currentState.audio.lowLatencyMmap.toString())
            }
            if (!containsKey("audio.bt_crackle_guard")) {
                put("audio.bt_crackle_guard", currentState.audio.btCrackleGuard.toString())
            }
        }
    }

    val accentColor = if (source.builtIn) {
        when (source.id.lowercase()) {
            "powersave" -> Color(0xFF00E676)
            "balance" -> AccentBlue
            "performance" -> AccentOrange
            "extreme" -> AccentRed
            else -> AccentCyan
        }
    } else {
        when (base.lowercase()) {
            "powersave" -> Color(0xFF00E676)
            "balance" -> AccentBlue
            "performance" -> AccentOrange
            "extreme" -> AccentRed
            else -> AccentCyan
        }
    }

    val headerIcon = if (source.builtIn) {
        when (source.id.lowercase()) {
            "powersave" -> Icons.Default.BatteryChargingFull
            "balance" -> Icons.Default.Tune
            "performance" -> Icons.Default.Bolt
            "extreme" -> Icons.Default.LocalFireDepartment
            else -> Icons.Default.Tune
        }
    } else {
        when (base.lowercase()) {
            "powersave" -> Icons.Default.BatteryChargingFull
            "balance" -> Icons.Default.Tune
            "performance" -> Icons.Default.Bolt
            "extreme" -> Icons.Default.LocalFireDepartment
            else -> Icons.Default.Layers
        }
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
                .fillMaxHeight(0.88f)
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
            Column(modifier = Modifier.fillMaxSize()) {
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
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f))

                // ── 2. Scrollable Bento Body ─────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section 1: Name (Custom Profiles Only)
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
                    }

                    // Section 2: Core Compute Policy (CPU & GPU)
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
                            if (source.builtIn) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "KOMPUTASI INTI BAKU",
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )
                                        Text(
                                            text = "Basis profil terkunci pada identitas ${source.name}",
                                            color = TextSecondary.copy(alpha = 0.7f),
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = accentColor.copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = accentColor,
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${source.name} (Terkunci)",
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val desc = when (source.baseProfile.lowercase()) {
                                                "powersave" -> "Governor hemat daya, batas frekuensi rendah & efisiensi termal."
                                                "performance" -> "Governor performa, frekuensi menengah-tinggi untuk gaming & beban berat."
                                                "extreme" -> "Governor maksimum tanpa batasan clock atau throttling OEM."
                                                else -> "Governor adaptif Schedutil untuk keseimbangan daya & performa harian."
                                            }
                                            Text(
                                                text = desc,
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                lineHeight = 13.5.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Layers, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "TEMPLAT ACUAN (BASE TEMPLATE)",
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )
                                        Text(
                                            text = "Profil dasar acuan untuk kebijakan kernel & termal",
                                            color = TextSecondary.copy(alpha = 0.7f),
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }

                                // 4 Segmented Chips for Base Profile Template
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        Triple("powersave", "Hemat Daya", Color(0xFF00E676)),
                                        Triple("balance", "Seimbang", AccentBlue),
                                        Triple("performance", "Performa", AccentOrange),
                                        Triple("extreme", "Ekstrem", AccentRed)
                                    ).forEach { (id, label, chipColor) ->
                                        val isSelected = base.lowercase() == id
                                        Surface(
                                            onClick = {
                                                base = id
                                                unifiedPreset = id
                                                if (!separateCpuGpu) {
                                                    cpu = id
                                                    gpu = when (id) {
                                                        "powersave" -> "battery"
                                                        "performance" -> "esports"
                                                        "balance" -> "balanced"
                                                        else -> id
                                                    }
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
                            }

                            // Decouple Toggle: Separate CPU & GPU
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { separateCpuGpu = !separateCpuGpu }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Kustomisasi preset CPU & GPU secara terpisah",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Bebaskan kombinasi governor CPU dan profil grafis GPU",
                                        color = TextSecondary,
                                        fontSize = 9.5.sp
                                    )
                                }
                                Switch(
                                    checked = separateCpuGpu,
                                    onCheckedChange = { separateCpuGpu = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = BgElevated,
                                        uncheckedBorderColor = BorderGlass
                                    ),
                                    modifier = Modifier.scale(0.85f)
                                )
                            }

                            // Dual Core Pickers (CPU & GPU)
                            AnimatedVisibility(visible = separateCpuGpu) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // CPU Selector Trigger
                                    Surface(
                                        onClick = { presetMenu = "cpu" },
                                        shape = RoundedCornerShape(10.dp),
                                        color = BgElevated,
                                        border = BorderStroke(0.8.dp, BorderGlass),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = AccentCyan.copy(alpha = 0.15f),
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Memory, null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                                                    }
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("CPU PRESET", color = TextSecondary, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                                    Text(
                                                        text = when (cpu.lowercase()) {
                                                            "powersave" -> "Hemat Daya"
                                                            "balance" -> "Seimbang"
                                                            "performance" -> "Performa"
                                                            "extreme" -> "Ekstrem"
                                                            else -> cpu.uppercase()
                                                        },
                                                        color = TextPrimary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                    )
                                                }
                                            }
                                            Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // GPU Selector Trigger
                                    Surface(
                                        onClick = { presetMenu = "gpu" },
                                        shape = RoundedCornerShape(10.dp),
                                        color = BgElevated,
                                        border = BorderStroke(0.8.dp, BorderGlass),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = AccentOrange.copy(alpha = 0.15f),
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.SportsEsports, null, tint = AccentOrange, modifier = Modifier.size(15.dp))
                                                    }
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text("GPU PRESET", color = TextSecondary, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                                    Text(
                                                        text = when (gpu.lowercase()) {
                                                            "battery" -> "Hemat Baterai"
                                                            "balanced" -> "Seimbang"
                                                            "esports" -> "Esports"
                                                            "extreme" -> "Ekstrem"
                                                            else -> gpu.uppercase()
                                                        },
                                                        color = TextPrimary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                    )
                                                }
                                            }
                                            Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Subsystem Overrides & Real Value Controls (Bento)
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
                                Icon(Icons.Default.Tune, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "PARAMETER SUBSISTEM TAMBAHAN",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Tentukan setelan spesifik yang diterapkan saat profil ini aktif",
                                        color = TextSecondary.copy(alpha = 0.7f),
                                        fontSize = 9.5.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = BorderGlass.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 2.dp))

                            // 1. Memori & ZRAM
                            val memChecked = ProfileDomain.MEMORY in includedDomains
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (memChecked) AccentCyan.copy(alpha = 0.05f) else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            includedDomains = if (memChecked) includedDomains - ProfileDomain.MEMORY else includedDomains + ProfileDomain.MEMORY
                                        }
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AccentCyan.copy(alpha = 0.12f),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Storage, null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text("Memori & ZRAM", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (memChecked) "Terapkan rasio swap & MGLRU kustom" else "Mengikuti setelan sistem saat ini (tidak diubah)",
                                                color = if (memChecked) AccentCyan else TextSecondary,
                                                fontSize = 9.5.sp,
                                                lineHeight = 12.5.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = memChecked,
                                        onCheckedChange = { checked ->
                                            includedDomains = if (checked) includedDomains + ProfileDomain.MEMORY else includedDomains - ProfileDomain.MEMORY
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = BgDeepOled,
                                            checkedTrackColor = AccentCyan,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = BgElevated,
                                            uncheckedBorderColor = BorderGlass
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }

                                AnimatedVisibility(visible = memChecked) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp, bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SettingOptionChips(
                                            title = "RASIO SWAPPINESS",
                                            currentValue = customSettings["memory.swappiness"] ?: "100",
                                            options = listOf(
                                                "60" to "60% (Hemat)",
                                                "100" to "100% (Standar)",
                                                "130" to "130% (Agresif)",
                                                "160" to "160% (Game)",
                                                "200" to "200% (Maks)"
                                            ),
                                            accentColor = AccentCyan,
                                            onSelect = { customSettings["memory.swappiness"] = it }
                                        )
                                        SettingOptionChips(
                                            title = "MULTI-GEN LRU (MGLRU)",
                                            currentValue = customSettings["memory.mglru_enabled"] ?: "true",
                                            options = listOf(
                                                "true" to "Aktif (Disarankan)",
                                                "false" to "Nonaktif"
                                            ),
                                            accentColor = AccentCyan,
                                            onSelect = { customSettings["memory.mglru_enabled"] = it }
                                        )
                                    }
                                }
                            }

                            // 2. Layar & Sentuhan
                            val dispChecked = ProfileDomain.DISPLAY in includedDomains
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (dispChecked) AccentBlue.copy(alpha = 0.05f) else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            includedDomains = if (dispChecked) includedDomains - ProfileDomain.DISPLAY else includedDomains + ProfileDomain.DISPLAY
                                        }
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AccentBlue.copy(alpha = 0.12f),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Smartphone, null, tint = AccentBlue, modifier = Modifier.size(15.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text("Layar & Sentuhan", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (dispChecked) "Kunci refresh rate & respon sentuhan kustom" else "Mengikuti setelan sistem saat ini (tidak diubah)",
                                                color = if (dispChecked) AccentBlue else TextSecondary,
                                                fontSize = 9.5.sp,
                                                lineHeight = 12.5.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = dispChecked,
                                        onCheckedChange = { checked ->
                                            includedDomains = if (checked) includedDomains + ProfileDomain.DISPLAY else includedDomains - ProfileDomain.DISPLAY
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = BgDeepOled,
                                            checkedTrackColor = AccentBlue,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = BgElevated,
                                            uncheckedBorderColor = BorderGlass
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }

                                AnimatedVisibility(visible = dispChecked) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp, bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SettingOptionChips(
                                            title = "KUNCI REFRESH RATE",
                                            currentValue = customSettings["display_touch.refresh_rate_lock"] ?: "0",
                                            options = listOf(
                                                "0" to "Bebas / Auto",
                                                "60" to "60 Hz",
                                                "90" to "90 Hz",
                                                "120" to "120 Hz"
                                            ),
                                            accentColor = AccentBlue,
                                            onSelect = { customSettings["display_touch.refresh_rate_lock"] = it }
                                        )
                                        SettingOptionChips(
                                            title = "RESPON SENTUHAN (TOUCHBOOST)",
                                            currentValue = customSettings["display_touch.touchboost"] ?: "false",
                                            options = listOf(
                                                "true" to "TouchBoost Aktif",
                                                "false" to "Standar (Off)"
                                            ),
                                            accentColor = AccentBlue,
                                            onSelect = { customSettings["display_touch.touchboost"] = it }
                                        )
                                    }
                                }
                            }

                            // 3. Kebijakan Termal
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (includeThermal) AccentRed.copy(alpha = 0.05f) else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { includeThermal = !includeThermal }
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
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
                                            Text(
                                                if (includeThermal) "Terapkan profil mitigasi suhu kustom" else "Mengikuti kebijakan OEM/sistem saat ini",
                                                color = if (includeThermal) AccentRed else TextSecondary,
                                                fontSize = 9.5.sp,
                                                lineHeight = 12.5.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = includeThermal,
                                        onCheckedChange = { includeThermal = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = BgDeepOled,
                                            checkedTrackColor = AccentRed,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = BgElevated,
                                            uncheckedBorderColor = BorderGlass
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }

                                AnimatedVisibility(visible = includeThermal) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp, bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SettingOptionChips(
                                            title = "MODE MITIGASI TERMAL",
                                            currentValue = customSettings["thermal.mode"] ?: "default",
                                            options = listOf(
                                                "default" to "Standar OEM",
                                                "stable" to "Stabil Dingin",
                                                "performance" to "Performa Tinggi"
                                            ),
                                            accentColor = AccentRed,
                                            onSelect = { customSettings["thermal.mode"] = it }
                                        )
                                        SettingOptionChips(
                                            title = "BATAS SUHU MAKSIMUM",
                                            currentValue = customSettings["thermal.custom_temp_limit_c"] ?: "45",
                                            options = listOf(
                                                "40" to "40°C",
                                                "45" to "45°C (Aman)",
                                                "48" to "48°C",
                                                "50" to "50°C (Game)",
                                                "55" to "55°C (Ekstrem)"
                                            ),
                                            accentColor = AccentRed,
                                            onSelect = { customSettings["thermal.custom_temp_limit_c"] = it }
                                        )
                                    }
                                }
                            }

                            // 4. Batas Pengisian Baterai
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (includeBattery) AccentGreen.copy(alpha = 0.05f) else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { includeBattery = !includeBattery }
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
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
                                            Text(
                                                if (includeBattery) "Terapkan batas AutoCut & tapering kustom" else "Mengikuti setelan pengisian sistem saat ini",
                                                color = if (includeBattery) AccentGreen else TextSecondary,
                                                fontSize = 9.5.sp,
                                                lineHeight = 12.5.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = includeBattery,
                                        onCheckedChange = { includeBattery = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = BgDeepOled,
                                            checkedTrackColor = AccentGreen,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = BgElevated,
                                            uncheckedBorderColor = BorderGlass
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }

                                AnimatedVisibility(visible = includeBattery) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp, bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SettingOptionChips(
                                            title = "BATAS KAPASITAS AUTOCUT",
                                            currentValue = customSettings["charging.max_battery_percent"] ?: "80",
                                            options = listOf(
                                                "70" to "70%",
                                                "80" to "80% (Awet)",
                                                "85" to "85%",
                                                "90" to "90%",
                                                "100" to "100% (Penuh)"
                                            ),
                                            accentColor = AccentGreen,
                                            onSelect = { customSettings["charging.max_battery_percent"] = it }
                                        )
                                        SettingOptionChips(
                                            title = "SMART TAPERING ARUS",
                                            currentValue = customSettings["charging.smart_tapering_enabled"] ?: "true",
                                            options = listOf(
                                                "true" to "Smart Tapering Aktif",
                                                "false" to "Standar"
                                            ),
                                            accentColor = AccentGreen,
                                            onSelect = { customSettings["charging.smart_tapering_enabled"] = it }
                                        )
                                    }
                                }
                            }

                            // 5. Wi-Fi & Jaringan
                            val netChecked = ProfileDomain.NETWORK in includedDomains
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (netChecked) AccentGreen.copy(alpha = 0.05f) else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            includedDomains = if (netChecked) includedDomains - ProfileDomain.NETWORK else includedDomains + ProfileDomain.NETWORK
                                        }
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AccentGreen.copy(alpha = 0.12f),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Wifi, null, tint = AccentGreen, modifier = Modifier.size(15.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text("Wi-Fi & Jaringan", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (netChecked) "Terapkan algoritma TCP & stabilizer kustom" else "Mengikuti konfigurasi sistem saat ini (tidak diubah)",
                                                color = if (netChecked) AccentGreen else TextSecondary,
                                                fontSize = 9.5.sp,
                                                lineHeight = 12.5.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = netChecked,
                                        onCheckedChange = { checked ->
                                            includedDomains = if (checked) includedDomains + ProfileDomain.NETWORK else includedDomains - ProfileDomain.NETWORK
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = BgDeepOled,
                                            checkedTrackColor = AccentGreen,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = BgElevated,
                                            uncheckedBorderColor = BorderGlass
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }

                                AnimatedVisibility(visible = netChecked) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp, bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SettingOptionChips(
                                            title = "ALGORITMA TCP CONGESTION",
                                            currentValue = customSettings["network.tcp_congestion"] ?: "bbr",
                                            options = listOf(
                                                "bbr" to "BBR (Low-Latency)",
                                                "cubic" to "Cubic (Standar)",
                                                "westwood" to "Westwood"
                                            ),
                                            accentColor = AccentGreen,
                                            onSelect = { customSettings["network.tcp_congestion"] = it }
                                        )
                                        SettingOptionChips(
                                            title = "WI-FI PING STABILIZER",
                                            currentValue = customSettings["network.wifi_ping_stabilizer"] ?: "true",
                                            options = listOf(
                                                "true" to "Stabilizer Aktif",
                                                "false" to "Nonaktif"
                                            ),
                                            accentColor = AccentGreen,
                                            onSelect = { customSettings["network.wifi_ping_stabilizer"] = it }
                                        )
                                    }
                                }
                            }

                            // 6. Sound & Audio DAC
                            val audChecked = ProfileDomain.AUDIO in includedDomains
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (audChecked) AccentCyan.copy(alpha = 0.05f) else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            includedDomains = if (audChecked) includedDomains - ProfileDomain.AUDIO else includedDomains + ProfileDomain.AUDIO
                                        }
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AccentCyan.copy(alpha = 0.12f),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text("Sound & Audio DAC", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (audChecked) "Terapkan bypass buffer MMAP & proteksi kustom" else "Mengikuti konfigurasi sistem saat ini (tidak diubah)",
                                                color = if (audChecked) AccentCyan else TextSecondary,
                                                fontSize = 9.5.sp,
                                                lineHeight = 12.5.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = audChecked,
                                        onCheckedChange = { checked ->
                                            includedDomains = if (checked) includedDomains + ProfileDomain.AUDIO else includedDomains - ProfileDomain.AUDIO
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = BgDeepOled,
                                            checkedTrackColor = AccentCyan,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = BgElevated,
                                            uncheckedBorderColor = BorderGlass
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }

                                AnimatedVisibility(visible = audChecked) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp, bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SettingOptionChips(
                                            title = "LOW-LATENCY MMAP AUDIO",
                                            currentValue = customSettings["audio.low_latency_mmap"] ?: "true",
                                            options = listOf(
                                                "true" to "MMAP Bypass Aktif",
                                                "false" to "Standar"
                                            ),
                                            accentColor = AccentCyan,
                                            onSelect = { customSettings["audio.low_latency_mmap"] = it }
                                        )
                                        SettingOptionChips(
                                            title = "DIRECT DAC OFFLOAD / BT GUARD",
                                            currentValue = customSettings["audio.bt_crackle_guard"] ?: "true",
                                            options = listOf(
                                                "true" to "Proteksi Aktif",
                                                "false" to "Standar"
                                            ),
                                            accentColor = AccentCyan,
                                            onSelect = { customSettings["audio.bt_crackle_guard"] = it }
                                        )
                                    }
                                }
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
                            val targetBase = if (source.builtIn) source.baseProfile else base
                            val chosen = source.copy(
                                id = id,
                                name = name.trim(),
                                baseProfile = targetBase,
                                domainProfiles = source.domainProfiles + mapOf(ProfileDomain.CPU to cpu, ProfileDomain.GPU to gpu),
                                includedDomains = selectedDomains,
                                settings = customSettings.toMap(),
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

    if (presetMenu.isNotBlank()) {
        val isCpu = presetMenu == "cpu"
        val title = if (isCpu) "PILIH PRESET CPU" else "PILIH PRESET GPU"
        val subtitle = if (isCpu) "Tentukan profil frekuensi & alokasi core CPU" else "Tentukan perilaku clock & scaling governor GPU"
        val headerColor = if (isCpu) AccentBlue else AccentOrange
        val presetHeaderIcon = if (isCpu) Icons.Default.Memory else Icons.Default.SportsEsports

        val options = if (isCpu) {
            listOf(
                PresetOption("powersave", "Hemat Daya", "Mode hemat konsumsi daya, batas frekuensi rendah", Icons.Default.BatteryChargingFull, Color(0xFF00E676)),
                PresetOption("balance", "Seimbang", "Rasio efisiensi & respon standar harian", Icons.Default.Tune, AccentBlue),
                PresetOption("performance", "Performa", "Respon instan, frekuensi tinggi untuk multitasking", Icons.Default.Bolt, AccentOrange),
                PresetOption("extreme", "Ekstrem", "Frekuensi maksimum tanpa batasan clock CPU", Icons.Default.LocalFireDepartment, AccentRed)
            )
        } else {
            listOf(
                PresetOption("battery", "Hemat Baterai", "Clock GPU minimal, efisiensi termal maksimal", Icons.Default.BatteryChargingFull, Color(0xFF00E676)),
                PresetOption("balanced", "Seimbang", "Skala dinamis seimbang sesuai beban grafis", Icons.Default.Tune, AccentBlue),
                PresetOption("esports", "Esports", "Clock GPU menengah-tinggi untuk frame rate stabil", Icons.Default.SportsEsports, AccentOrange),
                PresetOption("extreme", "Ekstrem", "Clock GPU maksimum terkunci tanpa downclock", Icons.Default.LocalFireDepartment, AccentRed)
            )
        }
        val currentSelection = if (isCpu) cpu.lowercase() else gpu.lowercase()

        Dialog(
            onDismissRequest = { presetMenu = "" },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF10131B),
                border = BorderStroke(1.dp, Brush.verticalGradient(listOf(headerColor.copy(alpha = 0.5f), BorderGlass.copy(alpha = 0.35f)))),
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = headerColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, headerColor.copy(alpha = 0.3f)),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(presetHeaderIcon, null, tint = headerColor, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = title,
                                    color = headerColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = subtitle,
                                    color = TextSecondary,
                                    fontSize = 10.5.sp,
                                    lineHeight = 13.5.sp
                                )
                            }
                        }
                        Surface(
                            onClick = { presetMenu = "" },
                            shape = CircleShape,
                            color = Color(0x18FFFFFF),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, "Tutup", tint = TextSecondary, modifier = Modifier.size(15.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { opt ->
                            val isSelected = currentSelection == opt.id
                            Surface(
                                onClick = {
                                    if (isCpu) cpu = opt.id else gpu = opt.id
                                    presetMenu = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) opt.color.copy(alpha = 0.14f) else BgDeepOled,
                                border = BorderStroke(
                                    if (isSelected) 1.2.dp else 0.8.dp,
                                    if (isSelected) opt.color.copy(alpha = 0.8f) else BorderGlass
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = opt.color.copy(alpha = 0.15f),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(opt.icon, null, tint = opt.color, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = opt.label,
                                                color = if (isSelected) opt.color else TextPrimary,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = opt.desc,
                                                color = TextSecondary,
                                                fontSize = 9.5.sp,
                                                lineHeight = 12.5.sp
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Terpilih",
                                            tint = opt.color,
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
                                        baseProfile = if (source.builtIn) source.baseProfile else base,
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
                                        baseProfile = source.baseProfile,
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
