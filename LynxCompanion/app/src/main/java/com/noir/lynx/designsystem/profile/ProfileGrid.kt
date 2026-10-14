package com.noir.lynx.designsystem.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.noir.lynx.designsystem.theme.AccentBlue
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.AccentRed
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
                    val targetBg = if (isSelected) item.color.copy(alpha = 0.16f) else BgElevated
                    val targetBorder = if (isSelected) item.color.copy(alpha = 0.75f) else BorderSubtle
                    val animatedBg by animateColorAsState(targetBg, animationSpec = tween(150), label = "profile_grid_bg")
                    val animatedBorder by animateColorAsState(targetBorder, animationSpec = tween(150), label = "profile_grid_border")

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
                        border = BorderStroke(1.dp, animatedBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                item.name,
                                color = item.color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                            )
                            Text(profile.description.ifBlank { "Profil ${profile.baseProfile}" }, color = TextSecondary, fontSize = 10.sp)
                            if (!profile.builtIn) {
                                TextButton(onClick = { editing = profile }) { Text("Edit") }
                                TextButton(onClick = { onProfileDuplicated(profile.id) }) { Text("Salin") }
                                TextButton(onClick = { pendingDelete = profile.id }) { Text("Hapus") }
                            } else {
                                TextButton(onClick = { editing = profile }) { Text("Kustomisasi") }
                                val default = KernelPerformanceProfile.builtIns().firstOrNull { it.id == profile.id }
                                if (default != null && (profile.baseProfile != default.baseProfile || profile.domainProfiles != default.domainProfiles || profile.settings.isNotEmpty() || profile.includedSensitiveSettings.isNotEmpty())) {
                                    TextButton(onClick = { pendingRestore = profile.id }) { Text("Pulihkan bawaan") }
                                }
                            }
                        }
                    }
                }
                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { creating = true }) { Text("Tambah profil") }
            Button(onClick = { creating = true }) { Text("Simpan kondisi saat ini") }
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
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Nama profil") }, singleLine = true)
                TextButton(onClick = { menu = true }) { Text("Profil dasar: $base") }
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
                TextButton(onClick = { presetMenu = "cpu" }) { Text("CPU: $cpu · ubah") }
                TextButton(onClick = { presetMenu = "gpu" }) { Text("GPU: $gpu · ubah") }
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
                Text("Simpan nilai kondisi saat ini per domain:", color = TextSecondary, fontSize = 11.sp)
                listOf(
                    ProfileDomain.MEMORY to "Memori",
                    ProfileDomain.DISPLAY to "Layar",
                    ProfileDomain.NETWORK to "Jaringan",
                    ProfileDomain.AUDIO to "Audio",
                ).forEach { (domain, label) ->
                    TextButton(onClick = {
                        includedDomains = if (domain in includedDomains) includedDomains - domain else includedDomains + domain
                    }) { Text("${if (domain in includedDomains) "☑" else "□"} $label") }
                }
                TextButton(onClick = { includeThermal = !includeThermal }) { Text("${if (includeThermal) "☑" else "□"} Sertakan kebijakan termal saat ini") }
                TextButton(onClick = { includeBattery = !includeBattery }) { Text("${if (includeBattery) "☑" else "□"} Sertakan batas pengisian saat ini") }
                if (replacingBuiltIn) {
                    Text("Nilai bawaan asli dapat dipulihkan dari kartu profil.", color = TextSecondary, fontSize = 11.sp)
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
