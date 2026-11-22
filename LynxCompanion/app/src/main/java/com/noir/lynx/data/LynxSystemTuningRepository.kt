package com.noir.lynx.data
import android.os.StatFs
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
internal object LynxSystemTuningRepository {
    @Volatile
    private var cachedKsmStats: KsmStats? = null
    @Volatile
    private var cachedIoDevices: List<IoDeviceInfo>? = null
    @Volatile
    private var cachedStorageVolumes: List<StorageVolumeInfo>? = null
    @Volatile
    private var cachedTcpAlgos: List<String>? = null
    @Volatile
    private var cachedTcpCurrent: String? = null

    suspend fun readKsmStats(forceRefresh: Boolean = false): KsmStats = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedKsmStats != null) {
            return@withContext cachedKsmStats!!
        }
        try {
            val r = Shell.cmd("""
                K=/sys/kernel/mm/ksm
                [ -f "${'$'}K/run" ] && echo "supp:1" || echo "supp:0"
                echo "run:$(cat ${'$'}K/run 2>/dev/null)"
                echo "shared:$(cat ${'$'}K/pages_shared 2>/dev/null)"
                echo "sharing:$(cat ${'$'}K/pages_sharing 2>/dev/null)"
                echo "scan:$(cat ${'$'}K/pages_to_scan 2>/dev/null)"
                echo "sleep:$(cat ${'$'}K/sleep_millisecs 2>/dev/null)"
            """.trimIndent()).exec()
            var supported = false; var enabled = false; var shared = 0L; var sharing = 0L; var scan = 100; var sleep = 200
            for (line in r.out) {
                val t = line.trim()
                when {
                    t.startsWith("supp:") -> supported = t.removePrefix("supp:").trim() == "1"
                    t.startsWith("run:") -> enabled = t.removePrefix("run:").trim() == "1"
                    t.startsWith("shared:") -> shared = t.removePrefix("shared:").toLongOrNull() ?: 0L
                    t.startsWith("sharing:") -> sharing = t.removePrefix("sharing:").toLongOrNull() ?: 0L
                    t.startsWith("scan:") -> scan = t.removePrefix("scan:").toIntOrNull() ?: 100
                    t.startsWith("sleep:") -> sleep = t.removePrefix("sleep:").toIntOrNull() ?: 200
                }
            }
            val savedMb = ((sharing - shared).coerceAtLeast(0L) * 4096L / 1024f / 1024f)
            val stats = KsmStats(supported = supported, enabled = enabled, pagesShared = shared,
                pagesSharing = sharing, savedMb = savedMb, pagesToScan = scan, sleepMs = sleep)
            cachedKsmStats = stats
            stats
        } catch (e: Exception) { Log.e("LynxRepository", "readKsmStats: ${e.message}"); KsmStats() }
    }

    suspend fun setKsmEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val check = Shell.cmd("[ -f /sys/kernel/mm/ksm/run ] && echo 1 || echo 0").exec()
            if (check.out.firstOrNull()?.trim() != "1") return@withContext false

            val v = if (enabled) "1" else "0"
            val cmd = "chmod 644 /sys/kernel/mm/ksm/run 2>/dev/null && echo $v > /sys/kernel/mm/ksm/run 2>/dev/null && [ \"$(cat /sys/kernel/mm/ksm/run 2>/dev/null)\" = \"$v\" ] && echo ok"
            val ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedKsmStats = cachedKsmStats?.copy(enabled = enabled)
            }
            ok
        } catch (e: Exception) { false }
    }

    suspend fun setKsmTunables(pagesToScan: Int, sleepMs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeScan = pagesToScan.coerceIn(10, 10000)
            val safeSleep = sleepMs.coerceIn(5, 10000)
            val ok = Shell.cmd("""
                K=/sys/kernel/mm/ksm
                [ -e "${'$'}K/pages_to_scan" ] && [ -e "${'$'}K/sleep_millisecs" ] || exit 1
                chmod 644 "${'$'}K/pages_to_scan" "${'$'}K/sleep_millisecs" 2>/dev/null
                echo $safeScan > "${'$'}K/pages_to_scan" 2>/dev/null &&
                echo $safeSleep > "${'$'}K/sleep_millisecs" 2>/dev/null &&
                [ "${'$'}(cat ${'$'}K/pages_to_scan 2>/dev/null)" = "$safeScan" ] &&
                [ "${'$'}(cat ${'$'}K/sleep_millisecs 2>/dev/null)" = "$safeSleep" ] &&
                echo ok
            """.trimIndent()).exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedKsmStats = (cachedKsmStats ?: readKsmStats(forceRefresh = true)).copy(
                    pagesToScan = safeScan,
                    sleepMs = safeSleep
                )
            }
            ok
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: I/O Scheduler (per block device)
    // ----------------------------------------------------------------

    suspend fun readIoDevices(forceRefresh: Boolean = false): List<IoDeviceInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedIoDevices != null) {
            return@withContext cachedIoDevices!!
        }
        try {
            val baseList = mutableListOf<IoDeviceInfo>()
            var parsedVolumes: List<StorageVolumeInfo>? = null
            var needQueueHydration = false

            if (LynxRepository.isLynxdAvailable()) {
                val lynxRes = Shell.cmd("'$LYNXD_PATH' io info 2>/dev/null").exec()
                if (lynxRes.isSuccess && lynxRes.out.isNotEmpty()) {
                    val raw = lynxRes.out.joinToString("").trim()
                    if (raw.startsWith("{")) {
                        val root = JSONObject(raw)
                        val arr = root.optJSONArray("devices")
                        if (arr != null && arr.length() > 0) {
                            for (i in 0 until arr.length()) {
                                val d = arr.getJSONObject(i)
                                val name = d.optString("name")
                                if (name.isBlank()) continue
                                val cur = d.optString("current_scheduler", "none")
                                val availJson = d.optJSONArray("available_schedulers")
                                val avail = mutableListOf<String>()
                                if (availJson != null) {
                                    for (j in 0 until availJson.length()) {
                                        val s = availJson.optString(j)
                                        if (s.isNotBlank()) avail.add(s)
                                    }
                                }
                                val targetRa = if (d.has("target_read_ahead_kb") && !d.isNull("target_read_ahead_kb")) {
                                    d.optInt("target_read_ahead_kb", 0).takeIf { it > 0 }
                                } else null
                                val hasNr = d.has("nr_requests")
                                if (!hasNr) needQueueHydration = true
                                baseList.add(
                                    IoDeviceInfo(
                                        device = name,
                                        deviceType = d.optString("type", ""),
                                        group = d.optString("group", ""),
                                        isPrimary = d.optBoolean("is_primary", false),
                                        isReadOnly = d.optBoolean("is_read_only", false),
                                        sizeBytes = d.optLong("size_bytes", 0L),
                                        removable = d.optBoolean("removable", false),
                                        currentScheduler = cur,
                                        availableSchedulers = avail,
                                        readAheadKb = d.optInt("current_read_ahead_kb", 0),
                                        targetReadAheadKb = targetRa,
                                        policy = d.optString("policy", ""),
                                        nrRequests = if (hasNr && !d.isNull("nr_requests")) d.optInt("nr_requests") else null,
                                        iostats = if (d.has("iostats") && !d.isNull("iostats")) d.optInt("iostats") else null,
                                        rqAffinity = if (d.has("rq_affinity") && !d.isNull("rq_affinity")) d.optInt("rq_affinity") else null,
                                        addRandom = if (d.has("add_random") && !d.isNull("add_random")) d.optInt("add_random") else null,
                                        rotational = if (d.has("rotational") && !d.isNull("rotational")) d.optInt("rotational") else null,
                                        mountxManaged = d.optBoolean("mountx_managed", false),
                                        mountxLocked = d.optBoolean("mountx_locked", false),
                                    )
                                )
                            }
                        }

                        val volArr = root.optJSONArray("volumes")
                        if (volArr != null) {
                            val vList = mutableListOf<StorageVolumeInfo>()
                            for (i in 0 until volArr.length()) {
                                val v = volArr.getJSONObject(i)
                                vList.add(
                                    StorageVolumeInfo(
                                        partition = v.optString("partition", ""),
                                        parentDisk = v.optString("parent_disk", ""),
                                        deviceType = v.optString("device_type", ""),
                                        group = v.optString("group", ""),
                                        mountPoint = v.optString("mount_point", ""),
                                        fsType = v.optString("fs_type", ""),
                                        partitionSizeBytes = v.optLong("partition_size_bytes", 0L),
                                        f2fsSysfsExists = v.optBoolean("f2fs_sysfs_exists", false),
                                        isPrimaryInternal = v.optBoolean("is_primary_internal", false),
                                        isMountxTarget = v.optBoolean("is_mountx_target", false),
                                    )
                                )
                            }
                            parsedVolumes = hydrateVolumeCapacities(vList)
                        }
                    }
                }
            }

            if (baseList.isEmpty()) {
                val r = Shell.cmd("""
                    primary_disk=""
                    data_mm=${'$'}(awk '$5 == "/data" {print $3; exit}' /proc/self/mountinfo 2>/dev/null)
                    if [ -n "${'$'}data_mm" ] && [ -e "/sys/dev/block/${'$'}data_mm" ]; then
                      pnode=${'$'}(readlink -f "/sys/dev/block/${'$'}data_mm" 2>/dev/null)
                      [ -e "${'$'}pnode/partition" ] && pnode=${'$'}(dirname "${'$'}pnode")
                      if [ -d "${'$'}pnode/slaves" ]; then
                        first_slave=${'$'}(ls -1 "${'$'}pnode/slaves" 2>/dev/null | head -n 1)
                        if [ -n "${'$'}first_slave" ]; then
                          snode=${'$'}(readlink -f "/sys/class/block/${'$'}first_slave" 2>/dev/null)
                          [ -e "${'$'}snode/partition" ] && snode=${'$'}(dirname "${'$'}snode")
                          primary_disk=${'$'}(basename "${'$'}snode")
                        fi
                      else
                        primary_disk=${'$'}(basename "${'$'}pnode")
                      fi
                    fi
                    for block in /sys/block/*; do
                      [ -d "${'$'}block" ] || continue
                      dev=${'$'}{block##*/}
                      q=${'$'}block/queue
                      [ -f "${'$'}q/scheduler" ] || continue
                      sched=${'$'}(cat "${'$'}q/scheduler" 2>/dev/null)
                      ra=${'$'}(cat "${'$'}q/read_ahead_kb" 2>/dev/null)
                      nr=${'$'}(cat "${'$'}q/nr_requests" 2>/dev/null)
                      ios=${'$'}(cat "${'$'}q/iostats" 2>/dev/null)
                      rqa=${'$'}(cat "${'$'}q/rq_affinity" 2>/dev/null)
                      arnd=${'$'}(cat "${'$'}q/add_random" 2>/dev/null)
                      rot=${'$'}(cat "${'$'}q/rotational" 2>/dev/null)
                      sz=${'$'}(cat "${'$'}block/size" 2>/dev/null)
                      ro=${'$'}(cat "${'$'}block/ro" 2>/dev/null)
                      rem=${'$'}(cat "${'$'}block/removable" 2>/dev/null)
                      real=${'$'}(readlink -f "${'$'}block/device" 2>/dev/null)
                      dtype="unknown"
                      bgrp=""
                      if [ -d "${'$'}block/dm" ] || echo "${'$'}dev" | grep -qE '^dm-'; then
                        dtype="virtual"; bgrp="dm"
                      elif [ -d "${'$'}block/loop" ] || echo "${'$'}dev" | grep -qE '^loop'; then
                        dtype="loop"; bgrp="loop"
                      elif [ -e "${'$'}block/comp_algorithm" ] || [ -e "${'$'}block/mm_stat" ] || echo "${'$'}dev" | grep -qE '^zram'; then
                        dtype="virtual"; bgrp="zram"
                      elif echo "${'$'}dev" | grep -qE '(boot|rpmb)'; then
                        dtype="emmc"; bgrp="hw_boot"
                      elif echo "${'$'}real" | grep -q '/usb'; then
                        dtype="usb"; bgrp="usb"
                      elif [ -e "${'$'}block/device/ext_csd" ] || [ -e "${'$'}block/device/life_time" ] || [ "${'$'}(cat "${'$'}block/device/type" 2>/dev/null)" = "MMC" ]; then
                        dtype="emmc"; bgrp="internal"
                      elif [ -e "${'$'}block/device/scr" ] || [ -e "${'$'}block/device/ssr" ] || [ "${'$'}(cat "${'$'}block/device/type" 2>/dev/null)" = "SD" ] || echo "${'$'}real" | grep -q 'externdevice'; then
                        dtype="sd"; bgrp="sd"
                      elif echo "${'$'}real" | grep -qE 'ufs|host'; then
                        if echo "${'$'}dev" | grep -qE '^sd[a-z]+$'; then dtype="ufs"; bgrp="internal"; fi
                      elif echo "${'$'}dev" | grep -qE '^nvme'; then
                        dtype="nvme"; bgrp="internal"
                        [ "${'$'}rem" = "1" ] && bgrp="external"
                      elif [ "${'$'}rem" = "1" ] && ! echo "${'$'}dev" | grep -qE '^(ram|nbd)'; then
                        dtype="sd"; bgrp="sd"
                      elif echo "${'$'}dev" | grep -qE '^ram'; then
                        dtype="virtual"; bgrp="ram"
                      else
                        dtype="unknown"; bgrp="other"
                      fi
                      is_prim=0
                      if [ -n "${'$'}primary_disk" ]; then
                        [ "${'$'}dev" = "${'$'}primary_disk" ] && is_prim=1
                      elif [ "${'$'}dtype" = "emmc" ] && [ "${'$'}bgrp" = "internal" ]; then
                        is_prim=1
                      fi
                      echo "dev:${'$'}dev|type:${'$'}dtype|grp:${'$'}bgrp|prim:${'$'}is_prim|ro:${'$'}ro|sz:${'$'}sz|rem:${'$'}rem|sched:${'$'}sched|ra:${'$'}ra|nr:${'$'}nr|ios:${'$'}ios|rqa:${'$'}rqa|arnd:${'$'}arnd|rot:${'$'}rot"
                    done
                """.trimIndent()).exec()
                for (line in r.out) {
                    val t = line.trim(); if (!t.startsWith("dev:")) continue
                    val parts = t.split("|")
                    val dev = parts.getOrNull(0)?.removePrefix("dev:") ?: continue
                    val dtype = parts.getOrNull(1)?.removePrefix("type:") ?: ""
                    val bgrp = parts.getOrNull(2)?.removePrefix("grp:") ?: ""
                    val isPrim = parts.getOrNull(3)?.removePrefix("prim:") == "1"
                    val isRo = parts.getOrNull(4)?.removePrefix("ro:") == "1"
                    val sz = (parts.getOrNull(5)?.removePrefix("sz:")?.toLongOrNull() ?: 0L) * 512L
                    val rem = parts.getOrNull(6)?.removePrefix("rem:") == "1"
                    val schedRaw = parts.getOrNull(7)?.removePrefix("sched:") ?: ""
                    val ra = parts.getOrNull(8)?.removePrefix("ra:")?.toIntOrNull() ?: 0
                    val nr = parts.getOrNull(9)?.removePrefix("nr:")?.toIntOrNull()
                    val ios = parts.getOrNull(10)?.removePrefix("ios:")?.toIntOrNull()
                    val rqa = parts.getOrNull(11)?.removePrefix("rqa:")?.toIntOrNull()
                    val arnd = parts.getOrNull(12)?.removePrefix("arnd:")?.toIntOrNull()
                    val rot = parts.getOrNull(13)?.removePrefix("rot:")?.toIntOrNull()
                    val avail = schedRaw.replace("[", "").replace("]", "").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    val cur = Regex("\\[([^]]+)]").find(schedRaw)?.groupValues?.getOrNull(1) ?: avail.firstOrNull() ?: "none"
                    baseList.add(
                        IoDeviceInfo(
                            device = dev,
                            deviceType = dtype,
                            group = bgrp,
                            isPrimary = isPrim,
                            isReadOnly = isRo,
                            sizeBytes = sz,
                            removable = rem,
                            currentScheduler = cur,
                            availableSchedulers = avail,
                            readAheadKb = ra,
                            nrRequests = nr,
                            iostats = ios,
                            rqAffinity = rqa,
                            addRandom = arnd,
                            rotational = rot,
                        )
                    )
                }
                cachedIoDevices = baseList
                cachedStorageVolumes = readMountedVolumesFallback(baseList)
                return@withContext baseList
            }

            if (needQueueHydration) {
                val devNames = baseList.joinToString(" ") { it.device.replace(Regex("[^a-zA-Z0-9_-]"), "") }
                val qRes = Shell.cmd("""
                    for d in $devNames; do
                      q="/sys/block/${'$'}d/queue"
                      [ -d "${'$'}q" ] || continue
                      nr=${'$'}(cat "${'$'}q/nr_requests" 2>/dev/null)
                      ios=${'$'}(cat "${'$'}q/iostats" 2>/dev/null)
                      rqa=${'$'}(cat "${'$'}q/rq_affinity" 2>/dev/null)
                      arnd=${'$'}(cat "${'$'}q/add_random" 2>/dev/null)
                      echo "q:${'$'}d|${'$'}nr|${'$'}ios|${'$'}rqa|${'$'}arnd"
                    done
                """.trimIndent()).exec()
                val qMap = mutableMapOf<String, List<Int?>>()
                for (line in qRes.out) {
                    val t = line.trim(); if (!t.startsWith("q:")) continue
                    val parts = t.removePrefix("q:").split("|")
                    val d = parts.getOrNull(0) ?: continue
                    qMap[d] = listOf(
                        parts.getOrNull(1)?.toIntOrNull(),
                        parts.getOrNull(2)?.toIntOrNull(),
                        parts.getOrNull(3)?.toIntOrNull(),
                        parts.getOrNull(4)?.toIntOrNull()
                    )
                }
                val finalList = baseList.map { info ->
                    val q = qMap[info.device]
                    if (q != null) info.copy(
                        nrRequests = info.nrRequests ?: q.getOrNull(0),
                        iostats = info.iostats ?: q.getOrNull(1),
                        rqAffinity = info.rqAffinity ?: q.getOrNull(2),
                        addRandom = info.addRandom ?: q.getOrNull(3)
                    ) else info
                }
                cachedIoDevices = finalList
                if (parsedVolumes != null) cachedStorageVolumes = parsedVolumes
                return@withContext finalList
            }

            cachedIoDevices = baseList
            if (parsedVolumes != null) cachedStorageVolumes = parsedVolumes
            baseList
        } catch (e: Exception) { Log.e("LynxRepository", "readIoDevices: ${e.message}"); emptyList() }
    }

    suspend fun readStorageVolumes(forceRefresh: Boolean = false): List<StorageVolumeInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedStorageVolumes != null) {
            return@withContext cachedStorageVolumes!!
        }
        val devs = readIoDevices(forceRefresh = forceRefresh)
        cachedStorageVolumes ?: readMountedVolumesFallback(devs).also { cachedStorageVolumes = it }
    }

    private fun hydrateVolumeCapacities(volumes: List<StorageVolumeInfo>): List<StorageVolumeInfo> {
        if (volumes.isEmpty()) return volumes
        val statResolved = volumes.map { vol ->
            if (vol.mountPoint.isBlank()) return@map vol
            val (tot, avail) = runCatching {
                val stat = StatFs(vol.mountPoint)
                stat.totalBytes to stat.availableBytes
            }.getOrDefault(0L to 0L)
            vol.copy(totalBytes = tot, availBytes = avail)
        }
        val unresolved = statResolved.filter { it.totalBytes <= 0L && it.mountPoint.isNotBlank() }
        if (unresolved.isEmpty()) return statResolved

        val args = unresolved.joinToString(" ") { "'${it.mountPoint.replace("'", "'\\''")}'" }
        val dfMap = mutableMapOf<String, Pair<Long, Long>>()
        runCatching {
            val out = Shell.cmd("df -kP $args 2>/dev/null || df -k $args 2>/dev/null").exec().out
            for (line in out) {
                val cols = line.trim().split(Regex("\\s+"))
                if (cols.size >= 6 && cols[0] != "Filesystem") {
                    val totKb = cols[1].toLongOrNull() ?: continue
                    val availKb = cols[3].toLongOrNull() ?: continue
                    val mnt = cols.drop(5).joinToString(" ")
                    dfMap[mnt] = (totKb * 1024L) to (availKb * 1024L)
                }
            }
        }
        return statResolved.map { vol ->
            if (vol.totalBytes > 0L) vol
            else {
                val df = dfMap[vol.mountPoint]
                if (df != null) vol.copy(totalBytes = df.first, availBytes = df.second)
                else if (vol.partitionSizeBytes > 0L) vol.copy(totalBytes = vol.partitionSizeBytes)
                else vol
            }
        }
    }

    private fun readMountedVolumesFallback(devices: List<IoDeviceInfo>): List<StorageVolumeInfo> {
        val raw = mutableListOf<StorageVolumeInfo>()
        runCatching {
            val res = Shell.cmd("""
                awk -F' - ' '{
                  split($1, m, " "); split($2, f, " ");
                  if (m[4] == "/" && m[5] != "/" && m[5] !~ /^\/(system|vendor|product|odm|apex|bootstrap-apex|linkerconfig|data_mirror|storage\/emulated)/ && m[5] !~ /^\/mnt\/(runtime|installer|androidwritable|pass_through|user)\//) {
                    print m[3] "|" m[5] "|" f[1] "|" f[2]
                  }
                }' /proc/self/mountinfo 2>/dev/null | while IFS='|' read -r mm mnt fst src; do
                  sb=""
                  if [ -e "/sys/dev/block/${'$'}mm" ]; then sb="/sys/dev/block/${'$'}mm"
                  elif echo "${'$'}src" | grep -q "/vold/public:"; then
                    vmm=${'$'}(echo "${'$'}src" | sed 's/.*public://; s/,/:/')
                    [ -e "/sys/dev/block/${'$'}vmm" ] && sb="/sys/dev/block/${'$'}vmm"
                  fi
                  [ -z "${'$'}sb" ] && continue
                  part=${'$'}(basename ${'$'}(readlink -f "${'$'}sb" 2>/dev/null))
                  pnode=${'$'}(readlink -f "${'$'}sb" 2>/dev/null)
                  [ -e "${'$'}pnode/partition" ] && pnode=${'$'}(dirname "${'$'}pnode")
                  if [ -d "${'$'}pnode/slaves" ]; then
                    sl=${'$'}(ls -1 "${'$'}pnode/slaves" 2>/dev/null | head -n 1)
                    if [ -n "${'$'}sl" ]; then
                      snode=${'$'}(readlink -f "/sys/class/block/${'$'}sl" 2>/dev/null)
                      [ -e "${'$'}snode/partition" ] && snode=${'$'}(dirname "${'$'}snode")
                      disk=${'$'}(basename "${'$'}snode")
                    else disk=${'$'}(basename "${'$'}pnode"); fi
                  else disk=${'$'}(basename "${'$'}pnode"); fi
                  [ -d "/sys/block/${'$'}disk/loop" ] || [ -d "/sys/block/${'$'}disk/dm" ] && [ "${'$'}mnt" != "/data" ] && continue
                  echo "${'$'}disk" | grep -qE '^(loop|zram|ram|nbd)' && continue
                  psz=${'$'}(cat "/sys/class/block/${'$'}part/size" 2>/dev/null)
                  f2fs=0; [ -d "/sys/fs/f2fs/${'$'}part" ] && f2fs=1
                  echo "vol:${'$'}part|${'$'}disk|${'$'}mnt|${'$'}fst|${'$'}psz|${'$'}f2fs"
                done
            """.trimIndent()).exec()
            for (line in res.out) {
                val t = line.trim(); if (!t.startsWith("vol:")) continue
                val p = t.removePrefix("vol:").split("|")
                val part = p.getOrNull(0) ?: continue
                val disk = p.getOrNull(1) ?: ""
                val mnt = p.getOrNull(2)?.replace("\\040", " ") ?: continue
                val fst = p.getOrNull(3) ?: ""
                val psz = (p.getOrNull(4)?.toLongOrNull() ?: 0L) * 512L
                val f2fs = p.getOrNull(5) == "1"
                val devInfo = devices.firstOrNull { it.device == disk }
                val grp = devInfo?.resolvedGroup().orEmpty()
                if (mnt != "/data" && grp !in listOf("sd", "usb", "external")) continue
                raw.add(
                    StorageVolumeInfo(
                        partition = part,
                        parentDisk = disk,
                        deviceType = devInfo?.deviceType.orEmpty(),
                        group = grp,
                        mountPoint = mnt,
                        fsType = fst,
                        partitionSizeBytes = psz,
                        f2fsSysfsExists = f2fs,
                        isPrimaryInternal = mnt == "/data",
                        isMountxTarget = false,
                    )
                )
            }
        }
        return hydrateVolumeCapacities(raw.distinctBy { "${it.parentDisk}:${it.partition}:${it.mountPoint}" })
    }

    suspend fun setIoScheduler(device: String, scheduler: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val d = device.replace(Regex("[^a-zA-Z0-9_-]"), "")
            val s = scheduler.replace(Regex("[^a-zA-Z0-9\\-]"), "")
            var ok = false
            if (LynxRepository.isLynxdAvailable()) {
                val cmd = "'$LYNXD_PATH' io set-scheduler '$d' '$s' 2>/dev/null"
                val res = Shell.cmd(cmd).exec()
                val json = res.out.firstOrNull { it.trim().startsWith("{") }
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                if (json != null) {
                    ok = json.optBoolean("ok", false) && (json.optInt("updated", 0) + json.optInt("unchanged", 0)) > 0
                }
            }
            if (!ok) {
                val cmd = "if [ -f '/sys/block/$d/queue/scheduler' ]; then echo '$s' > '/sys/block/$d/queue/scheduler' 2>/dev/null && grep -q '\\[$s\\]' '/sys/block/$d/queue/scheduler' && echo ok; fi"
                ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            }
            if (ok) {
                cachedIoDevices = cachedIoDevices?.map {
                    if (it.device == d) it.copy(currentScheduler = s) else it
                }
            }
            ok
        } catch (e: Exception) { false }
    }

    suspend fun setReadAheadKb(device: String, kb: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val d = device.replace(Regex("[^a-zA-Z0-9_-]"), "")
            val cmd = "if [ -f '/sys/block/$d/queue/read_ahead_kb' ]; then echo $kb > '/sys/block/$d/queue/read_ahead_kb' 2>/dev/null && [ \"$(cat /sys/block/$d/queue/read_ahead_kb 2>/dev/null)\" = \"$kb\" ] && echo ok; fi"
            val ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedIoDevices = cachedIoDevices?.map {
                    if (it.device == d) it.copy(readAheadKb = kb) else it
                }
            }
            ok
        } catch (e: Exception) { false }
    }

    suspend fun setIoQueueTunable(device: String, key: String, value: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val d = device.replace(Regex("[^a-zA-Z0-9_-]"), "")
            val k = when (key) {
                "nr_requests", "iostats", "rq_affinity", "add_random" -> key
                else -> return@withContext false
            }
            val cmd = "if [ -w '/sys/block/$d/queue/$k' ]; then echo $value > '/sys/block/$d/queue/$k' 2>/dev/null && [ \"$(cat /sys/block/$d/queue/$k 2>/dev/null)\" = \"$value\" ] && echo ok; fi"
            val ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            if (ok) {
                cachedIoDevices = cachedIoDevices?.map { info ->
                    if (info.device != d) info else when (k) {
                        "nr_requests" -> info.copy(nrRequests = value)
                        "iostats" -> info.copy(iostats = value)
                        "rq_affinity" -> info.copy(rqAffinity = value)
                        "add_random" -> info.copy(addRandom = value)
                        else -> info
                    }
                }
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun tuneBlockGroup(
        groupOrTarget: String,
        memberDevices: List<String>,
        param: String,
        value: String
    ): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        try {
            val safeParam = when (param) {
                "scheduler", "read_ahead_kb", "nr_requests", "rq_affinity", "iostats", "add_random" -> param
                else -> return@withContext false to 0
            }
            val cleanTarget = groupOrTarget.replace(Regex("[^a-zA-Z0-9_,-]"), "")
            val cleanVal = value.replace(Regex("[^a-zA-Z0-9-]"), "")
            if (cleanTarget.isBlank() || cleanVal.isBlank()) return@withContext false to 0

            val ok: Boolean
            val count: Int
            if (LynxRepository.isLynxdAvailable()) {
                val res = Shell.cmd("'$LYNXD_PATH' io group-tune '$cleanTarget' '$safeParam' '$cleanVal' 2>/dev/null").exec()
                val json = res.out.firstOrNull { it.trim().startsWith("{") }
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                if (json != null && json.has("updated")) {
                    val parsedOk = json.optBoolean("ok", false)
                    val parsedCount = json.optInt("updated", 0) + json.optInt("unchanged", 0)
                    if (parsedOk && parsedCount > 0) {
                        cachedIoDevices = null
                        return@withContext true to parsedCount
                    }
                }
            }

            // Fallback direct batch write with read-back verification per member block
            val devList = memberDevices.map { it.replace(Regex("[^a-zA-Z0-9_-]"), "") }.filter { it.isNotBlank() }
            if (devList.isEmpty()) return@withContext false to 0
            val argStr = devList.joinToString(" ")
            val batchCmd = if (safeParam == "scheduler") {
                """
                    ok_cnt=0
                    for d in $argStr; do
                      p="/sys/block/${'$'}d/queue/scheduler"
                      [ -w "${'$'}p" ] || continue
                      echo "$cleanVal" > "${'$'}p" 2>/dev/null
                      grep -q "\[$cleanVal\]" "${'$'}p" 2>/dev/null && ok_cnt=$((ok_cnt + 1))
                    done
                    echo "cnt:${'$'}ok_cnt"
                """.trimIndent()
            } else {
                """
                    ok_cnt=0
                    for d in $argStr; do
                      p="/sys/block/${'$'}d/queue/$safeParam"
                      [ -w "${'$'}p" ] || continue
                      echo "$cleanVal" > "${'$'}p" 2>/dev/null
                      [ "${'$'}(cat "${'$'}p" 2>/dev/null)" = "$cleanVal" ] && ok_cnt=$((ok_cnt + 1))
                    done
                    echo "cnt:${'$'}ok_cnt"
                """.trimIndent()
            }
            val res = Shell.cmd(batchCmd).exec().out.firstOrNull { it.startsWith("cnt:") }
            count = res?.removePrefix("cnt:")?.trim()?.toIntOrNull() ?: 0
            ok = count > 0
            cachedIoDevices = null
            ok to count
        } catch (_: Exception) { false to 0 }
    }

    suspend fun applyBlockGroupPreset(
        groupId: String,
        devices: List<IoDeviceInfo>,
        preset: String
    ): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        val unlocked = devices.filter { !it.mountxLocked && !it.isReadOnly }
        if (unlocked.isEmpty()) return@withContext false to 0
        val names = unlocked.map { it.device }
        val target = if (groupId in listOf("internal", "sd", "usb", "external", "dm", "loop", "zram", "hw_boot", "ram", "other") && unlocked.size == devices.size) {
            groupId
        } else {
            names.joinToString(",")
        }

        val hasEmmc = unlocked.any { it.deviceType.equals("emmc", ignoreCase = true) } &&
            unlocked.none { it.deviceType.equals("ufs", ignoreCase = true) }

        val (ra, nr, rqa, schedOrder) = when (groupId) {
            "internal" -> if (hasEmmc) {
                when (preset) {
                    "powersave" -> listOf(64, 32, 1, listOf("bfq", "mq-deadline", "deadline", "cfq", "none"))
                    "performance" -> listOf(128, 64, 1, listOf("bfq", "mq-deadline", "deadline", "none"))
                    else -> listOf(128, 64, 1, listOf("bfq", "mq-deadline", "deadline", "none"))
                }
            } else {
                when (preset) {
                    "powersave" -> listOf(128, 64, 1, listOf("mq-deadline", "deadline", "none"))
                    "performance" -> listOf(512, 256, 2, listOf("kyber", "mq-deadline", "deadline", "none"))
                    else -> listOf(256, 128, 2, listOf("mq-deadline", "deadline", "kyber", "none"))
                }
            }
            "sd" -> when (preset) {
                "powersave" -> listOf(256, 64, 1, listOf("mq-deadline", "deadline", "bfq", "noop", "none"))
                "performance" -> listOf(1024, 256, 2, listOf("deadline", "mq-deadline", "none", "bfq"))
                else -> listOf(512, 128, 2, listOf("mq-deadline", "deadline", "bfq", "none"))
            }
            "usb" -> when (preset) {
                "powersave" -> listOf(256, 64, 1, listOf("mq-deadline", "deadline", "none"))
                "performance" -> listOf(1024, 128, 2, listOf("none", "mq-deadline", "deadline"))
                else -> listOf(512, 128, 2, listOf("mq-deadline", "deadline", "none"))
            }
            "dm" -> when (preset) {
                "powersave" -> listOf(128, 64, 1, listOf("none"))
                "performance" -> listOf(512, 128, 2, listOf("none"))
                else -> listOf(256, 128, 1, listOf("none"))
            }
            "loop" -> when (preset) {
                "powersave" -> listOf(128, 64, 1, listOf("mq-deadline", "none"))
                "performance" -> listOf(512, 128, 2, listOf("none", "mq-deadline"))
                else -> listOf(256, 128, 1, listOf("mq-deadline", "none"))
            }
            "zram" -> listOf(0, 64, 1, listOf("none"))
            else -> listOf(64, 64, 1, listOf("none"))
        }

        val raInt = ra as Int
        val nrInt = nr as Int
        val rqaInt = rqa as Int
        @Suppress("UNCHECKED_CAST")
        val candidates = schedOrder as List<String>

        val commonScheds = unlocked.map { it.availableSchedulers.toSet() }
            .reduceOrNull { acc, s -> acc intersect s }
            ?: unlocked.flatMap { it.availableSchedulers }.toSet()
        val chosenSched = candidates.firstOrNull { it in commonScheds }

        var appliedSteps = 0
        var totalAffected = 0
        if (!chosenSched.isNullOrBlank()) {
            val (okS, cS) = tuneBlockGroup(target, names, "scheduler", chosenSched)
            if (okS) { appliedSteps++; totalAffected = maxOf(totalAffected, cS) }
        }
        val (okRa, cRa) = tuneBlockGroup(target, names, "read_ahead_kb", raInt.toString())
        if (okRa) { appliedSteps++; totalAffected = maxOf(totalAffected, cRa) }
        val (okNr, cNr) = tuneBlockGroup(target, names, "nr_requests", nrInt.toString())
        if (okNr) { appliedSteps++; totalAffected = maxOf(totalAffected, cNr) }
        val (okRq, _) = tuneBlockGroup(target, names, "rq_affinity", rqaInt.toString())
        if (okRq) appliedSteps++
        val (okIos, _) = tuneBlockGroup(target, names, "iostats", if (preset == "performance") "0" else "1")
        if (okIos) appliedSteps++

        cachedIoDevices = null
        (appliedSteps > 0) to totalAffected
    }

    suspend fun setMountXOverride(unlock: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            var verified = false
            if (LynxRepository.isLynxdAvailable()) {
                val res = Shell.cmd("'$LYNXD_PATH' io mountx-override ${if (unlock) 1 else 0} 2>/dev/null").exec()
                val json = res.out.firstOrNull { it.trim().startsWith("{") }
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                if (json != null && json.optBoolean("ok", false)) {
                    verified = json.optBoolean("override_unlocked", !unlock) == unlock
                }
            }
            if (!verified) {
                val cmd = if (unlock) {
                    "touch /dev/lynx_mountx_override 2>/dev/null && [ -f /dev/lynx_mountx_override ] && echo ok"
                } else {
                    "rm -f /dev/lynx_mountx_override 2>/dev/null && [ ! -f /dev/lynx_mountx_override ] && echo ok"
                }
                verified = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            }
            cachedIoDevices = null
            verified
        } catch (_: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: TCP Congestion Control
    // ----------------------------------------------------------------

    suspend fun readAvailableTcpAlgorithms(forceRefresh: Boolean = false): List<String> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedTcpAlgos != null) {
            return@withContext cachedTcpAlgos!!
        }
        try {
            val direct = java.io.File("/proc/sys/net/ipv4/tcp_available_congestion_control")
            if (direct.exists() && direct.canRead()) {
                val algos = direct.readText().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (algos.isNotEmpty()) {
                    cachedTcpAlgos = algos
                    return@withContext algos
                }
            }
            if (LynxRepository.isModuleInstalled()) {
                val lynxRes = Shell.cmd("if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' network info 2>/dev/null; fi").exec()
                if (lynxRes.isSuccess && lynxRes.out.isNotEmpty()) {
                    val raw = lynxRes.out.joinToString("").trim()
                    if (raw.startsWith("{")) {
                        val root = JSONObject(raw)
                        val arr = root.optJSONArray("available_algos")
                        if (arr != null && arr.length() > 0) {
                            val list = mutableListOf<String>()
                            for (i in 0 until arr.length()) list.add(arr.optString(i))
                            cachedTcpAlgos = list
                            return@withContext list
                        }
                    }
                }
            }
            val algos = Shell.cmd("cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null")
                .exec().out.firstOrNull()?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
            cachedTcpAlgos = algos
            algos
        } catch (e: Exception) { emptyList() }
    }

    suspend fun readCurrentTcpCongestion(forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedTcpCurrent != null) {
            return@withContext cachedTcpCurrent!!
        }
        try {
            val direct = java.io.File("/proc/sys/net/ipv4/tcp_congestion_control")
            if (direct.exists() && direct.canRead()) {
                val cur = direct.readText().trim()
                if (cur.isNotBlank()) {
                    cachedTcpCurrent = cur
                    return@withContext cur
                }
            }
            if (LynxRepository.isModuleInstalled()) {
                val lynxRes = Shell.cmd("if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' network info 2>/dev/null; fi").exec()
                if (lynxRes.isSuccess && lynxRes.out.isNotEmpty()) {
                    val raw = lynxRes.out.joinToString("").trim()
                    if (raw.startsWith("{")) {
                        val root = JSONObject(raw)
                        val cur = root.optString("current_algo", "")
                        if (cur.isNotBlank()) {
                            cachedTcpCurrent = cur
                            return@withContext cur
                        }
                    }
                }
            }
            val cur = Shell.cmd("cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null")
                .exec().out.firstOrNull()?.trim() ?: ""
            cachedTcpCurrent = cur
            cur
        } catch (_: Exception) { "" }
    }

    suspend fun setTcpCongestion(algorithm: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safe = algorithm.replace(Regex("[^a-zA-Z0-9_]"), "")
            var ok = false
            if (LynxRepository.isModuleInstalled()) {
                val cmd = "if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' network set-algo '$safe' && echo ok; fi"
                val res = Shell.cmd(cmd).exec()
                if (res.out.any { it.contains("ok") || it.contains("set to") }) ok = true
            }
            if (!ok) {
                val cmd = "echo '$safe' > /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null && [ \"$(cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null)\" = \"$safe\" ] && echo ok"
                ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            }
            if (ok) {
                cachedTcpCurrent = safe
            }
            ok
        } catch (e: Exception) { false }
    }


    // ----------------------------------------------------------------
    //  Dynamic Governor Tunables Discovery (Zero-Hardcoding)
    // ----------------------------------------------------------------

    suspend fun readGovernorTunables(policyId: Int, governor: String): List<GovernorTunable> = withContext(Dispatchers.IO) {
        try {
            val safeGov = governor.replace(Regex("[^a-zA-Z0-9_]"), "")
            if (safeGov.isBlank() || safeGov == "performance" || safeGov == "powersave" || safeGov == "userspace") {
                return@withContext emptyList()
            }
            val script = """
                d="/sys/devices/system/cpu/cpufreq/policy$policyId/$safeGov"
                [ ! -d "${'$'}d" ] && d="/sys/devices/system/cpu/cpufreq/$safeGov"
                [ ! -d "${'$'}d" ] && d="/sys/devices/system/cpu/cpufreq/schedutil"
                if [ -d "${'$'}d" ]; then
                    for f in "${'$'}d"/*; do
                        [ -f "${'$'}f" ] || continue
                        k=${'$'}(basename "${'$'}f")
                        v=${'$'}(cat "${'$'}f" 2>/dev/null | head -n1 | tr -d '\r\n')
                        [ -n "${'$'}v" ] && echo "${'$'}k:${'$'}v"
                    done
                fi
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            if (!r.isSuccess || r.out.isEmpty()) return@withContext emptyList()

            val list = mutableListOf<GovernorTunable>()
            for (line in r.out) {
                val idx = line.indexOf(':')
                if (idx < 1) continue
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()

                val displayName = key.split("_").joinToString(" ") { word ->
                    when (word.lowercase()) {
                        "us" -> "µs"
                        "ms" -> "ms"
                        "ns" -> "ns"
                        "khz" -> "kHz"
                        "pct" -> "%"
                        "freq" -> "Freq"
                        else -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                }

                val unit = when {
                    key.endsWith("_us") -> "µs"
                    key.endsWith("_ms") -> "ms"
                    key.endsWith("_ns") -> "ns"
                    key.endsWith("_khz") || key.endsWith("_freq") -> "kHz"
                    key.contains("load") || key.contains("pct") || key.contains("ratio") -> "%"
                    else -> ""
                }

                val isBool = (value == "0" || value == "1") &&
                    unit.isEmpty() &&
                    (key.contains("enable") || key.startsWith("is_") || key.endsWith("_mode")) &&
                    !key.contains("rate") && !key.contains("limit") && !key.contains("time") && !key.contains("delay") && !key.contains("freq")
                val floatVal = value.toFloatOrNull() ?: 0f
                val maxVal = when (unit) {
                    "%" -> 100f
                    "kHz" -> 3500000f
                    "µs" -> 2000000f
                    "ms" -> 2000f
                    else -> maxOf(100000f, floatVal * 2f)
                }

                list.add(
                    GovernorTunable(
                        key = key,
                        displayName = displayName,
                        currentValue = value,
                        minValue = 0f,
                        maxValue = maxVal,
                        isBoolean = isBool,
                        unit = unit,
                    )
                )
            }
            list.sortedBy { it.displayName }
        } catch (e: Exception) { Log.e("LynxRepository", "readGovernorTunables: ${e.message}"); emptyList() }
    }

    suspend fun setGovernorTunable(policyId: Int, governor: String, key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeGov = governor.replace(Regex("[^a-zA-Z0-9_]"), "")
            val safeKey = key.replace(Regex("[^a-zA-Z0-9_]"), "")
            val safeVal = value.replace(Regex("[^a-zA-Z0-9. _-]"), "")
            val script = """
                for p in "/sys/devices/system/cpu/cpufreq/policy$policyId/$safeGov/$safeKey" \
                         "/sys/devices/system/cpu/cpufreq/$safeGov/$safeKey" \
                         "/sys/devices/system/cpu/cpufreq/schedutil/$safeKey"; do
                    if [ -f "${'$'}p" ]; then
                        chmod 644 "${'$'}p" 2>/dev/null
                        echo '$safeVal' > "${'$'}p" 2>/dev/null
                    fi
                done
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun applyGovernorPreset(preset: String, policyId: Int? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = if (policyId != null) {
                val d = "/sys/devices/system/cpu/cpufreq/policy$policyId/schedutil"
                val isLittle = policyId == 0
                when (preset) {
                    "esports" -> """
                        [ -d "$d" ] && chmod 644 "$d"/* 2>/dev/null
                        echo 0 > "$d/up_rate_limit_us" 2>/dev/null
                        echo ${if (isLittle) 5000 else 2000} > "$d/down_rate_limit_us" 2>/dev/null
                        echo 0 > "$d/rate_limit_us" 2>/dev/null
                        echo ok
                    """.trimIndent()
                    "responsive" -> """
                        [ -d "$d" ] && chmod 644 "$d"/* 2>/dev/null
                        echo 0 > "$d/up_rate_limit_us" 2>/dev/null
                        echo ${if (isLittle) 10000 else 5000} > "$d/down_rate_limit_us" 2>/dev/null
                        echo 0 > "$d/rate_limit_us" 2>/dev/null
                        echo ok
                    """.trimIndent()
                    "powersave" -> """
                        [ -d "$d" ] && chmod 644 "$d"/* 2>/dev/null
                        echo ${if (isLittle) 10000 else 20000} > "$d/up_rate_limit_us" 2>/dev/null
                        echo ${if (isLittle) 1000 else 500} > "$d/down_rate_limit_us" 2>/dev/null
                        echo ok
                    """.trimIndent()
                    "oem" -> """
                        [ -d "$d" ] && chmod 644 "$d"/* 2>/dev/null
                        echo 0 > "$d/up_rate_limit_us" 2>/dev/null
                        echo 30000 > "$d/down_rate_limit_us" 2>/dev/null
                        echo ok
                    """.trimIndent()
                    else -> """
                        [ -d "$d" ] && chmod 644 "$d"/* 2>/dev/null
                        echo ${if (isLittle) 1000 else 0} > "$d/up_rate_limit_us" 2>/dev/null
                        echo ${if (isLittle) 20000 else 10000} > "$d/down_rate_limit_us" 2>/dev/null
                        echo ok
                    """.trimIndent()
                }
            } else {
                when (preset) {
                    "esports" -> """
                        for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do
                            [ -d "${'$'}d" ] || continue
                            chmod 644 "${'$'}d"/* 2>/dev/null
                            pol_id=${'$'}(echo "${'$'}d" | tr -dc '0-9')
                            if [ "${'$'}pol_id" = "0" ]; then
                                echo 0 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                                echo 5000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                            else
                                echo 0 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                                echo 2000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                            fi
                            echo 0 > "${'$'}d/rate_limit_us" 2>/dev/null
                        done
                        echo ok
                    """.trimIndent()
                    "responsive" -> """
                        for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do
                            [ -d "${'$'}d" ] || continue
                            chmod 644 "${'$'}d"/* 2>/dev/null
                            pol_id=${'$'}(echo "${'$'}d" | tr -dc '0-9')
                            if [ "${'$'}pol_id" = "0" ]; then
                                echo 0 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                                echo 10000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                            else
                                echo 0 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                                echo 5000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                            fi
                            echo 0 > "${'$'}d/rate_limit_us" 2>/dev/null
                        done
                        echo ok
                    """.trimIndent()
                    "powersave" -> """
                        for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do
                            [ -d "${'$'}d" ] || continue
                            chmod 644 "${'$'}d"/* 2>/dev/null
                            pol_id=${'$'}(echo "${'$'}d" | tr -dc '0-9')
                            if [ "${'$'}pol_id" = "0" ]; then
                                echo 10000 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                                echo 1000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                            else
                                echo 20000 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                                echo 500 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                            fi
                        done
                        echo ok
                    """.trimIndent()
                    "oem" -> """
                        for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do
                            [ -d "${'$'}d" ] || continue
                            chmod 644 "${'$'}d"/* 2>/dev/null
                            echo 0 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                            echo 30000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                        done
                        echo ok
                    """.trimIndent()
                    else -> """
                        for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do
                            [ -d "${'$'}d" ] || continue
                            chmod 644 "${'$'}d"/* 2>/dev/null
                            pol_id=${'$'}(echo "${'$'}d" | tr -dc '0-9')
                            if [ "${'$'}pol_id" = "0" ]; then
                                echo 1000 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                                echo 20000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                            else
                                echo 0 > "${'$'}d/up_rate_limit_us" 2>/dev/null
                                echo 10000 > "${'$'}d/down_rate_limit_us" 2>/dev/null
                            fi
                        done
                        echo ok
                    """.trimIndent()
                }
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setSchedutilRateLimits(policyId: Int?, upRateUs: Long, downRateUs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeUp = upRateUs.coerceIn(0L, 50000L)
            val safeDown = downRateUs.coerceIn(100L, 100000L)
            val script = if (policyId != null) {
                val d = "/sys/devices/system/cpu/cpufreq/policy$policyId/schedutil"
                """
                    [ -d "$d" ] && chmod 644 "$d"/* 2>/dev/null
                    echo $safeUp > "$d/up_rate_limit_us" 2>/dev/null
                    echo $safeDown > "$d/down_rate_limit_us" 2>/dev/null
                    echo 0 > "$d/rate_limit_us" 2>/dev/null
                    echo ok
                """.trimIndent()
            } else {
                """
                    for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil; do
                        [ -d "${'$'}d" ] || continue
                        chmod 644 "${'$'}d"/* 2>/dev/null
                        echo $safeUp > "${'$'}d/up_rate_limit_us" 2>/dev/null
                        echo $safeDown > "${'$'}d/down_rate_limit_us" 2>/dev/null
                        echo 0 > "${'$'}d/rate_limit_us" 2>/dev/null
                    done
                    echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: LMK Minfree Preset
    // ----------------------------------------------------------------


    suspend fun applyLmkPreset(preset: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val minfree = when (preset.lowercase()) {
                "conservative", "multitasking" -> "18432,23040,27648,32256,55296,80640"
                "balanced"     -> "18432,23040,27648,36864,65536,92160"
                "gaming"       -> "18432,23040,27648,43008,71680,100352"
                "aggressive"   -> "18432,23040,32256,49152,80640,116736"
                else -> return@withContext false
            }
            Shell.cmd(
                "[ -w /sys/module/lowmemorykiller/parameters/minfree ] && echo '$minfree' > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null && echo ok"
            ).exec().out.any { it.trim() == "ok" }
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  AnyKernel3 Flasher & Boot Partition Backup/Restore
    // ----------------------------------------------------------------



    suspend fun readSocOverride(): String = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("[ -f /data/adb/lynx/soc_override ] && cat /data/adb/lynx/soc_override 2>/dev/null").exec()
            r.out.firstOrNull()?.trim()?.ifBlank { "auto" } ?: "auto"
        } catch (e: Exception) { "auto" }
    }

    suspend fun setSocOverride(soc: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safe = soc.trim().lowercase()
            Shell.cmd("mkdir -p /data/adb/lynx 2>/dev/null; echo '$safe' > /data/adb/lynx/soc_override").exec()
            LynxRepository.writeStateKey("target_soc_override", safe, "val")
            true
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Dynamic ZRAM Compression Algorithm (Zero-Hardcoding)
    // ----------------------------------------------------------------

    suspend fun readZramCompAlgorithms(): Pair<String, List<String>> = withContext(Dispatchers.IO) {
        try {
            if (LynxRepository.isLynxdAvailable()) {
                val res = Shell.cmd("'$LYNXD_PATH' memory zram-info 2>/dev/null").exec()
                val rawJson = res.out.joinToString("").trim()
                if (rawJson.startsWith("{")) {
                    val obj = JSONObject(rawJson)
                    val available = obj.optBoolean("available", false)
                    val active = obj.optString("active_algo", "")
                    val arr = obj.optJSONArray("supported_algos")
                    val algos = mutableListOf<String>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val a = arr.optString(i).trim()
                            if (a.isNotBlank()) algos.add(a)
                        }
                    }
                    if (!available) return@withContext Pair("", emptyList())
                    if (algos.isNotEmpty()) return@withContext Pair(active.ifBlank { algos.first() }, algos)
                }
            }
            val r = Shell.cmd("[ -e /sys/block/zram0/comp_algorithm ] && cat /sys/block/zram0/comp_algorithm 2>/dev/null").exec()
            val raw = r.out.firstOrNull()?.trim().orEmpty()
            if (raw.isBlank()) return@withContext Pair("", emptyList())
            val active = Regex("\\[([^]]+)]").find(raw)?.groupValues?.getOrNull(1) ?: ""
            val avail = raw.replace("[", "").replace("]", "").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (avail.isEmpty()) Pair("", emptyList()) else Pair(active.ifBlank { avail.first() }, avail)
        } catch (e: Exception) {
            Pair("", emptyList())
        }
    }

    suspend fun setZramCompAlgorithm(algo: String, zramSizeMb: Int): Boolean =
        applyZramConfiguration(algo, zramSizeMb).success

    suspend fun applyZramConfiguration(algo: String, zramSizeMb: Int): ZramApplyResult = withContext(Dispatchers.IO) {
        try {
            val safeAlgo = algo.trim().replace(Regex("[^a-zA-Z0-9_-]"), "")
            if (zramSizeMb < 0) {
                return@withContext ZramApplyResult(false, "Ukuran ZRAM tidak valid.")
            }
            if (zramSizeMb in 1..127) {
                return@withContext ZramApplyResult(
                    false,
                    "Ukuran ZRAM minimal 128 MB (0.13 GB), atau pilih 0 GB untuk menonaktifkan."
                )
            }

            // 1. Inspect live RAM & swap state via kernel sysfs/procfs
            val probe = Shell.cmd("""
                [ -e /sys/block/zram0/disksize ] || { echo "PROBE_ERR:no_zram"; exit 2; }
                awk '/^MemTotal:/{t=${'$'}2} /^MemAvailable:/{a=${'$'}2} /^MemFree:/{f=${'$'}2} /^Cached:/{c=${'$'}2} END{if(a=="") a=f+c; printf "MEM|%d|%d\n", t+0, a+0}' /proc/meminfo 2>/dev/null
                awk '/zram0/{printf "SWAP|%d|%d\n", ${'$'}3+0, ${'$'}4+0; found=1} END{if(!found) print "SWAP|0|0"}' /proc/swaps 2>/dev/null
                DISK_BYTES=${'$'}(cat /sys/block/zram0/disksize 2>/dev/null || echo 0)
                echo "DISK|${'$'}DISK_BYTES"
            """.trimIndent()).exec()

            val probeLines = probe.out.map { it.trim() }
            if (probeLines.any { it.startsWith("PROBE_ERR:no_zram") } || !probe.isSuccess) {
                return@withContext ZramApplyResult(false, "Perangkat /sys/block/zram0 tidak tersedia pada kernel ini.")
            }

            var memTotKb = 0L
            var memAvailKb = 0L
            var swapUsedKb = 0L
            var curDiskBytes = 0L
            for (line in probeLines) {
                val p = line.split('|')
                when (p.firstOrNull()) {
                    "MEM" -> {
                        memTotKb = p.getOrNull(1)?.toLongOrNull() ?: 0L
                        memAvailKb = p.getOrNull(2)?.toLongOrNull() ?: 0L
                    }
                    "SWAP" -> {
                        swapUsedKb = p.getOrNull(2)?.toLongOrNull() ?: 0L
                    }
                    "DISK" -> {
                        curDiskBytes = p.getOrNull(1)?.toLongOrNull() ?: 0L
                    }
                }
            }

            val totalRamMb = (memTotKb / 1024L).toInt().coerceAtLeast(1024)
            val currentDiskMb = (curDiskBytes / 1048576L).toInt()
            val maxAllowedMb = maxOf((totalRamMb * 3) / 4, currentDiskMb, 128)
            if (zramSizeMb > maxAllowedMb) {
                val maxGb = com.noir.lynx.feature.memory.ui.formatZramGb(maxAllowedMb)
                return@withContext ZramApplyResult(
                    false,
                    "Ukuran melebihi batas aman perangkat (maks $maxGb)."
                )
            }

            // Guard against OOM crash before paging compressed ZRAM back into physical RAM
            val safetyHeadroomKb = 524_288L // 512 MB buffer
            if (swapUsedKb > 0L && memAvailKb > 0L && memAvailKb < swapUsedKb + safetyHeadroomKb) {
                val usedMb = (swapUsedKb / 1024L).toInt().coerceAtLeast(1)
                val availMb = (memAvailKb / 1024L).toInt()
                return@withContext ZramApplyResult(
                    false,
                    "RAM tersedia (${com.noir.lynx.feature.memory.ui.formatZramGb(availMb)}) belum cukup untuk membongkar ${com.noir.lynx.feature.memory.ui.formatZramGb(usedMb)} swap ZRAM (butuh cadangan ~0.5 GB). Bersihkan cache RAM dahulu."
                )
            }

            // 2. Try lynxd first when swap is empty (or updated lynxd binary handles MemAvailable guard)
            val lynxdCmd = if (zramSizeMb == 0) {
                "'$LYNXD_PATH' memory zram-disable"
            } else {
                val algoArg = if (safeAlgo.isNotEmpty()) "'algo=$safeAlgo'" else ""
                "'$LYNXD_PATH' memory zram-set 'size=${zramSizeMb}M' $algoArg"
            }
            val lynxdAttempt = Shell.cmd("[ -x '$LYNXD_PATH' ] && $lynxdCmd").exec()
            if (lynxdAttempt.isSuccess) {
                val live = verifyZramLiveState(zramSizeMb, safeAlgo)
                if (live != null) {
                    val (verifiedMb, verifiedAlgo, isSwapped) = live
                    if ((zramSizeMb == 0 && verifiedMb == 0 && !isSwapped) || (zramSizeMb > 0 && verifiedMb > 0 && isSwapped)) {
                        LynxRepository.writeStateKey("memory.zram_size_mb", verifiedMb.toString(), "val")
                        val resolvedAlgo = verifiedAlgo.ifBlank { safeAlgo }
                        if (resolvedAlgo.isNotEmpty()) {
                            LynxRepository.writeStateKey("memory.comp_algorithm", resolvedAlgo, "str")
                        }
                        val sizeLockedByKernel = zramSizeMb > 0 && verifiedMb != zramSizeMb
                        val msg = when {
                            verifiedMb == 0 -> "ZRAM dinonaktifkan (0 GB)"
                            sizeLockedByKernel ->
                                "Kernel mengunci ukuran ZRAM di ${com.noir.lynx.feature.memory.ui.formatZramGb(verifiedMb)}; algoritma aktif: ${resolvedAlgo.uppercase()}."
                            else ->
                                "ZRAM aktif: ${com.noir.lynx.feature.memory.ui.formatZramGb(verifiedMb)} (${resolvedAlgo.uppercase()})"
                        }
                        return@withContext ZramApplyResult(
                            success = !sizeLockedByKernel || (safeAlgo.isNotEmpty() && resolvedAlgo == safeAlgo),
                            message = msg,
                            appliedSizeMb = verifiedMb,
                            appliedAlgo = resolvedAlgo
                        )
                    }
                }
            }

            // 3. Direct verified root kernel transaction (always re-enables mkswap/swapon even if kernel clamps disksize)
            val targetBytes = zramSizeMb.toLong() * 1048576L
            val applyScript = if (zramSizeMb == 0) {
                """
                if grep -q 'zram0' /proc/swaps 2>/dev/null; then
                    swapoff /dev/block/zram0 2>/dev/null || { echo "ZRAM_ERR:swapoff"; exit 11; }
                fi
                reset_ok=0
                for i in 1 2 3 4 5 6; do
                    if echo 1 > /sys/block/zram0/reset 2>/dev/null; then reset_ok=1; break; fi
                    sleep 0.1 2>/dev/null || usleep 100000 2>/dev/null || sleep 1
                done
                [ "${'$'}reset_ok" = "1" ] || { echo "ZRAM_ERR:reset"; exit 12; }
                echo "ZRAM_OK|0|"
                """.trimIndent()
            } else {
                """
                if grep -q 'zram0' /proc/swaps 2>/dev/null; then
                    swapoff /dev/block/zram0 2>/dev/null || { echo "ZRAM_ERR:swapoff"; exit 11; }
                fi
                reset_ok=0
                for i in 1 2 3 4 5 6; do
                    if echo 1 > /sys/block/zram0/reset 2>/dev/null; then reset_ok=1; break; fi
                    sleep 0.1 2>/dev/null || usleep 100000 2>/dev/null || sleep 1
                done
                [ "${'$'}reset_ok" = "1" ] || { echo "ZRAM_ERR:reset"; exit 12; }
                if [ -n "$safeAlgo" ] && [ -e /sys/block/zram0/comp_algorithm ]; then
                    if grep -qw "$safeAlgo" /sys/block/zram0/comp_algorithm 2>/dev/null; then
                        echo "$safeAlgo" > /sys/block/zram0/comp_algorithm 2>/dev/null || { echo "ZRAM_ERR:algo"; exit 13; }
                    fi
                fi
                echo "$targetBytes" > /sys/block/zram0/disksize 2>/dev/null || true
                actual_bytes=${'$'}(cat /sys/block/zram0/disksize 2>/dev/null || echo 0)
                if [ "${'$'}actual_bytes" = "0" ] || [ -z "${'$'}actual_bytes" ]; then
                    echo "ZRAM_ERR:disksize"; exit 14
                fi
                mkswap /dev/block/zram0 >/dev/null 2>&1 || { echo "ZRAM_ERR:mkswap"; exit 16; }
                swapon /dev/block/zram0 >/dev/null 2>&1 || swapon -p 32758 /dev/block/zram0 >/dev/null 2>&1 || { echo "ZRAM_ERR:swapon"; exit 17; }
                active_a=${'$'}(sed -n 's/.*\[\([^]]*\)\].*/\1/p' /sys/block/zram0/comp_algorithm 2>/dev/null | head -n1)
                echo "ZRAM_OK|${'$'}actual_bytes|${'$'}active_a"
                """.trimIndent()
            }

            val directRes = Shell.cmd(applyScript).exec()
            val okLine = directRes.out.firstOrNull { it.trim().startsWith("ZRAM_OK|") }?.trim()
            if (directRes.isSuccess && okLine != null) {
                val parts = okLine.split('|')
                val verifiedBytes = parts.getOrNull(1)?.toLongOrNull() ?: targetBytes
                val verifiedMb = (verifiedBytes / 1048576L).toInt()
                val verifiedAlgo = parts.getOrNull(2).orEmpty().ifBlank { safeAlgo }
                LynxRepository.writeStateKey("memory.zram_size_mb", verifiedMb.toString(), "val")
                if (verifiedAlgo.isNotEmpty()) {
                    LynxRepository.writeStateKey("memory.comp_algorithm", verifiedAlgo, "str")
                }
                val sizeLockedByKernel = zramSizeMb > 0 && verifiedMb != zramSizeMb
                val msg = when {
                    verifiedMb == 0 -> "ZRAM dinonaktifkan (0 GB)"
                    sizeLockedByKernel ->
                        "Kernel mengunci ukuran ZRAM di ${com.noir.lynx.feature.memory.ui.formatZramGb(verifiedMb)}; algoritma aktif: ${verifiedAlgo.uppercase()}."
                    else ->
                        "ZRAM aktif: ${com.noir.lynx.feature.memory.ui.formatZramGb(verifiedMb)} (${verifiedAlgo.uppercase()})"
                }
                return@withContext ZramApplyResult(
                    success = !sizeLockedByKernel || (safeAlgo.isNotEmpty() && verifiedAlgo == safeAlgo),
                    message = msg,
                    appliedSizeMb = verifiedMb,
                    appliedAlgo = verifiedAlgo
                )
            }

            val errTag = (directRes.out + directRes.err)
                .firstOrNull { it.contains("ZRAM_ERR:") }
                ?.substringAfter("ZRAM_ERR:")
                ?.trim()
            val errReason = when {
                errTag == "swapoff" -> "swapoff gagal memindahkan halaman ZRAM kembali ke RAM (memori sistem sedang sibuk)."
                errTag == "reset" -> "Kernel masih mengunci perangkat ZRAM (/sys/block/zram0/reset sibuk)."
                errTag == "algo" -> "Kernel menolak algoritma kompresi ${safeAlgo.uppercase()}."
                errTag?.startsWith("disksize") == true || errTag?.startsWith("verify") == true ->
                    "Kernel gagal menetapkan ukuran disksize ${com.noir.lynx.feature.memory.ui.formatZramGb(zramSizeMb)}."
                errTag == "mkswap" -> "Format swap (mkswap /dev/block/zram0) gagal."
                errTag == "swapon" -> "Aktivasi swap (swapon /dev/block/zram0) ditolak kernel."
                else -> "Gagal mengubah konfigurasi ZRAM (${com.noir.lynx.feature.memory.ui.formatZramGb(zramSizeMb)})."
            }
            ZramApplyResult(false, errReason)
        } catch (e: Exception) {
            ZramApplyResult(false, e.message ?: "Gagal menerapkan konfigurasi ZRAM.")
        }
    }

    private fun verifyZramLiveState(expectedMb: Int, fallbackAlgo: String): Triple<Int, String, Boolean>? {
        val check = Shell.cmd("""
            disk=${'$'}(cat /sys/block/zram0/disksize 2>/dev/null || echo -1)
            active=${'$'}(sed -n 's/.*\[\([^]]*\)\].*/\1/p' /sys/block/zram0/comp_algorithm 2>/dev/null | head -n1)
            if grep -q 'zram0' /proc/swaps 2>/dev/null; then swapped=1; else swapped=0; fi
            echo "${'$'}disk|${'$'}active|${'$'}swapped"
        """.trimIndent()).exec()
        val line = check.out.firstOrNull()?.trim() ?: return null
        val parts = line.split('|')
        val diskBytes = parts.getOrNull(0)?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val actualAlgo = parts.getOrNull(1).orEmpty().ifBlank { fallbackAlgo }
        val isSwapped = parts.getOrNull(2) == "1"
        val actualMb = (diskBytes / 1048576L).toInt()
        return if ((expectedMb == 0 && !isSwapped) || (expectedMb > 0 && isSwapped && actualMb > 0)) {
            Triple(actualMb, actualAlgo, isSwapped)
        } else {
            null
        }
    }

    // ----------------------------------------------------------------
    //  Dynamic Display Refresh Rate Discovery (Zero-Hardcoding)
    // ----------------------------------------------------------------
}
