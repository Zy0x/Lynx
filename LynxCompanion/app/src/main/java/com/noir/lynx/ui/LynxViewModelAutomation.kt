package com.noir.lynx.ui

import android.content.Context
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update


    fun LynxViewModel.toggleGameHud(context: android.content.Context, enable: Boolean) {
        recordStateMutation()
        context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("hud_user_enabled", enable)
            .apply()
        viewModelScope.launch(Dispatchers.IO) {
            if (enable) {
                var canDraw = android.provider.Settings.canDrawOverlays(context)
                if (!canDraw) {
                    LynxRepository.grantOverlayPermission()
                    canDraw = android.provider.Settings.canDrawOverlays(context)
                }
                if (!canDraw) {
                    _uiState.update {
                        it.copy(
                            isGameHudActive = false,
                            errorMessage = "Izin 'Tampilkan di atas aplikasi lain' diperlukan."
                        )
                    }
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                    return@launch
                }

                _uiState.update { it.copy(isGameHudActive = true) }
                val intent = android.content.Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
                    action = com.noir.lynx.service.LynxFloatingHudService.ACTION_START
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                _uiState.update { it.copy(successMessage = "Lynx Engine Diaktifkan") }
            } else {
                _uiState.update { it.copy(isGameHudActive = false) }
                val intent = android.content.Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
                    action = com.noir.lynx.service.LynxFloatingHudService.ACTION_STOP
                }
                context.startService(intent)
                _uiState.update { it.copy(successMessage = "Lynx Engine Dinonaktifkan") }
            }
        }
    }


    fun LynxViewModel.setHudMode(context: android.content.Context, mode: Int) {
        val clamped = mode.coerceIn(0, 1)
        context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("hud_mode", clamped)
            .apply()
        _uiState.update { it.copy(hudMode = clamped) }
        val intent = android.content.Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
            action = com.noir.lynx.service.LynxFloatingHudService.ACTION_SET_MODE
            putExtra(com.noir.lynx.service.LynxFloatingHudService.EXTRA_MODE, clamped)
        }
        if (com.noir.lynx.service.LynxFloatingHudService.isRunning) {
            context.startService(intent)
        }
    }


    fun LynxViewModel.setHudPinMiniFps(context: android.content.Context, pin: Boolean) {
        _uiState.update { it.copy(hudPinMiniFps = pin) }
        context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("pin_mini_fps", pin)
            .apply()
        val intent = android.content.Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
            action = com.noir.lynx.service.LynxFloatingHudService.ACTION_SET_PIN_FPS
            putExtra(com.noir.lynx.service.LynxFloatingHudService.EXTRA_PIN_FPS, pin)
        }
        if (com.noir.lynx.service.LynxFloatingHudService.isRunning) {
            context.startService(intent)
        }
    }


    fun LynxViewModel.syncHudPrefs(context: android.content.Context) {
        val prefs = context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
        val style = prefs.getInt("hud_style", 1).coerceIn(1, 6)
        val mode = prefs.getInt("hud_mode", 0).coerceIn(0, 1)
        val pin = prefs.getBoolean("pin_mini_fps", false)
        val feedbackStyle = prefs.getString("mode_feedback_style", "jewel_top") ?: "jewel_top"
        val feedbackDual = prefs.getBoolean("mode_feedback_dual", true)
        val isRunning = com.noir.lynx.service.LynxFloatingHudService.isHudProcessAlive(context)
        _uiState.update {
            it.copy(
                hudStyle = style,
                hudMode = mode,
                hudPinMiniFps = pin,
                modeFeedbackStyle = feedbackStyle,
                modeFeedbackDualMorph = feedbackDual,
                isGameHudActive = isRunning
            )
        }
    }

    fun LynxViewModel.setModeFeedbackStyle(context: android.content.Context, style: String) {
        context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("mode_feedback_style", style)
            .apply()
        _uiState.update { it.copy(modeFeedbackStyle = style) }

        // Debounce root daemon configuration write so rapid tapping doesn't flood shell tasks
        feedbackStyleSaveJob?.cancel()
        feedbackStyleSaveJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(350L)
            com.topjohnwu.superuser.Shell.cmd("echo '$style' > /data/adb/lynx/feedback_style 2>/dev/null").submit()
        }
    }

    fun LynxViewModel.setModeFeedbackDualMorph(context: android.content.Context, enabled: Boolean) {
        context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("mode_feedback_dual", enabled)
            .apply()
        _uiState.update { it.copy(modeFeedbackDualMorph = enabled) }
    }


    fun LynxViewModel.setHudStyle(context: android.content.Context, style: Int) {
        val clamped = style.coerceIn(1, 6)
        context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("hud_style", clamped)
            .apply()
        _uiState.update { it.copy(hudStyle = clamped) }
        val intent = android.content.Intent(context, com.noir.lynx.service.LynxFloatingHudService::class.java).apply {
            action = com.noir.lynx.service.LynxFloatingHudService.ACTION_SET_STYLE
            putExtra(com.noir.lynx.service.LynxFloatingHudService.EXTRA_STYLE, clamped)
        }
        if (com.noir.lynx.service.LynxFloatingHudService.isRunning) {
            context.startService(intent)
        }
    }


    fun LynxViewModel.toggleAppAutomation(context: android.content.Context, enable: Boolean) {
        recordStateMutation()
        _uiState.update { it.copy(isAppAutomationActive = enable) }
        viewModelScope.launch(Dispatchers.IO) {
            if (enable) {
                LynxRepository.startAppAutomation(context)
                _uiState.update { it.copy(successMessage = "Otomasi Profil Per-App Diaktifkan") }
            } else {
                LynxRepository.stopAppAutomation(context)
                _uiState.update { it.copy(successMessage = "Otomasi Profil Dinonaktifkan") }
            }
        }
    }


    fun LynxViewModel.addOrUpdateAppProfileRule(context: android.content.Context, rule: com.noir.lynx.data.AppProfileRule) {
        recordStateMutation()
        _uiState.update { current ->
            val updated = (current.appProfileRules.filter { it.packageName != rule.packageName } + rule)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
            current.copy(appProfileRules = updated)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.saveAppProfileRule(rule)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Aturan profil untuk ${rule.appName} disimpan!") }
                if (_uiState.value.isAppAutomationActive || com.noir.lynx.service.LynxAppAutomationService.isRunning) {
                    val intent = android.content.Intent(context, com.noir.lynx.service.LynxAppAutomationService::class.java).apply {
                        action = com.noir.lynx.service.LynxAppAutomationService.ACTION_RELOAD_RULES
                    }
                    context.startService(intent)
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menyimpan aturan aplikasi") }
            }
        }
    }


    fun LynxViewModel.deleteAppProfileRule(context: android.content.Context, packageName: String) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(appProfileRules = current.appProfileRules.filter { it.packageName != packageName })
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.deleteAppProfileRule(packageName)
            if (ok) {
                _uiState.update { it.copy(successMessage = "Aturan aplikasi dihapus") }
                if (_uiState.value.isAppAutomationActive || com.noir.lynx.service.LynxAppAutomationService.isRunning) {
                    val intent = android.content.Intent(context, com.noir.lynx.service.LynxAppAutomationService::class.java).apply {
                        action = com.noir.lynx.service.LynxAppAutomationService.ACTION_RELOAD_RULES
                    }
                    context.startService(intent)
                }
            }
        }
    }


    fun LynxViewModel.toggleAppProfileRule(context: android.content.Context, packageName: String, enabled: Boolean) {
        recordStateMutation()
        _uiState.update { current ->
            current.copy(appProfileRules = current.appProfileRules.map {
                if (it.packageName == packageName) it.copy(enabled = enabled) else it
            })
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.toggleAppProfileRule(packageName, enabled)
            if (ok) {
                if (_uiState.value.isAppAutomationActive || com.noir.lynx.service.LynxAppAutomationService.isRunning) {
                    val intent = android.content.Intent(context, com.noir.lynx.service.LynxAppAutomationService::class.java).apply {
                        action = com.noir.lynx.service.LynxAppAutomationService.ACTION_RELOAD_RULES
                    }
                    context.startService(intent)
                }
            }
        }
    }

    fun LynxViewModel.clearAllAppProfileRules(context: android.content.Context) {
        recordStateMutation()
        _uiState.update { it.copy(appProfileRules = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = LynxRepository.clearAllAppProfileRules()
            if (ok) {
                _uiState.update { it.copy(successMessage = "Semua aturan profil aplikasi berhasil dihapus") }
                if (_uiState.value.isAppAutomationActive || com.noir.lynx.service.LynxAppAutomationService.isRunning) {
                    val intent = android.content.Intent(context, com.noir.lynx.service.LynxAppAutomationService::class.java).apply {
                        action = com.noir.lynx.service.LynxAppAutomationService.ACTION_RELOAD_RULES
                    }
                    context.startService(intent)
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Gagal menghapus aturan aplikasi") }
            }
        }
    }

    fun LynxViewModel.autoDetectAndSyncInstalledRules(context: android.content.Context) {
        recordStateMutation()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanningInstalledGames = true) }
            try {
                val (addedCount, updatedRules) = LynxRepository.autoDetectAndSyncInstalledRules()
                val sorted = updatedRules.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
                _uiState.update { it.copy(isScanningInstalledGames = false, appProfileRules = sorted) }
                if (addedCount > 0) {
                    _uiState.update { it.copy(successMessage = "$addedCount game terpasang berhasil ditambahkan!") }
                    if (_uiState.value.isAppAutomationActive || com.noir.lynx.service.LynxAppAutomationService.isRunning) {
                        val intent = android.content.Intent(context, com.noir.lynx.service.LynxAppAutomationService::class.java).apply {
                            action = com.noir.lynx.service.LynxAppAutomationService.ACTION_RELOAD_RULES
                        }
                        context.startService(intent)
                    }
                } else {
                    _uiState.update { it.copy(successMessage = "Semua game terpasang sudah terdaftar") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanningInstalledGames = false, errorMessage = "Gagal memindai game: ${e.message}") }
            }
        }
    }

    fun LynxViewModel.setAppAutomationCooldownSeconds(context: android.content.Context, seconds: Int) {
        _uiState.update { it.copy(appAutomationCooldownSeconds = seconds) }
        context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("app_cooldown_seconds", seconds)
            .apply()

        viewModelScope.launch(Dispatchers.IO) {
            com.topjohnwu.superuser.Shell.cmd(
                "echo '$seconds' > /data/adb/lynx/cooldown_duration 2>/dev/null; chmod 644 /data/adb/lynx/cooldown_duration 2>/dev/null"
            ).submit()
        }

        val label = if (seconds == 0) context.getString(R.string.audit_persistent)
            else context.resources.getQuantityString(R.plurals.audit_cooldown_seconds, seconds, seconds)
        _uiState.update { it.copy(successMessage = context.getString(R.string.audit_cooldown_feedback, label)) }
    }

    fun LynxViewModel.loadAppAutomationCooldown(context: android.content.Context) {
        val prefs = context.getSharedPreferences("lynx_hud_prefs", android.content.Context.MODE_PRIVATE)
        val defaultSec = prefs.getInt("app_cooldown_seconds", 30)
        _uiState.update { it.copy(appAutomationCooldownSeconds = defaultSec) }
    }

    fun LynxViewModel.refreshMountXStorageInfo() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val (dataTotalBytes, dataAvailBytes) = runCatching {
                val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
                stat.totalBytes to stat.availableBytes
            }.getOrDefault(0L to 0L)

            val dataFsType = runCatching {
                java.io.File("/proc/mounts").useLines { lines ->
                    lines.firstNotNullOfOrNull { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 3 && parts[1] == "/data") parts[2] else null
                    }
                }.orEmpty()
            }.getOrDefault("")

            var baseInfo = MountXStorageInfo(
                internalFsType = dataFsType,
                internalTotalBytes = dataTotalBytes,
                internalAvailableBytes = dataAvailBytes
            )

            val path = LynxRepository.getLynxdPath()
            val result = com.topjohnwu.superuser.Shell.cmd("'$path' io mountx-info 2>/dev/null").exec()
            val out = result.out.joinToString("\n").trim()
            if (out.startsWith("{") && out.endsWith("}")) {
                try {
                    val json = org.json.JSONObject(out)
                    val pidsArray = json.optJSONArray("mmcqd_pids")
                    val pidsList = mutableListOf<Int>()
                    if (pidsArray != null) {
                        for (i in 0 until pidsArray.length()) {
                            pidsList.add(pidsArray.getInt(i))
                        }
                    }
                    val managedArray = json.optJSONArray("managed_disks")
                    val managedList = mutableListOf<String>()
                    if (managedArray != null) {
                        for (i in 0 until managedArray.length()) {
                            val d = managedArray.optString(i)
                            if (d.isNotBlank()) managedList.add(d)
                        }
                    }
                    baseInfo = MountXStorageInfo(
                        mountxInstalled = json.optBoolean("mountx_installed", false),
                        moduleInstalled = json.optBoolean("module_installed", false),
                        appInstalled = json.optBoolean("app_installed", false),
                        mountxMode = json.optString("mountx_mode", "none"),
                        moduleDir = json.optString("module_dir", ""),
                        moduleVersion = json.optString("module_version", ""),
                        ioTweaksActive = json.optBoolean("io_tweaks_active", false),
                        overrideUnlocked = json.optBoolean("override_unlocked", false),
                        sdBase = json.optString("sd_base", ""),
                        sdBlock = json.optString("sd_block", ""),
                        configuredReadAheadKb = if (json.isNull("configured_read_ahead_kb")) null else json.optInt("configured_read_ahead_kb"),
                        configuredScheduler = json.optString("configured_scheduler", ""),
                        configuredNrRequests = if (json.isNull("configured_nr_requests")) null else json.optInt("configured_nr_requests"),
                        configuredRqAffinity = if (json.isNull("configured_rq_affinity")) null else json.optInt("configured_rq_affinity"),
                        managedDisks = managedList,
                        activeBindCount = json.optInt("active_bind_count", 0),
                        isDetected = json.optBoolean("is_detected", false),
                        mountPoint = json.optString("mount_point", ""),
                        partitionName = json.optString("partition_name", ""),
                        parentDevice = json.optString("parent_device", ""),
                        fsType = json.optString("fs_type", ""),
                        f2fsSysfsExists = json.optBoolean("f2fs_sysfs_exists", false),
                        mmcqdPids = pidsList,
                        currentReadAheadKb = json.optInt("current_read_ahead_kb", 0),
                        currentScheduler = json.optString("current_scheduler", ""),
                        dirtyBackgroundBytes = json.optLong("dirty_background_bytes", 0L),
                        dirtyBytes = json.optLong("dirty_bytes", 0L),
                        isOptimized = json.optBoolean("is_optimized", false),
                        targetReadAheadKb = if (json.isNull("target_read_ahead_kb")) null else json.optInt("target_read_ahead_kb"),
                        isPrimaryEmmc = json.optBoolean("is_primary_emmc", false),
                        primaryStorageType = json.optString("primary_storage_type", ""),
                        emmcName = json.optString("emmc_name", ""),
                        emmcLifeTime = json.optString("emmc_life_time", ""),
                        internalFsType = dataFsType,
                        internalTotalBytes = dataTotalBytes,
                        internalAvailableBytes = dataAvailBytes
                    )
                } catch (e: Exception) {
                    android.util.Log.e("LynxAutomation", "Error parsing mountx info: ${e.message}")
                }
            } else {
                // Standalone fallback without lynxd: probe primary eMMC / UFS + MountX presence safely
                val fallback = com.topjohnwu.superuser.Shell.cmd("""
                    chip=""
                    life=""
                    ptype=""
                    if [ -d /sys/block/mmcblk0/device ] && [ -f /sys/block/mmcblk0/device/ext_csd ]; then
                        ptype="eMMC 5.1 Flash"
                        chip=${'$'}(cat /sys/block/mmcblk0/device/name 2>/dev/null)
                        life=${'$'}(cat /sys/block/mmcblk0/device/life_time 2>/dev/null)
                    elif [ -d /sys/block/sda/device ] || [ -d /sys/block/sdc/device ]; then
                        ptype="UFS High-Throughput"
                    fi
                    mx_mod=0
                    for d in /data/adb/modules/MountX /data/adb/modules/mountx /data/adb/modules/Mountify /data/adb/ksu/modules/MountX /data/adb/ap/modules/MountX; do
                        if [ -d "${'$'}d" ] && [ ! -e "${'$'}d/disable" ] && [ ! -e "${'$'}d/remove" ]; then mx_mod=1; break; fi
                    done
                    mx_app=0
                    if [ -d /data/data/app.mountx ] || [ -d /data/user/0/app.mountx ] || [ -d /dev/mountx_bin ]; then mx_app=1; fi
                    mx_ovr=0
                    [ -f /dev/lynx_mountx_override ] && mx_ovr=1
                    echo "${'$'}ptype|${'$'}chip|${'$'}life|${'$'}mx_mod|${'$'}mx_app|${'$'}mx_ovr"
                """.trimIndent()).exec().out.firstOrNull()?.split("|")
                val fbMod = fallback?.getOrNull(3)?.trim() == "1"
                val fbApp = fallback?.getOrNull(4)?.trim() == "1"
                val fbOvr = fallback?.getOrNull(5)?.trim() == "1"
                baseInfo = baseInfo.copy(
                    primaryStorageType = fallback?.getOrNull(0)?.trim().orEmpty(),
                    emmcName = fallback?.getOrNull(1)?.trim().orEmpty(),
                    emmcLifeTime = fallback?.getOrNull(2)?.trim().orEmpty(),
                    isPrimaryEmmc = fallback?.getOrNull(0)?.contains("eMMC", ignoreCase = true) == true,
                    moduleInstalled = fbMod,
                    appInstalled = fbApp,
                    mountxInstalled = fbMod || fbApp,
                    mountxMode = when {
                        fbMod && fbApp -> "module+app"
                        fbMod -> "module"
                        fbApp -> "app"
                        else -> "none"
                    },
                    overrideUnlocked = fbOvr,
                    ioTweaksActive = fbMod || fbApp
                )
            }

            _uiState.update { it.copy(mountXStorageInfo = baseInfo) }
        }
    }

    fun LynxViewModel.triggerMountXBoost(context: Context, boost: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val info = _uiState.value.mountXStorageInfo
            if (info.mountxInstalled && info.ioTweaksActive && !info.overrideUnlocked) {
                _uiState.update {
                    it.copy(errorMessage = "Disk dikelola MountX. Aktifkan Override Lanjutan terlebih dahulu untuk mengubah dari Lynx.")
                }
                return@launch
            }
            val path = LynxRepository.getLynxdPath()
            val cmd = if (boost) {
                "'$path' io mountx-tune"
            } else {
                "'$path' io mountx-restore"
            }
            val res = com.topjohnwu.superuser.Shell.cmd(cmd).exec()
            val result = runCatching { org.json.JSONObject(res.out.joinToString("\n")) }.getOrNull()
            val ok = res.isSuccess && result?.optBoolean("ok", false) == true
            val message = when {
                !ok -> AppLocale.getString(context, R.string.storage_policy_failed)
                !boost -> AppLocale.getString(context, R.string.storage_policy_restored)
                result?.optInt("changed", 0) == 0 -> AppLocale.getString(context, R.string.storage_policy_unchanged)
                else -> AppLocale.getString(context, R.string.storage_policy_applied)
            }
            refreshMountXStorageInfo()
            refreshIoDevices(forceRefresh = true)
            _uiState.update { if (ok) it.copy(successMessage = message) else it.copy(errorMessage = message) }
        }
    }

