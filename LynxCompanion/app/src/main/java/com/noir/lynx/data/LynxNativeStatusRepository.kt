package com.noir.lynx.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.noir.lynx.LynxApp
import com.noir.lynx.hardware.NodeWriter
import com.noir.lynx.hardware.WriteResult
import com.noir.lynx.hardware.gpu.GpuBackendManager
import com.noir.lynx.service.LynxAppAutomationService
import com.topjohnwu.superuser.Shell
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private const val NATIVE_STATUS_TAG = "LynxRepository"

internal object LynxNativeStatusRepository {
    suspend fun readNativeStatus(): NativeEngineStatus? = withContext(Dispatchers.IO) {
        if (!LynxRepository.isModuleInstalled()) return@withContext null
        try {
            val lynxdPath = LynxRepository.getLynxdPath()
            val cmd = "[ -x '$lynxdPath' ] && '$lynxdPath' status --json 2>/dev/null"
            val res = Shell.cmd(cmd).exec()
            if (res.isSuccess && res.out.isNotEmpty()) {
                val raw = res.out.joinToString("\n").trim()
                if (raw.startsWith("{")) {
                    val root = JSONObject(raw)
                    val daemon = root.optJSONObject("daemon")
                    val profile = root.optJSONObject("profile")
                    val runtime = root.optJSONObject("runtime")
                    val health = root.optJSONObject("health")

                    return@withContext NativeEngineStatus(
                        isNativeActive = true,
                        daemonRunning = daemon?.optBoolean("running", false) ?: false,
                        daemonPid = daemon?.optInt("pid", 0) ?: 0,
                        activeProfile = profile?.optString("active", "dormant") ?: "dormant",
                        statusText = profile?.optString("status", "applied") ?: "applied",
                        isDirty = profile?.optBoolean("dirty", false) ?: false,
                        timestamp = profile?.optLong("timestamp", 0L) ?: 0L,
                        modifier = runtime?.optString("modifier", "None") ?: "None",
                        modifierDesc = runtime?.optString("modifier_desc", "Full Base Profile") ?: "Full Base Profile",
                        foregroundApp = runtime?.optString("foreground_app", "") ?: "",
                        tempC = runtime?.optDouble("temp_c", 0.0)?.toFloat() ?: 0f,
                        batteryPct = runtime?.optInt("battery_pct", 0) ?: 0,
                        cpuHealth = health?.optBoolean("cpu", true) ?: true,
                        gpuHealth = health?.optBoolean("gpu", true) ?: true,
                        batteryHealth = health?.optBoolean("battery", true) ?: true,
                        memoryHealth = health?.optBoolean("memory", true) ?: true,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(NATIVE_STATUS_TAG, "readNativeStatus error: ${e.message}")
        }
        null
    }

    // Authoritative registry of user-locked cluster policies: policyId -> Pair(minFreq, maxFreq)
}
