package com.noir.lynx.designsystem.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.noir.lynx.designsystem.theme.AccentBlue
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.AccentRed
import com.noir.lynx.designsystem.theme.BgDeepOled
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        profiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { profile ->
                    val item = profileItem(profile)
                    val isSelected = profile.id == currentProfile
                    val targetBg = if (isSelected) item.color.copy(alpha = 0.14f) else BgElevated
                    val targetBorder = if (isSelected) item.color.copy(alpha = 0.75f) else BorderSubtle
                    val animatedBg by animateColorAsState(targetBg, animationSpec = tween(150), label = "profile_grid_bg")
                    val animatedBorder by animateColorAsState(targetBorder, animationSpec = tween(150), label = "profile_grid_border")
                    var cardMenuExpanded by remember { mutableStateOf(false) }

                    Surface(
                        onClick = {
                            if (profile.id == "extreme" && currentProfile != profile.id) onExtremeConfirmRequired()
                            else if (profile.baseProfile == "extreme" && currentProfile != profile.id) pendingExtremeProfile = profile.id
                            else if (currentProfile != profile.id) {
                                if (hasOverrides) pendingSelection = profile.id else onProfileSelected(profile.id, true)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = animatedBg,
                        border = BorderStroke(if (isSelected) 1.2.dp else 0.8.dp, animatedBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            // Top Row: Icon container + Active Pill + Kebab Action Menu
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = item.color.copy(alpha = 0.16f),
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = null,
                                                tint = item.color,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = item.color.copy(alpha = 0.22f),
                                            border = BorderStroke(0.6.dp, item.color)
                                        ) {
                                            Text(
                                                text = "AKTIF",
                                                color = item.color,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                // Kebab Dropdown Menu
                                Box {
                                    IconButton(
                                        onClick = { cardMenuExpanded = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Opsi",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = cardMenuExpanded,
                                        onDismissRequest = { cardMenuExpanded = false }
                                    ) {
                                        if (!profile.builtIn) {
                                            DropdownMenuItem(
                                                text = { Text("Edit Profil") },
                                                leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    cardMenuExpanded = false
                                                    editing = profile
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Salin Profil") },
                                                leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    cardMenuExpanded = false
                                                    onProfileDuplicated(profile.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Hapus Profil", color = AccentRed) },
                                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    cardMenuExpanded = false
                                                    pendingDelete = profile.id
                                                }
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Kustomisasi") },
                                                leadingIcon = { Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    cardMenuExpanded = false
                                                    editing = profile
                                                }
                                            )
                                            val default = KernelPerformanceProfile.builtIns().firstOrNull { it.id == profile.id }
                                            if (default != null && (profile.baseProfile != default.baseProfile || profile.domainProfiles != default.domainProfiles || profile.settings.isNotEmpty() || profile.includedSensitiveSettings.isNotEmpty())) {
                                                DropdownMenuItem(
                                                    text = { Text("Pulihkan Bawaan") },
                                                    leadingIcon = { Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp)) },
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

                            Spacer(Modifier.height(6.dp))

                            // Profile Name
                            Text(
                                text = item.name,
                                color = if (isSelected) item.color else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                            )

                            Spacer(Modifier.height(2.dp))

                            // Profile Description (1-Line Crisp Tag)
                            val shortDesc = when (profile.id.lowercase()) {
                                "powersave" -> "Maks 60 FPS • Hemat Daya"
                                "balance" -> "Seimbang & Responsif Harian"
                                "performance" -> "Gaming & Latensi Rendah"
                                "extreme" -> "Maksimal Tanpa Batas Termal"
                                else -> profile.description.ifBlank { "Profil ${profile.baseProfile}" }
                            }
                            Text(
                                text = shortDesc,
                                color = TextSecondary,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Status Tag if modified built-in
                            val defaultProf = KernelPerformanceProfile.builtIns().firstOrNull { it.id == profile.id }
                            val isModified = profile.builtIn && defaultProf != null && (profile.baseProfile != defaultProf.baseProfile || profile.domainProfiles != defaultProf.domainProfiles || profile.settings.isNotEmpty() || profile.includedSensitiveSettings.isNotEmpty())
                            if (isModified) {
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = AccentOrange.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, AccentOrange.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "Tersuai",
                                        color = AccentOrange,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // Action CTA: Create / Save Current Profile (Lynx Cyberpunk Styled Surface)
        Surface(
            onClick = { creating = true },
            shape = RoundedCornerShape(10.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Tambah Profil Baru / Simpan Kondisi Saat Ini",
                    color = AccentOrange,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
        AlertDialog(
            onDismissRequest = { pendingSelection = null },
            title = { Text("Override tweak aktif") },
            text = { Text("Pertahankan override lokal saat mengganti profil induk, atau hapus agar semua domain mengikuti profil baru?") },
            confirmButton = { TextButton(onClick = { onProfileSelected(selected, false); pendingSelection = null }) { Text("Pertahankan") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { onProfileSelected(selected, true); pendingSelection = null }) { Text("Hapus override") }
                    TextButton(onClick = { pendingSelection = null }) { Text("Batal") }
                }
            }
        )
    }
    pendingExtremeProfile?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingExtremeProfile = null },
            title = { Text("Profil dasar Extreme") },
            text = { Text("Profil ini menggunakan kebijakan dasar Extreme. Pilih Lanjutkan untuk menerapkannya.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingExtremeProfile = null
                    if (hasOverrides) pendingSelection = selected else onProfileSelected(selected, true)
                }) { Text("Lanjutkan") }
            },
            dismissButton = { TextButton(onClick = { pendingExtremeProfile = null }) { Text("Batal") } }
        )
    }
    pendingDelete?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus profil?") },
            text = { Text("Profil kustom ini akan dihapus dari perangkat.") },
            confirmButton = { TextButton(onClick = { onProfileDeleted(selected); pendingDelete = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } }
        )
    }
    pendingRestore?.let { selected ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Pulihkan profil bawaan?") },
            text = { Text("Kustomisasi ${catalog.find(selected)?.name ?: selected} akan dihapus dan nilai bawaan Lynx dipulihkan.") },
            confirmButton = { TextButton(onClick = { onRestoreBuiltIn(selected); pendingRestore = null }) { Text("Pulihkan") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Batal") } }
        )
    }
}

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
    var menu by remember { mutableStateOf(false) }
    var replaceMenu by remember { mutableStateOf(false) }
    var replaceBuiltIn by remember { mutableStateOf(false) }
    val selectedSensitive = buildSet {
        if (includeThermal) addAll(listOf("thermal.mode", "thermal.custom_temp_limit_c"))
        if (includeBattery) addAll(listOf("charging.max_battery_percent", "charging.high_current_target_percent"))
    }
    val selectedDomains = includedDomains + buildSet {
        if (includeThermal) add(ProfileDomain.THERMAL)
        if (includeBattery) add(ProfileDomain.BATTERY)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source.builtIn) "Kustomisasi ${source.name}" else "Profil Kernel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nama profil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Surface(
                    onClick = { menu = true },
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Profil dasar: $base", color = TextPrimary, fontSize = 12.sp)
                        Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    KernelPerformanceProfile.BUILT_IN_IDS.filter { it != "auto" }.forEach { id ->
                        DropdownMenuItem(text = { Text(id) }, onClick = {
                            base = id
                            cpu = id
                            gpu = when (id) { "powersave" -> "battery"; "performance" -> "esports"; "balance" -> "balanced"; else -> id }
                            menu = false
                        })
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { presetMenu = "cpu" },
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CPU: $cpu", color = TextPrimary, fontSize = 11.5.sp)
                            Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Surface(
                        onClick = { presetMenu = "gpu" },
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("GPU: $gpu", color = TextPrimary, fontSize = 11.5.sp)
                            Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                DropdownMenu(expanded = presetMenu == "cpu", onDismissRequest = { presetMenu = "" }) {
                    listOf("powersave", "balance", "performance", "extreme").forEach { id ->
                        DropdownMenuItem(text = { Text(id) }, onClick = { cpu = id; presetMenu = "" })
                    }
                }
                DropdownMenu(expanded = presetMenu == "gpu", onDismissRequest = { presetMenu = "" }) {
                    listOf("battery", "balanced", "esports", "extreme").forEach { id ->
                        DropdownMenuItem(text = { Text(id) }, onClick = { gpu = id; presetMenu = "" })
                    }
                }

                Text("Simpan nilai kondisi saat ini per domain:", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                
                listOf(
                    ProfileDomain.MEMORY to "Memori",
                    ProfileDomain.DISPLAY to "Layar",
                    ProfileDomain.NETWORK to "Jaringan",
                    ProfileDomain.AUDIO to "Audio",
                ).forEach { (domain, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                includedDomains = if (domain in includedDomains) includedDomains - domain else includedDomains + domain
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = domain in includedDomains,
                            onCheckedChange = { checked ->
                                includedDomains = if (checked) includedDomains + domain else includedDomains - domain
                            },
                            colors = CheckboxDefaults.colors(checkedColor = AccentCyan, checkmarkColor = BgDeepOled, uncheckedColor = TextSecondary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = TextPrimary, fontSize = 12.sp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { includeThermal = !includeThermal }
                        .padding(vertical = 2.dp)
                ) {
                    Checkbox(
                        checked = includeThermal,
                        onCheckedChange = { includeThermal = it },
                        colors = CheckboxDefaults.colors(checkedColor = AccentCyan, checkmarkColor = BgDeepOled, uncheckedColor = TextSecondary)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Sertakan kebijakan termal saat ini", color = TextPrimary, fontSize = 12.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { includeBattery = !includeBattery }
                        .padding(vertical = 2.dp)
                ) {
                    Checkbox(
                        checked = includeBattery,
                        onCheckedChange = { includeBattery = it },
                        colors = CheckboxDefaults.colors(checkedColor = AccentCyan, checkmarkColor = BgDeepOled, uncheckedColor = TextSecondary)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Sertakan batas pengisian saat ini", color = TextPrimary, fontSize = 12.sp)
                }

                if (replacingBuiltIn) {
                    Text("Nilai bawaan asli dapat dipulihkan dari kartu profil.", color = TextSecondary, fontSize = 10.5.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val isNew = source.id == "profil_baru"
                val id = if (source.builtIn || !isNew) source.id else name.lowercase().trim().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "profil_baru" }
                val chosen = source.copy(
                    id = id, name = name.trim(), baseProfile = base,
                    domainProfiles = source.domainProfiles + mapOf(ProfileDomain.CPU to cpu, ProfileDomain.GPU to gpu),
                    includedDomains = selectedDomains,
                    includedSensitiveSettings = selectedSensitive
                )
                if (replacingBuiltIn && !replaceBuiltIn) replaceMenu = true else onSave(chosen, replaceBuiltIn, allowExistingCustom)
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
    if (replaceMenu) {
        AlertDialog(
            onDismissRequest = { replaceMenu = false },
            title = { Text("Simpan perubahan profil bawaan?") },
            text = { Text("Anda dapat menimpa ${source.name} atau menyimpan perubahan sebagai profil baru.") },
            confirmButton = { TextButton(onClick = { replaceBuiltIn = true; replaceMenu = false; onSave(source.copy(name = name.trim(), baseProfile = base, domainProfiles = source.domainProfiles + mapOf(ProfileDomain.CPU to cpu, ProfileDomain.GPU to gpu), includedDomains = selectedDomains, includedSensitiveSettings = selectedSensitive), true, false) }) { Text("Timpa bawaan") } },
            dismissButton = { TextButton(onClick = { replaceMenu = false; onSave(source.copy(id = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'), name = name.trim(), baseProfile = base, domainProfiles = source.domainProfiles + mapOf(ProfileDomain.CPU to cpu, ProfileDomain.GPU to gpu), includedDomains = selectedDomains, includedSensitiveSettings = selectedSensitive, builtIn = false), false, false) }) { Text("Simpan baru") } }
        )
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
